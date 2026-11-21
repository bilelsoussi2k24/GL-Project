package dao;

import config.DatabaseConnection;
import model.Doctor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DoctorDAO {

    private static final String SELECT = """
        SELECT d.id, d.user_id, d.staff_id, d.first_name, d.last_name, d.specialty, d.phone, d.office,
               d.status, d.work_days, d.first_appointment, d.last_appointment, d.visit_length,
               u.email, u.active
        FROM doctors d JOIN users u ON u.id = d.user_id
        """;

    public List<Doctor> findAll(String query, String status) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            sql.append(" AND (d.first_name LIKE ? OR d.last_name LIKE ? OR d.specialty LIKE ?")
               .append(" OR u.email LIKE ? OR d.staff_id LIKE ?)");
            String like = "%" + query.strip() + "%";
            for (int i = 0; i < 5; i++) args.add(like);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND d.status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY d.first_name, d.last_name");

        List<Doctor> doctors = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) st.setObject(i + 1, args.get(i));
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) doctors.add(map(rs));
            }
        }
        return doctors;
    }

    public Doctor findById(long id) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(SELECT + " WHERE d.id = ?")) {
            st.setLong(1, id);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public Doctor findByUserId(long userId) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(SELECT + " WHERE d.user_id = ?")) {
            st.setLong(1, userId);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Creates the profile row and returns its id. The staff id is derived from the id. */
    public long create(long userId, String firstName, String lastName, String specialty, String phone,
                       String office, String status) throws SQLException {
        String sql = """
            INSERT INTO doctors (user_id, staff_id, first_name, last_name, specialty, phone, office, status)
            VALUES (?, '', ?, ?, ?, ?, ?, ?)
            """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setLong(1, userId);
            st.setString(2, firstName);
            st.setString(3, lastName);
            st.setString(4, specialty);
            st.setString(5, phone);
            st.setString(6, office);
            st.setString(7, status);
            st.executeUpdate();
            long id;
            try (ResultSet keys = st.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Could not read the new doctor id");
                id = keys.getLong(1);
            }
            String staffId = String.format("DR-%04d", id);
            try (PreparedStatement update = con.prepareStatement("UPDATE doctors SET staff_id = ? WHERE id = ?")) {
                update.setString(1, staffId);
                update.setLong(2, id);
                update.executeUpdate();
            }
            return id;
        }
    }

    /**
     * Creates the login account and the doctor profile in one transaction, so a
     * failure never leaves a half-created doctor behind. A doctor created as
     * inactive gets an inactive login account from the start.
     */
    public long createWithAccount(String email, String passwordHash, String fullName, String phone,
                                  String firstName, String lastName, String specialty, String office,
                                  String status) throws SQLException {
        boolean active = !"inactive".equals(status);
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                long userId;
                String userSql = """
                    INSERT INTO users (email, password_hash, role, active, full_name, phone)
                    VALUES (?, ?, 'DOCTOR', ?, ?, ?)
                    """;
                try (PreparedStatement st = con.prepareStatement(userSql, Statement.RETURN_GENERATED_KEYS)) {
                    st.setString(1, email);
                    st.setString(2, passwordHash);
                    st.setBoolean(3, active);
                    st.setString(4, fullName);
                    st.setString(5, phone);
                    st.executeUpdate();
                    try (ResultSet keys = st.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Could not read the new account id");
                        userId = keys.getLong(1);
                    }
                }

                long doctorId;
                String doctorSql = """
                    INSERT INTO doctors (user_id, staff_id, first_name, last_name, specialty, phone, office, status)
                    VALUES (?, '', ?, ?, ?, ?, ?, ?)
                    """;
                try (PreparedStatement st = con.prepareStatement(doctorSql, Statement.RETURN_GENERATED_KEYS)) {
                    st.setLong(1, userId);
                    st.setString(2, firstName);
                    st.setString(3, lastName);
                    st.setString(4, specialty);
                    st.setString(5, phone);
                    st.setString(6, office);
                    st.setString(7, status);
                    st.executeUpdate();
                    try (ResultSet keys = st.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Could not read the new doctor id");
                        doctorId = keys.getLong(1);
                    }
                }
                try (PreparedStatement st = con.prepareStatement("UPDATE doctors SET staff_id = ? WHERE id = ?")) {
                    st.setString(1, String.format("DR-%04d", doctorId));
                    st.setLong(2, doctorId);
                    st.executeUpdate();
                }
                con.commit();
                return doctorId;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    /**
     * Administrator edit of a doctor: profile, login account and (when the doctor is
     * deactivated) session revocation in one transaction. A deactivated doctor is
     * signed out immediately and cannot reuse an old token if reactivated later.
     */
    public void updateAccount(long doctorId, long userId, String firstName, String lastName,
                              String specialty, String phone, String office, String status, String email)
            throws SQLException {
        boolean active = !"inactive".equals(status);
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement st = con.prepareStatement("""
                        UPDATE doctors SET first_name = ?, last_name = ?, specialty = ?, phone = ?,
                               office = ?, status = ? WHERE id = ?
                        """)) {
                    st.setString(1, firstName);
                    st.setString(2, lastName);
                    st.setString(3, specialty);
                    st.setString(4, phone);
                    st.setString(5, office);
                    st.setString(6, status);
                    st.setLong(7, doctorId);
                    st.executeUpdate();
                }
                try (PreparedStatement st = con.prepareStatement("""
                        UPDATE users SET full_name = ?, phone = ?, email = ?, active = ? WHERE id = ?
                        """)) {
                    st.setString(1, firstName + " " + lastName);
                    st.setString(2, phone);
                    st.setString(3, email);
                    st.setBoolean(4, active);
                    st.setLong(5, userId);
                    st.executeUpdate();
                }
                if (!active) {
                    try (PreparedStatement st = con.prepareStatement(
                            "DELETE FROM auth_sessions WHERE user_id = ?")) {
                        st.setLong(1, userId);
                        st.executeUpdate();
                    }
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

    /** Status change for a doctor: profile status, login state and sessions together. */
    public void updateStatusAccount(long doctorId, long userId, String status) throws SQLException {
        boolean active = !"inactive".equals(status);
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement st = con.prepareStatement(
                        "UPDATE doctors SET status = ? WHERE id = ?")) {
                    st.setString(1, status);
                    st.setLong(2, doctorId);
                    st.executeUpdate();
                }
                try (PreparedStatement st = con.prepareStatement(
                        "UPDATE users SET active = ? WHERE id = ?")) {
                    st.setBoolean(1, active);
                    st.setLong(2, userId);
                    st.executeUpdate();
                }
                if (!active) {
                    try (PreparedStatement st = con.prepareStatement(
                            "DELETE FROM auth_sessions WHERE user_id = ?")) {
                        st.setLong(1, userId);
                        st.executeUpdate();
                    }
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
     * Doctor-editable fields only (administrator-controlled fields stay untouched),
     * saved together with the account's display name, phone and email.
     */
    public void updateOwnProfileWithAccount(long userId, String firstName, String lastName, String phone,
                                            String office, String workDays, String firstAppointment,
                                            String lastAppointment, int visitLength, String fullName,
                                            String email) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement st = con.prepareStatement("""
                        UPDATE doctors SET first_name = ?, last_name = ?, phone = ?, office = ?, work_days = ?,
                               first_appointment = ?, last_appointment = ?, visit_length = ?
                        WHERE user_id = ?
                        """)) {
                    st.setString(1, firstName);
                    st.setString(2, lastName);
                    st.setString(3, phone);
                    st.setString(4, office);
                    st.setString(5, workDays);
                    st.setString(6, firstAppointment);
                    st.setString(7, lastAppointment);
                    st.setInt(8, visitLength);
                    st.setLong(9, userId);
                    st.executeUpdate();
                }
                try (PreparedStatement st = con.prepareStatement(
                        "UPDATE users SET full_name = ?, phone = ?, email = ? WHERE id = ?")) {
                    st.setString(1, fullName);
                    st.setString(2, phone);
                    st.setString(3, email);
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

    public long count() throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement("SELECT COUNT(*) FROM doctors");
             ResultSet rs = st.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    public long countByStatus(String status) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement("SELECT COUNT(*) FROM doctors WHERE status = ?")) {
            st.setString(1, status);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    public long countPatients(long doctorId) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement("SELECT COUNT(*) FROM doctor_patients WHERE doctor_id = ?")) {
            st.setLong(1, doctorId);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private Doctor map(ResultSet rs) throws SQLException {
        java.sql.Time first = rs.getTime("first_appointment");
        java.sql.Time last = rs.getTime("last_appointment");
        return new Doctor(
            rs.getInt("id"),
            rs.getInt("user_id"),
            rs.getString("staff_id"),
            rs.getString("first_name"),
            rs.getString("last_name"),
            rs.getString("specialty"),
            rs.getString("phone"),
            rs.getString("office"),
            rs.getString("status"),
            rs.getString("email"),
            rs.getBoolean("active"),
            rs.getString("work_days"),
            first == null ? "08:00" : first.toLocalTime().toString().substring(0, 5),
            last == null ? "17:00" : last.toLocalTime().toString().substring(0, 5),
            rs.getInt("visit_length")
        );
    }
}
