package model;

import java.time.LocalDateTime;

public class Appointment {

    private final int id;
    private final int doctorId;
    private final int patientId;
    private final LocalDateTime appointmentDate;
    private final String status;
    private final String reason;
    private final String patientFirstName;
    private final String patientLastName;
    private final String patientPhone;

    public Appointment(int id, int doctorId, int patientId,
                       LocalDateTime appointmentDate,
                       String status, String reason) {
        this(id, doctorId, patientId, appointmentDate, status, reason, null, null, null);
    }

    public Appointment(int id, int doctorId, int patientId,
                       LocalDateTime appointmentDate,
                       String status, String reason,
                       String patientFirstName, String patientLastName, String patientPhone) {
        this.id = id;
        this.doctorId = doctorId;
        this.patientId = patientId;
        this.appointmentDate = appointmentDate;
        this.status = status;
        this.reason = reason;
        this.patientFirstName = patientFirstName;
        this.patientLastName = patientLastName;
        this.patientPhone = patientPhone;
    }

    public int getId() {
        return id;
    }

    public int getDoctorId() {
        return doctorId;
    }

    public int getPatientId() {
        return patientId;
    }

    public LocalDateTime getAppointmentDate() {
        return appointmentDate;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public String getPatientFirstName() {
        return patientFirstName;
    }

    public String getPatientLastName() {
        return patientLastName;
    }

    public String getPatientPhone() {
        return patientPhone;
    }
}
