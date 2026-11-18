
package model;

import java.time.LocalDateTime;

public class Appointment {

    private int id;
    private int doctorId;
    private int patientId;
    private LocalDateTime appointmentDate;
    private String status;
    private String reason;

    public Appointment(int id, int doctorId, int patientId,
                       LocalDateTime appointmentDate,
                       String status, String reason) {

        this.id = id;
        this.doctorId = doctorId;
        this.patientId = patientId;
        this.appointmentDate = appointmentDate;
        this.status = status;
        this.reason = reason;
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
}
