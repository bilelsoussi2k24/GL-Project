package security;

import java.time.LocalDateTime;

/** Authenticated staff member attached to a request. */
public record Session(
    long sessionId,
    long userId,
    String email,
    String role,
    String fullName,
    Long doctorId,
    LocalDateTime expiresAt
) {
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isDoctor() {
        return "DOCTOR".equals(role);
    }
}
