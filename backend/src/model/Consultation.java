package model;

import java.time.LocalDateTime;

public class Consultation {

    private final int id;
    private final int appointmentId;
    private final String diagnosis;
    private final String treatment;
    private final String notes;
    private final LocalDateTime consultationDate;
    private final Integer patientId;
    private final String patientName;
    private final Integer doctorId;

    public Consultation(int id, int appointmentId, String diagnosis, String treatment,
                        String notes, LocalDateTime consultationDate) {
        this(id, appointmentId, diagnosis, treatment, notes, consultationDate, null, null, null);
    }

    public Consultation(int id, int appointmentId, String diagnosis, String treatment,
                        String notes, LocalDateTime consultationDate,
                        Integer patientId, String patientName, Integer doctorId) {
        this.id = id;
        this.appointmentId = appointmentId;
        this.diagnosis = diagnosis;
        this.treatment = treatment;
        this.notes = notes;
        this.consultationDate = consultationDate;
        this.patientId = patientId;
        this.patientName = patientName;
        this.doctorId = doctorId;
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

    public Integer getPatientId() {
        return patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public Integer getDoctorId() {
        return doctorId;
    }
}
