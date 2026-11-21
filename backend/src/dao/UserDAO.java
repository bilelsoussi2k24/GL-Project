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

    public void setActive(long userId, boolean active) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement("UPDATE users SET active = ? WHERE id = ?")) {
            st.setBoolean(1, active);
            st.setLong(2, userId);
            st.executeUpdate();
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

    public void updatePassword(long userId, String passwordHash) throws SQLException {
        String sql = "UPDATE users SET password_hash = ?, password_changed_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, passwordHash);
            st.setLong(2, userId);
            st.executeUpdate();
        }
    }

    public void updateProfile(long userId, String fullName, String phone) throws SQLException {
        String sql = "UPDATE users SET full_name = ?, phone = ? WHERE id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, fullName);
            st.setString(2, phone);
            st.setLong(3, userId);
            st.executeUpdate();
        }
    }

    public void updateEmail(long userId, String email) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement("UPDATE users SET email = ? WHERE id = ?")) {
            st.setString(1, email.toLowerCase(Locale.ROOT));
            st.setLong(2, userId);
            st.executeUpdate();
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

    /** Administrators whose password is a real hash, i.e. who can actually sign in. */
    public long countUsableAdmins() throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND password_hash LIKE 'pbkdf2_sha256$%'";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql);
             ResultSet rs = st.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    public long countActiveAdmins() throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND active = 1";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql);
             ResultSet rs = st.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
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
