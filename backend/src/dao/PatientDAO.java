
package dao;

import config.DatabaseConnection;
import model.Patient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PatientDAO {

    // Get all patients assigned to a doctor
    public List<Patient> getPatientsByDoctor(int doctorId)
            throws SQLException {

        List<Patient> patients = new ArrayList<>();

        String sql = """
            SELECT p.*
            FROM patients p
            JOIN doctor_patients dp
                ON p.id = dp.patient_id
            WHERE dp.doctor_id = ?
            """;

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement stmt = con.prepareStatement(sql)
        ) {
            stmt.setInt(1, doctorId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Patient patient = new Patient(
                        rs.getInt("id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getDate("birth_date").toLocalDate(),
                        rs.getString("gender"),
                        rs.getString("phone"),
                        rs.getString("address")
                    );

                    patients.add(patient);
                }
            }
        }

        return patients;
    }

    // Get one patient, only if assigned to the doctor
    public Patient getPatientById(int doctorId, int patientId)
            throws SQLException {

        String sql = """
            SELECT p.*
            FROM patients p
            JOIN doctor_patients dp
                ON p.id = dp.patient_id
            WHERE dp.doctor_id = ?
              AND p.id = ?
            """;

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement stmt = con.prepareStatement(sql)
        ) {
            stmt.setInt(1, doctorId);
            stmt.setInt(2, patientId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Patient(
                        rs.getInt("id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getDate("birth_date").toLocalDate(),
                        rs.getString("gender"),
                        rs.getString("phone"),
                        rs.getString("address")
                    );
                }
            }
        }

        return null;
    }

} // Closes PatientDAO
