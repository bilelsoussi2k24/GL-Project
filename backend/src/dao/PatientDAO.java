package dao;

import config.DatabaseConnection;
import model.Patient;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PatientDAO {

    private static final String COLUMNS =
        "p.id, p.first_name, p.last_name, p.birth_date, p.gender, p.phone, p.address";

    /** Patients assigned to a doctor, newest first. */
    public List<Patient> getPatientsByDoctor(long doctorId, String query) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT DISTINCT
            """).append(COLUMNS).append("""
             FROM patients p
            JOIN doctor_patients dp ON p.id = dp.patient_id
            WHERE dp.doctor_id = ?
            """);
        List<Object> args = new ArrayList<>();
        args.add(doctorId);
        if (query != null && !query.isBlank()) {
            sql.append(" AND (p.first_name LIKE ? OR p.last_name LIKE ? OR p.phone LIKE ?)");
            String like = "%" + query.strip() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY p.last_name, p.first_name");

        List<Patient> patients = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) st.setObject(i + 1, args.get(i));
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) patients.add(map(rs));
            }
        }
        return patients;
    }

    public Patient getPatientById(long doctorId, long patientId) throws SQLException {
        String sql = "SELECT " + COLUMNS + """
             FROM patients p
            JOIN doctor_patients dp ON p.id = dp.patient_id
            WHERE dp.doctor_id = ? AND p.id = ?
            """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setLong(1, doctorId);
            st.setLong(2, patientId);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public Patient findById(long patientId) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement("SELECT " + COLUMNS + " FROM patients p WHERE p.id = ?")) {
            st.setLong(1, patientId);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Every patient for the administrator, with their assigned doctors and visit counts. */
    public List<Map<String, Object>> listAll(String query) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT p.id, p.first_name, p.last_name, p.birth_date, p.gender, p.phone, p.address,
                   (SELECT COUNT(*) FROM appointments a WHERE a.patient_id = p.id) AS appointments,
                   (SELECT COUNT(*) FROM consultations c JOIN appointments a2 ON a2.id = c.appointment_id
                     WHERE a2.patient_id = p.id) AS consultations,
                   (SELECT GROUP_CONCAT(CONCAT('Dr. ', d.first_name, ' ', d.last_name) ORDER BY d.last_name SEPARATOR ', ')
                      FROM doctor_patients dp JOIN doctors d ON d.id = dp.doctor_id
                     WHERE dp.patient_id = p.id) AS doctors
            FROM patients p
            WHERE 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            sql.append(" AND (p.first_name LIKE ? OR p.last_name LIKE ? OR p.phone LIKE ? OR p.address LIKE ?)");
            String like = "%" + query.strip() + "%";
            for (int i = 0; i < 4; i++) args.add(like);
        }
        sql.append(" ORDER BY p.last_name, p.first_name");

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) st.setObject(i + 1, args.get(i));
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("firstName", rs.getString("first_name"));
                    m.put("lastName", rs.getString("last_name"));
                    m.put("name", rs.getString("first_name") + " " + rs.getString("last_name"));
                    m.put("hhId", String.format("HH-%06d", rs.getInt("id")));
                    Date birth = rs.getDate("birth_date");
                    m.put("birthDate", birth == null ? null : birth.toLocalDate().toString());
                    m.put("gender", rs.getString("gender"));
                    m.put("phone", rs.getString("phone"));
                    m.put("address", rs.getString("address"));
                    m.put("appointments", rs.getLong("appointments"));
                    m.put("consultations", rs.getLong("consultations"));
                    m.put("doctors", rs.getString("doctors"));
                    rows.add(m);
                }
            }
        }
        return rows;
    }

    public long create(String firstName, String lastName, LocalDate birthDate, String gender,
                       String phone, String address) throws SQLException {
        String sql = """
            INSERT INTO patients (first_name, last_name, birth_date, gender, phone, address)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, firstName);
            st.setString(2, lastName);
            st.setDate(3, Date.valueOf(birthDate));
            st.setString(4, gender);
            st.setString(5, phone);
            st.setString(6, address);
            st.executeUpdate();
            try (ResultSet keys = st.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        }
        throw new SQLException("Could not read the new patient id");
    }

    /**
     * Creates the patient and, when a doctor is supplied, the assignment in one
     * transaction. Validation happens before the transaction, so a failed assignment
     * never leaves an unassigned patient behind.
     *
     * @return -1 when the doctor does not exist or is deactivated; nothing is stored.
     */
    public long createWithDoctor(String firstName, String lastName, LocalDate birthDate, String gender,
                                 String phone, String address, Long doctorId) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                if (doctorId != null) {
                    try (PreparedStatement check = con.prepareStatement(
                            "SELECT status FROM doctors WHERE id = ? FOR UPDATE")) {
                        check.setLong(1, doctorId);
                        try (ResultSet rs = check.executeQuery()) {
                            if (!rs.next() || "inactive".equals(rs.getString("status"))) {
                                con.rollback();
                                return -1;
                            }
                        }
                    }
                }

                long patientId;
                try (PreparedStatement st = con.prepareStatement("""
                        INSERT INTO patients (first_name, last_name, birth_date, gender, phone, address)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    st.setString(1, firstName);
                    st.setString(2, lastName);
                    st.setDate(3, Date.valueOf(birthDate));
                    st.setString(4, gender);
                    st.setString(5, phone);
                    st.setString(6, address);
                    st.executeUpdate();
                    try (ResultSet keys = st.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Could not read the new patient id");
                        patientId = keys.getLong(1);
                    }
                }

                if (doctorId != null) {
                    try (PreparedStatement st = con.prepareStatement(
                            "INSERT INTO doctor_patients (doctor_id, patient_id) VALUES (?, ?)")) {
                        st.setLong(1, doctorId);
                        st.setLong(2, patientId);
                        st.executeUpdate();
                    }
                }
                con.commit();
                return patientId;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public void update(long id, String firstName, String lastName, LocalDate birthDate, String gender,
                       String phone, String address) throws SQLException {
        String sql = """
            UPDATE patients SET first_name = ?, last_name = ?, birth_date = ?, gender = ?, phone = ?, address = ?
            WHERE id = ?
            """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, firstName);
            st.setString(2, lastName);
            st.setDate(3, Date.valueOf(birthDate));
            st.setString(4, gender);
            st.setString(5, phone);
            st.setString(6, address);
            st.setLong(7, id);
            st.executeUpdate();
        }
    }

    /** Gives the patient to a doctor (adds a doctor_patients row when missing). */
    public void assignDoctor(long patientId, long doctorId) throws SQLException {
        String sql = "INSERT IGNORE INTO doctor_patients (doctor_id, patient_id) VALUES (?, ?)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setLong(1, doctorId);
            st.setLong(2, patientId);
            st.executeUpdate();
        }
    }

    public long count() throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement("SELECT COUNT(*) FROM patients");
             ResultSet rs = st.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private Patient map(ResultSet rs) throws SQLException {
        Date birth = rs.getDate("birth_date");
        return new Patient(
            rs.getInt("id"),
            rs.getString("first_name"),
            rs.getString("last_name"),
            birth == null ? null : birth.toLocalDate(),
            rs.getString("gender"),
            rs.getString("phone"),
            rs.getString("address")
        );
    }
}
