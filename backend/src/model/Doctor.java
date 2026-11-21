package model;

/** A doctor profile linked to a staff login account. */
public class Doctor {

    private final int id;
    private final int userId;
    private final String staffId;
    private final String firstName;
    private final String lastName;
    private final String specialty;
    private final String phone;
    private final String office;
    private final String status;
    private final String email;
    private final boolean accountActive;
    private final String workDays;
    private final String firstAppointment;
    private final String lastAppointment;
    private final int visitLength;

    public Doctor(int id, int userId, String staffId, String firstName, String lastName, String specialty,
                  String phone, String office, String status, String email, boolean accountActive,
                  String workDays, String firstAppointment, String lastAppointment, int visitLength) {
        this.id = id;
        this.userId = userId;
        this.staffId = staffId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.specialty = specialty;
        this.phone = phone;
        this.office = office;
        this.status = status;
        this.email = email;
        this.accountActive = accountActive;
        this.workDays = workDays;
        this.firstAppointment = firstAppointment;
        this.lastAppointment = lastAppointment;
        this.visitLength = visitLength;
    }

    public int getId() {
        return id;
    }

    public int getUserId() {
        return userId;
    }

    public String getStaffId() {
        return staffId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return ("Dr. " + firstName + " " + lastName).replaceAll("\\s+", " ").strip();
    }

    public String getSpecialty() {
        return specialty;
    }

    public String getPhone() {
        return phone;
    }

    public String getOffice() {
        return office;
    }

    public String getStatus() {
        return status;
    }

    public String getEmail() {
        return email;
    }

    public boolean isAccountActive() {
        return accountActive;
    }

    public String getWorkDays() {
        return workDays;
    }

    public String getFirstAppointment() {
        return firstAppointment;
    }

    public String getLastAppointment() {
        return lastAppointment;
    }

    public int getVisitLength() {
        return visitLength;
    }
}
