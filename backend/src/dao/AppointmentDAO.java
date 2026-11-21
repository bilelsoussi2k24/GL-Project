package dao;

import config.DatabaseConnection;
import model.Appointment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AppointmentDAO {

    private static final String SELECT = """
        SELECT a.id, a.doctor_id, a.patient_id, a.appointment_date, a.status, a.reason,
               p.first_name, p.last_name, p.phone
        FROM appointments a JOIN patients p ON p.id = a.patient_id
        """;

    public List<Appointment> getAppointmentsByDoctor(long doctorId, LocalDate from, LocalDate to, String status)
            throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE a.doctor_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(doctorId);
        if (from != null) {
            sql.append(" AND a.appointment_date >= ?");
            args.add(Timestamp.valueOf(from.atStartOfDay()));
        }
        if (to != null) {
            sql.append(" AND a.appointment_date < ?");
            args.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND a.status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY a.appointment_date ASC");

        List<Appointment> appointments = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) st.setObject(i + 1, args.get(i));
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) appointments.add(map(rs));
            }
        }
        return appointments;
    }

    public Appointment find(long doctorId, long appointmentId) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(SELECT + " WHERE a.doctor_id = ? AND a.id = ?")) {
            st.setLong(1, doctorId);
            st.setLong(2, appointmentId);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /**
     * Books an appointment only when the patient is assigned to this doctor.
     * The INSERT ... SELECT guarantees that rule inside the database.
     */
    public long create(long doctorId, long patientId, LocalDateTime date, String reason) throws SQLException {
        String sql = """
            INSERT INTO appointments (doctor_id, patient_id, appointment_date, status, reason)
            SELECT ?, dp.patient_id, ?, 'scheduled', ?
            FROM doctor_patients dp WHERE dp.doctor_id = ? AND dp.patient_id = ?
            """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setLong(1, doctorId);
            st.setTimestamp(2, Timestamp.valueOf(date));
            st.setString(3, reason);
            st.setLong(4, doctorId);
            st.setLong(5, patientId);
            if (st.executeUpdate() == 0) return -1;
            try (ResultSet keys = st.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        }
    }

    /** Reschedules an existing appointment the doctor owns. */
    public boolean update(long doctorId, long appointmentId, long patientId, LocalDateTime date, String reason)
            throws SQLException {
        String sql = """
            UPDATE appointments a
            JOIN doctor_patients dp ON dp.doctor_id = a.doctor_id AND dp.patient_id = ?
            SET a.patient_id = ?, a.appointment_date = ?, a.reason = ?
            WHERE a.id = ? AND a.doctor_id = ?
            """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setLong(1, patientId);
            st.setLong(2, patientId);
            st.setTimestamp(3, Timestamp.valueOf(date));
            st.setString(4, reason);
            st.setLong(5, appointmentId);
            st.setLong(6, doctorId);
            return st.executeUpdate() > 0;
        }
    }

    public boolean updateStatus(long doctorId, long appointmentId, String newStatus) throws SQLException {
        String sql = "UPDATE appointments SET status = ? WHERE id = ? AND doctor_id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, newStatus);
            st.setLong(2, appointmentId);
            st.setLong(3, doctorId);
            return st.executeUpdate() > 0;
        }
    }

    /** Administrator view: latest appointments across the hospital. */
    public List<Map<String, Object>> listRecent(int limit) throws SQLException {
        String sql = """
            SELECT a.id, a.appointment_date, a.status, a.reason,
                   CONCAT(p.first_name, ' ', p.last_name) AS patient_name,
                   CONCAT('Dr. ', d.first_name, ' ', d.last_name) AS doctor_name
            FROM appointments a
            JOIN patients p ON p.id = a.patient_id
            JOIN doctors d ON d.id = a.doctor_id
            ORDER BY a.appointment_date DESC
            LIMIT ?
            """;
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, limit);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("patientName", rs.getString("patient_name"));
                    m.put("doctorName", rs.getString("doctor_name"));
                    m.put("appointmentDate", rs.getTimestamp("appointment_date").toLocalDateTime().toString());
                    m.put("status", rs.getString("status"));
                    m.put("reason", rs.getString("reason"));
                    rows.add(m);
                }
            }
        }
        return rows;
    }

    public long countAll() throws SQLException {
        return scalar("SELECT COUNT(*) FROM appointments");
    }

    public long countToday() throws SQLException {
        return scalar("SELECT COUNT(*) FROM appointments WHERE DATE(appointment_date) = CURDATE()");
    }

    public long countByStatus(String status) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement("SELECT COUNT(*) FROM appointments WHERE status = ?")) {
            st.setString(1, status);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    public long countUpcoming() throws SQLException {
        return scalar("""
            SELECT COUNT(*) FROM appointments
            WHERE status = 'scheduled' AND appointment_date >= NOW()
            """);
    }

    private long scalar(String sql) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql);
             ResultSet rs = st.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private Appointment map(ResultSet rs) throws SQLException {
        return new Appointment(
            rs.getInt("id"),
            rs.getInt("doctor_id"),
            rs.getInt("patient_id"),
            rs.getTimestamp("appointment_date").toLocalDateTime(),
            rs.getString("status"),
            rs.getString("reason"),
            rs.getString("first_name"),
            rs.getString("last_name"),
            rs.getString("phone")
        );
    }
}
