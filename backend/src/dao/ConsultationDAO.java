
package dao;

import config.DatabaseConnection;
import model.Consultation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ConsultationDAO {

    public boolean addConsultation(
            int doctorId, Consultation consultation)
            throws SQLException {

        String sql = """
            INSERT INTO consultations
            (appointment_id, diagnosis, treatment, notes)
            SELECT a.id, ?, ?, ?
            FROM appointments a
            WHERE a.id = ?
              AND a.doctor_id = ?
              AND a.status = 'completed'
            """;

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement stmt = con.prepareStatement(sql)
        ) {
            stmt.setString(1, consultation.getDiagnosis());
            stmt.setString(2, consultation.getTreatment());
            stmt.setString(3, consultation.getNotes());
            stmt.setInt(4, consultation.getAppointmentId());
            stmt.setInt(5, doctorId);

            int rows = stmt.executeUpdate();

            return rows > 0;
        }
    }



public List<Consultation> getConsultationHistory(
        int doctorId, int patientId) throws SQLException {

    List<Consultation> history = new ArrayList<>();

    String sql = """
        SELECT c.*
        FROM consultations c
        JOIN appointments a
            ON c.appointment_id = a.id
        JOIN doctor_patients dp
            ON a.patient_id = dp.patient_id
        WHERE dp.doctor_id = ?
          AND a.patient_id = ?
        ORDER BY c.consultation_date DESC
        """;

    try (
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement stmt = con.prepareStatement(sql)
    ) {
        stmt.setInt(1, doctorId);
        stmt.setInt(2, patientId);

        try (ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {

                Consultation consultation = new Consultation(
                    rs.getInt("id"),
                    rs.getInt("appointment_id"),
                    rs.getString("diagnosis"),
                    rs.getString("treatment"),
                    rs.getString("notes"),
                    rs.getTimestamp("consultation_date").toLocalDateTime()
                );

                history.add(consultation);
            }
        }
    }

    return history;
}



public boolean updateConsultation(
        int doctorId, Consultation consultation)
        throws SQLException {

    String sql = """
        UPDATE consultations c
        JOIN appointments a
            ON c.appointment_id = a.id
        SET c.diagnosis = ?,
            c.treatment = ?,
            c.notes = ?
        WHERE c.id = ?
          AND a.doctor_id = ?
        """;

    try (
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement stmt = con.prepareStatement(sql)
    ) {
        stmt.setString(1, consultation.getDiagnosis());
        stmt.setString(2, consultation.getTreatment());
        stmt.setString(3, consultation.getNotes());
        stmt.setInt(4, consultation.getId());
        stmt.setInt(5, doctorId);

        int rows = stmt.executeUpdate();

        return rows > 0;
    }
}


}
