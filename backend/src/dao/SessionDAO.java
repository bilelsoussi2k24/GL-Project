package dao;

import config.DatabaseConnection;
import security.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/** Server-side staff sessions. Only a hash of each token is stored. */
public class SessionDAO {

    public long create(long userId, String tokenHash, LocalDateTime expiresAt) throws SQLException {
        String sql = "INSERT INTO auth_sessions (token_hash, user_id, expires_at) VALUES (?, ?, ?)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, tokenHash);
            st.setLong(2, userId);
            st.setTimestamp(3, Timestamp.valueOf(expiresAt));
            st.executeUpdate();
            try (ResultSet keys = st.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        }
        throw new SQLException("Could not create the session");
    }

    public Session findByTokenHash(String tokenHash) throws SQLException {
        String sql = """
            SELECT s.id, s.expires_at, u.id AS user_id, u.email, u.role, u.full_name, u.active, d.id AS doctor_id
            FROM auth_sessions s
            JOIN users u ON u.id = s.user_id
            LEFT JOIN doctors d ON d.user_id = u.id
            WHERE s.token_hash = ?
            """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, tokenHash);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) return null;
                if (!rs.getBoolean("active")) return null;
                long doctorId = rs.getLong("doctor_id");
                boolean hasDoctor = !rs.wasNull();
                Timestamp expires = rs.getTimestamp("expires_at");
                return new Session(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getString("email"),
                    rs.getString("role"),
                    rs.getString("full_name"),
                    hasDoctor ? doctorId : null,
                    expires == null ? null : expires.toLocalDateTime()
                );
            }
        }
    }

    public void delete(long sessionId) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement("DELETE FROM auth_sessions WHERE id = ?")) {
            st.setLong(1, sessionId);
            st.executeUpdate();
        }
    }

    public void deleteForUser(long userId, Long exceptSessionId) throws SQLException {
        String sql = "DELETE FROM auth_sessions WHERE user_id = ?"
            + (exceptSessionId == null ? "" : " AND id <> ?");
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setLong(1, userId);
            if (exceptSessionId != null) st.setLong(2, exceptSessionId);
            st.executeUpdate();
        }
    }

    public int deleteExpired() throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement("DELETE FROM auth_sessions WHERE expires_at < NOW()")) {
            return st.executeUpdate();
        }
    }
}
