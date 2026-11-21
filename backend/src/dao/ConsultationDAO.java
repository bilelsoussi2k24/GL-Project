package dao;

import config.DatabaseConnection;
import model.Consultation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class ConsultationDAO {

    private static final String SELECT = """
        SELECT c.id, c.appointment_id, c.diagnosis, c.treatment, c.notes, c.consultation_date,
               a.patient_id, a.doctor_id, CONCAT(p.first_name, ' ', p.last_name) AS patient_name
        FROM consultations c
        JOIN appointments a ON a.id = c.appointment_id
        JOIN patients p ON p.id = a.patient_id
        """;

    /** Consultation history for one patient, only when that patient is assigned to the doctor. */
    public List<Consultation> getConsultationHistory(long doctorId, long patientId) throws SQLException {
        String sql = SELECT + """
             WHERE a.patient_id = ?
               AND EXISTS (SELECT 1 FROM doctor_patients dp
                           WHERE dp.patient_id = a.patient_id AND dp.doctor_id = ?)
             ORDER BY c.consultation_date DESC
            """;
        List<Consultation> history = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setLong(1, patientId);
            st.setLong(2, doctorId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) history.add(map(rs));
            }
        }
        return history;
    }

    /** All consultations written by one doctor. */
    public List<Consultation> listByDoctor(long doctorId, int limit) throws SQLException {
        List<Consultation> history = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(SELECT + " WHERE a.doctor_id = ? ORDER BY c.consultation_date DESC LIMIT ?")) {
            st.setLong(1, doctorId);
            st.setInt(2, limit);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) history.add(map(rs));
            }
        }
        return history;
    }

    /** Administrator activity feed: latest consultations with doctor and patient names. */
    public List<java.util.Map<String, Object>> listRecentWithNames(int limit) throws SQLException {
        String sql = """
            SELECT c.id, c.diagnosis, c.consultation_date,
                   CONCAT(p.first_name, ' ', p.last_name) AS patient_name,
                   CONCAT('Dr. ', d.first_name, ' ', d.last_name) AS doctor_name
            FROM consultations c
            JOIN appointments a ON a.id = c.appointment_id
            JOIN patients p ON p.id = a.patient_id
            JOIN doctors d ON d.id = a.doctor_id
            ORDER BY c.consultation_date DESC
            LIMIT ?
            """;
        List<java.util.Map<String, Object>> rows = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, limit);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("diagnosis", rs.getString("diagnosis"));
                    m.put("patientName", rs.getString("patient_name"));
                    m.put("doctorName", rs.getString("doctor_name"));
                    m.put("consultationDate", rs.getTimestamp("consultation_date").toLocalDateTime().toString());
                    rows.add(m);
                }
            }
        }
        return rows;
    }

    public Consultation find(long doctorId, long consultationId) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(SELECT + " WHERE c.id = ? AND a.doctor_id = ?")) {
            st.setLong(1, consultationId);
            st.setLong(2, doctorId);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /**
     * Records a consultation for a completed appointment that belongs to the doctor.
     * Returns -1 when the appointment is not completed or not the doctor's.
     */
    public long create(long doctorId, long appointmentId, String diagnosis, String treatment, String notes)
            throws SQLException {
        String sql = """
            INSERT INTO consultations (appointment_id, diagnosis, treatment, notes)
            SELECT a.id, ?, ?, ? FROM appointments a
            WHERE a.id = ? AND a.doctor_id = ? AND a.status = 'completed'
            """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, diagnosis);
            st.setString(2, treatment);
            st.setString(3, notes);
            st.setLong(4, appointmentId);
            st.setLong(5, doctorId);
            if (st.executeUpdate() == 0) return -1;
            try (ResultSet keys = st.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        }
    }

    public boolean update(long doctorId, long consultationId, String diagnosis, String treatment, String notes)
            throws SQLException {
        String sql = """
            UPDATE consultations c
            JOIN appointments a ON a.id = c.appointment_id
            SET c.diagnosis = ?, c.treatment = ?, c.notes = ?
            WHERE c.id = ? AND a.doctor_id = ?
            """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, diagnosis);
            st.setString(2, treatment);
            st.setString(3, notes);
            st.setLong(4, consultationId);
            st.setLong(5, doctorId);
            return st.executeUpdate() > 0;
        }
    }

    public boolean existsForAppointment(long appointmentId) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement("SELECT 1 FROM consultations WHERE appointment_id = ?")) {
            st.setLong(1, appointmentId);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        }
    }

    public long countAll() throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement("SELECT COUNT(*) FROM consultations");
             ResultSet rs = st.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private Consultation map(ResultSet rs) throws SQLException {
        Timestamp date = rs.getTimestamp("consultation_date");
        return new Consultation(
            rs.getInt("id"),
            rs.getInt("appointment_id"),
            rs.getString("diagnosis"),
            rs.getString("treatment"),
            rs.getString("notes"),
            date == null ? null : date.toLocalDateTime(),
            rs.getInt("patient_id"),
            rs.getString("patient_name"),
            rs.getInt("doctor_id")
        );
    }
}
