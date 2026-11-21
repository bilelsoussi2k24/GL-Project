package dao;

import config.DatabaseConnection;
import model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UserDAO {

    private static final String COLUMNS =
        "id, email, password_hash, role, active, full_name, phone, password_changed_at";

    public User findByEmail(String email) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM users WHERE email = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, email.toLowerCase(Locale.ROOT));
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public User findById(long id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM users WHERE id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setLong(1, id);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public long create(String email, String passwordHash, String role, String fullName, String phone)
            throws SQLException {
        String sql = "INSERT INTO users (email, password_hash, role, active, full_name, phone) VALUES (?, ?, ?, 1, ?, ?)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, email.toLowerCase(Locale.ROOT));
            st.setString(2, passwordHash);
            st.setString(3, role);
            st.setString(4, fullName == null ? "" : fullName);
            st.setString(5, phone);
            st.executeUpdate();
            try (ResultSet keys = st.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        }
        throw new SQLException("Could not read the new account id");
    }

    public boolean emailExists(String email, Long exceptUserId) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE email = ?" + (exceptUserId == null ? "" : " AND id <> ?");
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, email.toLowerCase(Locale.ROOT));
            if (exceptUserId != null) st.setLong(2, exceptUserId);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Renames the default seeded title so greetings read "Administrator", not "Hospital". */
    public int renameDefaultAdminTitle() throws SQLException {
        String sql = "UPDATE users SET full_name = 'Administrator' "
            + "WHERE role = 'ADMIN' AND full_name = 'Hospital Administrator'";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            return st.executeUpdate();
        }
    }

    /**
     * Changes the password and revokes the account's sessions in one transaction.
     * {@code keepSessionId} is the caller's own session, which stays signed in.
     */
    public void changePasswordAndRevokeSessions(long userId, String passwordHash, Long keepSessionId)
            throws SQLException {
        String deleteSql = "DELETE FROM auth_sessions WHERE user_id = ?"
            + (keepSessionId == null ? "" : " AND id <> ?");
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement st = con.prepareStatement(
                        "UPDATE users SET password_hash = ?, password_changed_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                    st.setString(1, passwordHash);
                    st.setLong(2, userId);
                    st.executeUpdate();
                }
                try (PreparedStatement st = con.prepareStatement(deleteSql)) {
                    st.setLong(1, userId);
                    if (keepSessionId != null) st.setLong(2, keepSessionId);
                    st.executeUpdate();
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    /** Administrator password reset: the account is signed out everywhere. */
    public void resetPasswordAndRevokeSessions(long userId, String passwordHash) throws SQLException {
        changePasswordAndRevokeSessions(userId, passwordHash, null);
    }

    /** Profile and login email change together, so one cannot be stored without the other. */
    public void updateProfileAndEmail(long userId, String fullName, String phone, String email)
            throws SQLException {
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement st = con.prepareStatement(
                        "UPDATE users SET full_name = ?, phone = ?, email = ? WHERE id = ?")) {
                    st.setString(1, fullName);
                    st.setString(2, phone);
                    st.setString(3, email.toLowerCase(Locale.ROOT));
                    st.setLong(4, userId);
                    st.executeUpdate();
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    /**
     * Activates or deactivates an account, keeps the doctor profile consistent and
     * revokes every session on deactivation, all in one transaction.
     *
     * @return false when deactivating would leave the hospital without an active
     *         administrator. Nothing is changed in that case.
     */
    public boolean setActiveAndSync(long userId, boolean active) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                String role;
                try (PreparedStatement st = con.prepareStatement("SELECT role FROM users WHERE id = ? FOR UPDATE")) {
                    st.setLong(1, userId);
                    try (ResultSet rs = st.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();
                            return false;
                        }
                        role = rs.getString("role");
                    }
                }
                if (!active && "ADMIN".equals(role)) {
                    int activeAdmins = 0;
                    try (PreparedStatement st = con.prepareStatement(
                            "SELECT id FROM users WHERE role = 'ADMIN' AND active = 1 FOR UPDATE");
                         ResultSet rs = st.executeQuery()) {
                        while (rs.next()) activeAdmins++;
                    }
                    if (activeAdmins <= 1) {
                        con.rollback();
                        return false;
                    }
                }

                try (PreparedStatement st = con.prepareStatement("UPDATE users SET active = ? WHERE id = ?")) {
                    st.setBoolean(1, active);
                    st.setLong(2, userId);
                    st.executeUpdate();
                }
                if ("DOCTOR".equals(role)) {
                    try (PreparedStatement st = con.prepareStatement(
                            "UPDATE doctors SET status = ? WHERE user_id = ?")) {
                        st.setString(1, active ? "active" : "inactive");
                        st.setLong(2, userId);
                        st.executeUpdate();
                    }
                }
                if (!active) {
                    try (PreparedStatement st = con.prepareStatement(
                            "DELETE FROM auth_sessions WHERE user_id = ?")) {
                        st.setLong(1, userId);
                        st.executeUpdate();
                    }
                }
                con.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public List<Map<String, Object>> list(String role, String query) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT u.id, u.email, u.role, u.active, u.full_name, u.phone, u.created_at,
                   d.id AS doctor_id, d.staff_id, d.specialty, d.status AS doctor_status
            FROM users u
            LEFT JOIN doctors d ON d.user_id = u.id
            WHERE 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        if (role != null && !role.isBlank()) {
            sql.append(" AND u.role = ?");
            args.add(role);
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (u.email LIKE ? OR u.full_name LIKE ? OR d.staff_id LIKE ?)");
            String like = "%" + query.strip() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY u.role, u.full_name, u.email");

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) st.setObject(i + 1, args.get(i));
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("email", rs.getString("email"));
                    m.put("role", rs.getString("role"));
                    m.put("active", rs.getBoolean("active"));
                    m.put("fullName", rs.getString("full_name"));
                    m.put("phone", rs.getString("phone"));
                    Timestamp created = rs.getTimestamp("created_at");
                    m.put("createdAt", created == null ? null : created.toLocalDateTime().toString());
                    long doctorId = rs.getLong("doctor_id");
                    m.put("doctorId", rs.wasNull() ? null : doctorId);
                    m.put("staffId", rs.getString("staff_id"));
                    m.put("specialty", rs.getString("specialty"));
                    m.put("doctorStatus", rs.getString("doctor_status"));
                    rows.add(m);
                }
            }
        }
        return rows;
    }

    /** Most recently created accounts, for the administrator activity feed. */
    public List<Map<String, Object>> listRecent(int limit) throws SQLException {
        String sql = """
            SELECT email, role, full_name, created_at FROM users ORDER BY created_at DESC, id DESC LIMIT ?
            """;
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, limit);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("email", rs.getString("email"));
                    m.put("role", rs.getString("role"));
                    m.put("fullName", rs.getString("full_name"));
                    Timestamp created = rs.getTimestamp("created_at");
                    m.put("createdAt", created == null ? null : created.toLocalDateTime().toString());
                    rows.add(m);
                }
            }
        }
        return rows;
    }

    /**
     * Administrators who can actually sign in: an active account with a real hash.
     * An inactive administrator does not satisfy the bootstrap check.
     */
    public long countActiveUsableAdmins(Connection con) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users "
            + "WHERE role = 'ADMIN' AND active = 1 AND password_hash LIKE 'pbkdf2_sha256$%'";
        try (PreparedStatement st = con.prepareStatement(sql);
             ResultSet rs = st.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    /* --------------------------------------------------- bootstrap helpers */

    public User findByEmail(Connection con, String email) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM users WHERE email = ?";
        try (PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, email.toLowerCase(Locale.ROOT));
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public long create(Connection con, String email, String passwordHash, String role, String fullName)
            throws SQLException {
        String sql = "INSERT INTO users (email, password_hash, role, active, full_name) VALUES (?, ?, ?, 1, ?)";
        try (PreparedStatement st = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, email.toLowerCase(Locale.ROOT));
            st.setString(2, passwordHash);
            st.setString(3, role);
            st.setString(4, fullName == null ? "" : fullName);
            st.executeUpdate();
            try (ResultSet keys = st.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        }
        throw new SQLException("Could not read the new account id");
    }

    public void updatePassword(Connection con, long userId, String passwordHash) throws SQLException {
        try (PreparedStatement st = con.prepareStatement(
                "UPDATE users SET password_hash = ?, password_changed_at = CURRENT_TIMESTAMP WHERE id = ?")) {
            st.setString(1, passwordHash);
            st.setLong(2, userId);
            st.executeUpdate();
        }
    }

    public void setActive(Connection con, long userId, boolean active) throws SQLException {
        try (PreparedStatement st = con.prepareStatement("UPDATE users SET active = ? WHERE id = ?")) {
            st.setBoolean(1, active);
            st.setLong(2, userId);
            st.executeUpdate();
        }
    }

    public long countByRole(String role) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement("SELECT COUNT(*) FROM users WHERE role = ?")) {
            st.setString(1, role);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private User map(ResultSet rs) throws SQLException {
        Timestamp changed = rs.getTimestamp("password_changed_at");
        return new User(
            rs.getInt("id"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("role"),
            rs.getBoolean("active"),
            rs.getString("full_name"),
            rs.getString("phone"),
            changed == null ? null : changed.toLocalDateTime()
        );
    }
}
