
package dao;

import config.DatabaseConnection;
import model.Appointment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class AppointmentDAO {

    public void addAppointment(Appointment appointment)
            throws SQLException {

        String sql = """
            INSERT INTO appointments
            (doctor_id, patient_id, appointment_date, status, reason)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement stmt = con.prepareStatement(sql)
        ) {
            stmt.setInt(1, appointment.getDoctorId());
            stmt.setInt(2, appointment.getPatientId());
            stmt.setTimestamp(3,
                Timestamp.valueOf(appointment.getAppointmentDate()));
            stmt.setString(4, appointment.getStatus());
            stmt.setString(5, appointment.getReason());

            int rows = stmt.executeUpdate();

            if (rows > 0) {
                System.out.println("Appointment added successfully!");
            }
        }
    }




public List<Appointment> getAppointmentsByDoctor(int doctorId)
        throws SQLException {

    List<Appointment> appointments = new ArrayList<>();

    String sql = """
        SELECT *
        FROM appointments
        WHERE doctor_id = ?
        ORDER BY appointment_date ASC
        """;

    try (
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement stmt = con.prepareStatement(sql)
    ) {
        stmt.setInt(1, doctorId);

        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {

                Appointment appointment = new Appointment(
                    rs.getInt("id"),
                    rs.getInt("doctor_id"),
                    rs.getInt("patient_id"),
                    rs.getTimestamp("appointment_date").toLocalDateTime(),
                    rs.getString("status"),
                    rs.getString("reason")
                );

                appointments.add(appointment);
            }
        }
    }

    return appointments;
}




public boolean updateAppointmentStatus(
        int doctorId, int appointmentId, String newStatus)
        throws SQLException {

    if (!newStatus.equals("scheduled") &&
        !newStatus.equals("completed") &&
        !newStatus.equals("cancelled")) {
        throw new IllegalArgumentException("Invalid appointment status");
    }

    String sql = """
        UPDATE appointments
        SET status = ?
        WHERE id = ?
          AND doctor_id = ?
        """;

    try (
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement stmt = con.prepareStatement(sql)
    ) {
        stmt.setString(1, newStatus);
        stmt.setInt(2, appointmentId);
        stmt.setInt(3, doctorId);

        int rows = stmt.executeUpdate();

        return rows > 0;
    }
}


}
