package config;

import dao.UserDAO;
import security.PasswordHasher;

import java.io.PrintStream;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Additive, idempotent database migration.
 *
 * Nothing is dropped and no existing row is overwritten. Tables and columns are
 * created only when missing, so an existing hospital_db from the original dump
 * is upgraded in place.
 */
public final class DatabaseMigrator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private DatabaseMigrator() {
    }

    public static void migrate(PrintStream log) throws SQLException {
        ensureDatabase(log);

        try (Connection con = DatabaseConnection.getConnection()) {
            acquireLock(con);
            try {
                createTables(con);
                addMissingColumns(con, log);
                widenStatusEnums(con);
                backfillStaffIds(con);
                addIndexes(con);
                seedSettings(con);
                ensureAdministrator(con, log);
            } finally {
                releaseLock(con);
            }
        }
    }

    /**
     * Serialises migration and bootstrap between application instances. Without this,
     * two servers starting together could both run the administrator bootstrap.
     */
    private static void acquireLock(Connection con) throws SQLException {
        try (PreparedStatement st = con.prepareStatement("SELECT GET_LOCK('haven_schema_migration', 30)");
             ResultSet rs = st.executeQuery()) {
            if (!rs.next() || rs.getInt(1) != 1) {
                throw new SQLException("Could not acquire the migration lock; another instance is migrating.");
            }
        }
    }

    private static void releaseLock(Connection con) {
        try (PreparedStatement st = con.prepareStatement("SELECT RELEASE_LOCK('haven_schema_migration')")) {
            st.executeQuery();
        } catch (SQLException e) {
            System.err.println("Could not release the migration lock: " + e.getMessage());
        }
    }

    /** Creates the schema when the configured database does not exist yet. */
    private static void ensureDatabase(PrintStream log) throws SQLException {
        String url = AppConfig.dbUrl();
        java.util.regex.Matcher matcher =
            java.util.regex.Pattern.compile("^jdbc:mysql://([^/]+)/([^?]+)").matcher(url);
        if (!matcher.find()) return;
        String host = matcher.group(1);
        String database = matcher.group(2);
        String serverUrl = "jdbc:mysql://" + host + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (Connection con = DriverManager.getConnection(serverUrl, AppConfig.dbUser(), AppConfig.dbPassword());
             Statement st = con.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + database.replace("`", "")
                + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            log.println("Database '" + database + "' is ready.");
        } catch (SQLException e) {
            log.println("Could not create the database automatically: " + e.getMessage());
            throw e;
        }
    }

    private static void createTables(Connection con) throws SQLException {
        List<String> statements = List.of(
            """
            CREATE TABLE IF NOT EXISTS users (
              id int NOT NULL AUTO_INCREMENT,
              email varchar(150) NOT NULL,
              password_hash varchar(255) NOT NULL,
              role enum('ADMIN','DOCTOR') NOT NULL,
              active tinyint(1) NOT NULL DEFAULT 1,
              full_name varchar(120) NOT NULL DEFAULT '',
              phone varchar(20) DEFAULT NULL,
              password_changed_at datetime DEFAULT NULL,
              created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              UNIQUE KEY email (email)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
            """,
            """
            CREATE TABLE IF NOT EXISTS doctors (
              id int NOT NULL AUTO_INCREMENT,
              user_id int NOT NULL,
              staff_id varchar(20) DEFAULT NULL,
              first_name varchar(50) NOT NULL,
              last_name varchar(50) NOT NULL,
              specialty varchar(100) NOT NULL,
              phone varchar(20) DEFAULT NULL,
              office varchar(80) DEFAULT NULL,
              status enum('active','leave','inactive') NOT NULL DEFAULT 'active',
              work_days varchar(20) NOT NULL DEFAULT '1,2,3,4,5',
              first_appointment time NOT NULL DEFAULT '08:00:00',
              last_appointment time NOT NULL DEFAULT '17:00:00',
              visit_length int NOT NULL DEFAULT 30,
              created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              UNIQUE KEY user_id (user_id),
              UNIQUE KEY staff_id (staff_id),
              CONSTRAINT doctors_ibfk_1 FOREIGN KEY (user_id) REFERENCES users (id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
            """,
            """
            CREATE TABLE IF NOT EXISTS patients (
              id int NOT NULL AUTO_INCREMENT,
              first_name varchar(50) NOT NULL,
              last_name varchar(50) NOT NULL,
              birth_date date NOT NULL,
              gender varchar(20) DEFAULT NULL,
              phone varchar(20) DEFAULT NULL,
              address varchar(255) DEFAULT NULL,
              created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              KEY idx_patients_name (last_name, first_name)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
            """,
            """
            CREATE TABLE IF NOT EXISTS doctor_patients (
              doctor_id int NOT NULL,
              patient_id int NOT NULL,
              assigned_at datetime DEFAULT CURRENT_TIMESTAMP,
              PRIMARY KEY (doctor_id, patient_id),
              KEY patient_id (patient_id),
              CONSTRAINT doctor_patients_ibfk_1 FOREIGN KEY (doctor_id) REFERENCES doctors (id),
              CONSTRAINT doctor_patients_ibfk_2 FOREIGN KEY (patient_id) REFERENCES patients (id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
            """,
            """
            CREATE TABLE IF NOT EXISTS appointments (
              id int NOT NULL AUTO_INCREMENT,
              doctor_id int NOT NULL,
              patient_id int NOT NULL,
              appointment_date datetime NOT NULL,
              status enum('scheduled','completed','cancelled','missed') NOT NULL DEFAULT 'scheduled',
              reason varchar(255) DEFAULT NULL,
              created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              KEY doctor_id (doctor_id, patient_id),
              KEY idx_appointments_date (appointment_date),
              CONSTRAINT appointments_ibfk_1 FOREIGN KEY (doctor_id, patient_id)
                REFERENCES doctor_patients (doctor_id, patient_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
            """,
            """
            CREATE TABLE IF NOT EXISTS consultations (
              id int NOT NULL AUTO_INCREMENT,
              appointment_id int NOT NULL,
              diagnosis text,
              treatment text,
              notes text,
              consultation_date datetime DEFAULT CURRENT_TIMESTAMP,
              updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              UNIQUE KEY appointment_id (appointment_id),
              CONSTRAINT consultations_ibfk_1 FOREIGN KEY (appointment_id) REFERENCES appointments (id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
            """,
            """
            CREATE TABLE IF NOT EXISTS auth_sessions (
              id int NOT NULL AUTO_INCREMENT,
              token_hash char(64) NOT NULL,
              user_id int NOT NULL,
              created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              expires_at datetime NOT NULL,
              PRIMARY KEY (id),
              UNIQUE KEY token_hash (token_hash),
              KEY idx_sessions_user (user_id),
              KEY idx_sessions_expiry (expires_at),
              CONSTRAINT sessions_ibfk_1 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
            """,
            """
            CREATE TABLE IF NOT EXISTS hospital_settings (
              setting_key varchar(60) NOT NULL,
              setting_value text,
              PRIMARY KEY (setting_key)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
            """
        );

        try (Statement st = con.createStatement()) {
            for (String sql : statements) st.executeUpdate(sql);
        }
    }

    /** Columns introduced after the original database dump. */
    private static void addMissingColumns(Connection con, PrintStream log) throws SQLException {
        Map<String, List<String[]>> additions = new LinkedHashMap<>();
        additions.put("users", List.of(
            new String[]{"full_name", "ALTER TABLE users ADD COLUMN full_name varchar(120) NOT NULL DEFAULT ''"},
            new String[]{"phone", "ALTER TABLE users ADD COLUMN phone varchar(20) DEFAULT NULL"},
            new String[]{"password_changed_at", "ALTER TABLE users ADD COLUMN password_changed_at datetime DEFAULT NULL"},
            new String[]{"created_at", "ALTER TABLE users ADD COLUMN created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP"},
            new String[]{"updated_at", "ALTER TABLE users ADD COLUMN updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"}
        ));
        additions.put("doctors", List.of(
            new String[]{"staff_id", "ALTER TABLE doctors ADD COLUMN staff_id varchar(20) DEFAULT NULL"},
            new String[]{"office", "ALTER TABLE doctors ADD COLUMN office varchar(80) DEFAULT NULL"},
            new String[]{"status", "ALTER TABLE doctors ADD COLUMN status enum('active','leave','inactive') NOT NULL DEFAULT 'active'"},
            new String[]{"work_days", "ALTER TABLE doctors ADD COLUMN work_days varchar(20) NOT NULL DEFAULT '1,2,3,4,5'"},
            new String[]{"first_appointment", "ALTER TABLE doctors ADD COLUMN first_appointment time NOT NULL DEFAULT '08:00:00'"},
            new String[]{"last_appointment", "ALTER TABLE doctors ADD COLUMN last_appointment time NOT NULL DEFAULT '17:00:00'"},
            new String[]{"visit_length", "ALTER TABLE doctors ADD COLUMN visit_length int NOT NULL DEFAULT 30"},
            new String[]{"created_at", "ALTER TABLE doctors ADD COLUMN created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP"},
            new String[]{"updated_at", "ALTER TABLE doctors ADD COLUMN updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"}
        ));
        additions.put("patients", List.of(
            new String[]{"created_at", "ALTER TABLE patients ADD COLUMN created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP"},
            new String[]{"updated_at", "ALTER TABLE patients ADD COLUMN updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"}
        ));
        additions.put("appointments", List.of(
            new String[]{"created_at", "ALTER TABLE appointments ADD COLUMN created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP"},
            new String[]{"updated_at", "ALTER TABLE appointments ADD COLUMN updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"}
        ));
        additions.put("consultations", List.<String[]>of(
            new String[]{"updated_at", "ALTER TABLE consultations ADD COLUMN updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"}
        ));

        try (Statement st = con.createStatement()) {
            for (Map.Entry<String, List<String[]>> table : additions.entrySet()) {
                for (String[] column : table.getValue()) {
                    if (!hasColumn(con, table.getKey(), column[0])) {
                        st.executeUpdate(column[1]);
                        log.println("Added column " + table.getKey() + "." + column[0]);
                    }
                }
            }
        }
    }

    private static void widenStatusEnums(Connection con) throws SQLException {
        String doctorStatus = columnType(con, "doctors", "status");
        if (doctorStatus != null && !doctorStatus.contains("inactive")) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("ALTER TABLE doctors MODIFY COLUMN status enum('active','leave','inactive') NOT NULL DEFAULT 'active'");
            }
        }
        String appointmentStatus = columnType(con, "appointments", "status");
        if (appointmentStatus != null && !appointmentStatus.contains("missed")) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("ALTER TABLE appointments MODIFY COLUMN status enum('scheduled','completed','cancelled','missed') NOT NULL DEFAULT 'scheduled'");
            }
        }
    }

    private static void backfillStaffIds(Connection con) throws SQLException {
        try (PreparedStatement st = con.prepareStatement(
                "UPDATE doctors SET staff_id = CONCAT('DR-', LPAD(id, 4, '0')) WHERE staff_id IS NULL OR staff_id = ''")) {
            int updated = st.executeUpdate();
            if (updated > 0) System.out.println("Assigned staff IDs to " + updated + " existing doctor(s).");
        }
    }

    private static void addIndexes(Connection con) throws SQLException {
        addIndexIfMissing(con, "doctors", "staff_id", "CREATE UNIQUE INDEX staff_id ON doctors (staff_id)");
        addIndexIfMissing(con, "patients", "idx_patients_name", "CREATE INDEX idx_patients_name ON patients (last_name, first_name)");
        addIndexIfMissing(con, "appointments", "idx_appointments_date", "CREATE INDEX idx_appointments_date ON appointments (appointment_date)");
    }

    private static void addIndexIfMissing(Connection con, String table, String index, String sql) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        try (ResultSet rs = meta.getIndexInfo(con.getCatalog(), null, table, false, false)) {
            while (rs.next()) {
                if (index.equalsIgnoreCase(rs.getString("INDEX_NAME"))) return;
            }
        }
        try (Statement st = con.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private static void seedSettings(Connection con) throws SQLException {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("name", "Haven Hospital");
        defaults.put("phone", "[PHONE NUMBER]");
        defaults.put("erNumber", "[ER NUMBER]");
        defaults.put("email", "[EMAIL]");
        defaults.put("street", "[STREET ADDRESS]");
        defaults.put("cityState", "[CITY], [STATE]");
        defaults.put("parking", "[PARKING DETAILS]");
        defaults.put("visitingStart", "10:00");
        defaults.put("visitingEnd", "19:00");
        defaults.put("maxVisitors", "2");

        String sql = "INSERT IGNORE INTO hospital_settings (setting_key, setting_value) VALUES (?, ?)";
        try (PreparedStatement st = con.prepareStatement(sql)) {
            for (Map.Entry<String, String> entry : defaults.entrySet()) {
                st.setString(1, entry.getKey());
                st.setString(2, entry.getValue());
                st.addBatch();
            }
            st.executeBatch();
        }
    }

    /**
     * Makes sure an administrator can sign in.
     *
     * <p>Rules, in order:</p>
     * <ul>
     *   <li>If an active administrator with a real password hash exists, nothing is
     *       changed (repeated starts never reset a working password).</li>
     *   <li>An existing account at ADMIN_EMAIL that is not an administrator is never
     *       modified: its role and password stay as they are, and startup reports the
     *       conflict so the operator can choose another address.</li>
     *   <li>An administrator with a legacy placeholder hash gets a real password; an
     *       inactive administrator with a usable hash is reactivated, not reset.</li>
     *   <li>If no active usable administrator exists and ADMIN_EMAIL is free, the
     *       bootstrap account is created.</li>
     * </ul>
     */
    private static void ensureAdministrator(Connection con, PrintStream log) throws SQLException {
        UserDAO users = new UserDAO();
        users.renameDefaultAdminTitle();
        String email = AppConfig.initialAdminEmail();

        if (users.countActiveUsableAdmins(con) > 0) return;

        model.User existing = users.findByEmail(con, email);
        if (existing != null && !"ADMIN".equals(existing.getRole())) {
            throw new SQLException("ADMIN_EMAIL " + email + " belongs to an existing "
                + existing.getRole() + " account. No account was changed. Set ADMIN_EMAIL to a"
                + " free address (or reset that account with tool.SetPassword) and start again.");
        }

        boolean needsPassword = existing == null || PasswordHasher.isLegacy(existing.getPasswordHash());
        String password = null;
        if (needsPassword) {
            String configured = AppConfig.initialAdminPassword();
            boolean generated = configured == null || configured.isBlank();
            if (!generated && !PasswordHasher.isStrongEnough(configured)) {
                throw new SQLException("ADMIN_PASSWORD must be at least 8 characters and contain"
                    + " a letter and a number.");
            }
            password = generated ? randomPassword() : configured;
        }

        con.setAutoCommit(false);
        try {
            if (existing == null) {
                users.create(con, email, PasswordHasher.hash(password), "ADMIN", "Administrator");
                log.println("Created the first administrator account.");
            } else if (needsPassword) {
                users.updatePassword(con, existing.getId(), PasswordHasher.hash(password));
                users.setActive(con, existing.getId(), true);
                log.println("Replaced the unusable placeholder password of " + email + ".");
            } else {
                users.setActive(con, existing.getId(), true);
                log.println("Reactivated the existing administrator " + email + " (password unchanged).");
            }
            con.commit();
        } catch (SQLException | RuntimeException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
        }

        if (password == null) return;

        log.println("--------------------------------------------------------------");
        log.println("Administrator sign-in:");
        log.println("  email:    " + email);
        if (AppConfig.initialAdminPassword() == null || AppConfig.initialAdminPassword().isBlank()) {
            log.println("  password: " + password);
            log.println("This password is printed once. Set ADMIN_PASSWORD in backend/.env before");
            log.println("the first start to choose it yourself. Change it after signing in.");
        } else {
            log.println("  password: (the ADMIN_PASSWORD value from your configuration)");
        }
        log.println("--------------------------------------------------------------");
    }

    private static String randomPassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 14; i++) sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return sb.append("7a").toString();
    }

    private static boolean hasColumn(Connection con, String table, String column) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        try (ResultSet rs = meta.getColumns(con.getCatalog(), null, table, column)) {
            return rs.next();
        }
    }

    private static String columnType(Connection con, String table, String column) throws SQLException {
        String sql = """
            SELECT COLUMN_TYPE FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
            """;
        try (PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, table);
            st.setString(2, column);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Used by the CLI to report which accounts still hold legacy placeholder hashes. */
    public static List<String> legacyAccounts(Connection con) throws SQLException {
        List<String> emails = new ArrayList<>();
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT email FROM users WHERE password_hash NOT LIKE 'pbkdf2_sha256$%'")) {
            while (rs.next()) emails.add(rs.getString(1));
        }
        return emails;
    }
}
