package model;

import java.time.LocalDate;

public class Patient {
    private final int id;
    private final String firstName;
    private final String lastName;
    private final LocalDate birthDate;
    private final String gender;
    private final String phone;
    private final String address;

    public Patient(int id, String firstName, String lastName,
                   LocalDate birthDate, String gender,
                   String phone, String address) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthDate = birthDate;
        this.gender = gender;
        this.phone = phone;
        this.address = address;
    }

    public int getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return (firstName + " " + lastName).replaceAll("\\s+", " ").strip();
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getGender() {
        return gender;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }
}