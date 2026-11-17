
package model;

import java.time.LocalDateTime;

public class Consultation {

    private int id;
    private int appointmentId;
    private String diagnosis;
    private String treatment;
    private String notes;
    private LocalDateTime consultationDate;

    public Consultation(
            int id,
            int appointmentId,
            String diagnosis,
            String treatment,
            String notes,
            LocalDateTime consultationDate) {

        this.id = id;
        this.appointmentId = appointmentId;
        this.diagnosis = diagnosis;
        this.treatment = treatment;
        this.notes = notes;
        this.consultationDate = consultationDate;
    }

    public int getId() {
        return id;
    }

    public int getAppointmentId() {
        return appointmentId;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public String getTreatment() {
        return treatment;
    }

    public String getNotes() {
        return notes;
    }

    public LocalDateTime getConsultationDate() {
        return consultationDate;
    }
}
