package model;

import java.time.LocalDateTime;

/** A staff login account. */
public class User {

    private final int id;
    private final String email;
    private final String passwordHash;
    private final String role;
    private final boolean active;
    private final String fullName;
    private final String phone;
    private final LocalDateTime passwordChangedAt;

    public User(int id, String email, String passwordHash, String role, boolean active,
                String fullName, String phone, LocalDateTime passwordChangedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
        this.fullName = fullName;
        this.phone = phone;
        this.passwordChangedAt = passwordChangedAt;
    }

    public int getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public String getFullName() {
        return fullName == null ? "" : fullName;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }
}
