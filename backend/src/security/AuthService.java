package security;

import config.AppConfig;
import dao.SessionDAO;
import dao.UserDAO;
import model.User;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;

/** Authentication, session lifecycle and password rules. */
public final class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long WINDOW_MILLIS = 10 * 60 * 1000L;

    private static final SessionDAO SESSIONS = new SessionDAO();
    private static final UserDAO USERS = new UserDAO();

    /**
     * Failed sign-ins for one email from one address. This slows password guessing
     * without letting an attacker lock a colleague out of their own account: other
     * addresses are unaffected, and the limit is reset by a successful sign-in.
     */
    private static final RateLimiter ACCOUNT_FAILURES = new RateLimiter(8, WINDOW_MILLIS, 20_000);
    /** Broad per-address cap, so one host cannot spray failures across many accounts. */
    private static final RateLimiter ADDRESS_FAILURES = new RateLimiter(50, WINDOW_MILLIS, 10_000);

    private AuthService() {
    }

    public record LoginResult(String token, LocalDateTime expiresAt, User user, Long doctorId) {
    }

    public static LoginResult login(String email, String password, String expectedRole, String clientKey)
            throws SQLException {
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        String accountKey = normalizedEmail + "|" + clientKey;

        if (ACCOUNT_FAILURES.isLimited(accountKey) || ADDRESS_FAILURES.isLimited(clientKey)) {
            throw new AuthException(429, "Too many failed sign-in attempts. Try again in a few minutes.");
        }

        User user = USERS.findByEmail(normalizedEmail);
        boolean valid = user != null
            && !PasswordHasher.isLegacy(user.getPasswordHash())
            && PasswordHasher.verify(password, user.getPasswordHash());

        if (!valid) {
            ACCOUNT_FAILURES.record(accountKey);
            ADDRESS_FAILURES.record(clientKey);
            throw new AuthException(401, "Incorrect email or password.");
        }
        if (!user.isActive()) {
            throw new AuthException(403, "This account has been deactivated. Contact the administrator.");
        }
        if (expectedRole != null && !expectedRole.isBlank()
            && !expectedRole.equalsIgnoreCase(user.getRole())) {
            String expectedLabel = "ADMIN".equalsIgnoreCase(expectedRole) ? "an administrator" : "a doctor";
            throw new AuthException(403, "This account is not " + expectedLabel + " account.");
        }

        ACCOUNT_FAILURES.reset(accountKey);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(AppConfig.sessionHours());
        String token = newToken();
        SESSIONS.create(user.getId(), PasswordHasher.sha256Hex(token), expiresAt);

        model.Doctor doctor = new dao.DoctorDAO().findByUserId(user.getId());
        return new LoginResult(token, expiresAt, user, doctor == null ? null : Long.valueOf(doctor.getId()));
    }

    /** Returns the session for a bearer token, or null when it is unknown, expired or revoked. */
    public static Session authenticate(String token) throws SQLException {
        if (token == null || token.isBlank()) return null;
        Session session = SESSIONS.findByTokenHash(PasswordHasher.sha256Hex(token));
        if (session == null) return null;
        if (session.expiresAt() == null || session.expiresAt().isBefore(LocalDateTime.now())) {
            SESSIONS.delete(session.sessionId());
            return null;
        }
        return session;
    }

    public static void logout(String token) throws SQLException {
        if (token == null || token.isBlank()) return;
        Session session = SESSIONS.findByTokenHash(PasswordHasher.sha256Hex(token));
        if (session != null) SESSIONS.delete(session.sessionId());
    }

    public static LocalDateTime changePassword(Session session, String currentPassword, String newPassword)
            throws SQLException {
        User user = USERS.findById(session.userId());
        if (user == null) throw new AuthException(401, "Your session is no longer valid.");
        if (!PasswordHasher.verify(currentPassword, user.getPasswordHash())) {
            throw new AuthException(400, "Your current password is incorrect.");
        }
        if (!PasswordHasher.isStrongEnough(newPassword)) {
            throw new AuthException(400, "Use at least 8 characters, including a letter and a number.");
        }
        if (currentPassword.equals(newPassword)) {
            throw new AuthException(400, "Choose a password different from your current one.");
        }
        USERS.changePasswordAndRevokeSessions(user.getId(), PasswordHasher.hash(newPassword), session.sessionId());
        return LocalDateTime.now();
    }

    public static int purgeExpiredSessions() throws SQLException {
        return SESSIONS.deleteExpired();
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Authentication or authorization failure carrying the HTTP status to return. */
    public static final class AuthException extends RuntimeException {
        private final int status;

        public AuthException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }
}
