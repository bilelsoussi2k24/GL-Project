package security;

import config.AppConfig;
import dao.SessionDAO;
import dao.UserDAO;
import model.User;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Authentication, session lifecycle and password rules. */
public final class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_FAILURES = 8;
    private static final long WINDOW_MILLIS = 10 * 60 * 1000L;

    private static final SessionDAO SESSIONS = new SessionDAO();
    private static final UserDAO USERS = new UserDAO();
    private static final Map<String, Deque<Long>> FAILURES = new ConcurrentHashMap<>();

    private AuthService() {
    }

    public record LoginResult(String token, LocalDateTime expiresAt, User user, Long doctorId) {
    }

    public static LoginResult login(String email, String password, String expectedRole, String clientKey)
            throws SQLException {
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        String throttleKey = normalizedEmail + "|" + clientKey;

        if (isThrottled(throttleKey)) {
            throw new AuthException(429, "Too many failed sign-in attempts. Try again in a few minutes.");
        }

        User user = USERS.findByEmail(normalizedEmail);
        boolean valid = user != null
            && !PasswordHasher.isLegacy(user.getPasswordHash())
            && PasswordHasher.verify(password, user.getPasswordHash());

        if (!valid) {
            recordFailure(throttleKey);
            throw new AuthException(401, "Incorrect email or password.");
        }
        if (!user.isActive()) {
            throw new AuthException(403, "This account has been deactivated. Contact the administrator.");
        }
        if (expectedRole != null && !expectedRole.isBlank()
            && !expectedRole.equalsIgnoreCase(user.getRole())) {
            throw new AuthException(403, "This account is not a " + expectedRole.toLowerCase(Locale.ROOT) + " account.");
        }

        FAILURES.remove(throttleKey);
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
        USERS.updatePassword(user.getId(), PasswordHasher.hash(newPassword));
        SESSIONS.deleteForUser(user.getId(), session.sessionId());
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

    private static boolean isThrottled(String key) {
        Deque<Long> attempts = FAILURES.get(key);
        if (attempts == null) return false;
        long cutoff = System.currentTimeMillis() - WINDOW_MILLIS;
        synchronized (attempts) {
            while (!attempts.isEmpty() && attempts.peekFirst() < cutoff) attempts.pollFirst();
            return attempts.size() >= MAX_FAILURES;
        }
    }

    private static void recordFailure(String key) {
        Deque<Long> attempts = FAILURES.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (attempts) {
            attempts.addLast(System.currentTimeMillis());
        }
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
