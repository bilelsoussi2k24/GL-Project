import config.DatabaseMigrator;
import security.PasswordHasher;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Exercises the administrator bootstrap against a disposable MySQL server.
 *
 * <p>It never touches the configured application database: every scenario uses a
 * temporary database named {@code haven_test_boot_*}, created and dropped by this
 * test. Run it against a development MySQL instance, for example:</p>
 *
 * <pre>
 * MSYS_NO_PATHCONV=1 java -cp ".tmp/test-classes;backend/out;backend/lib/*" AdministratorBootstrapTest
 * </pre>
 *
 * <p>Connection settings come from {@code DB_USER} / {@code DB_PASSWORD} and from
 * {@code TEST_MYSQL_HOST_PORT} (default {@code 127.0.0.1:3307}).</p>
 */
public final class AdministratorBootstrapTest {

    private static final String HOST_PORT = env("TEST_MYSQL_HOST_PORT", "127.0.0.1:3307");
    private static final String USER = env("DB_USER", "root");
    private static final String PASSWORD = env("DB_PASSWORD", "");
    private static final String ADMIN_EMAIL = "bootstrap-admin@haven.test";
    private static final String ADMIN_PASSWORD = "Bootstrap12345";

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        if (System.getenv("DB_URL") != null && !System.getenv("DB_URL").contains("haven_test_boot")) {
            System.err.println("Refusing to run: DB_URL is set in the environment and does not point"
                + " at a haven_test_boot database. Unset it first.");
            System.exit(2);
        }

        emptyDatabaseGetsWorkingAdministrator();
        existingWorkingAdministratorIsPreserved();
        inactiveAdministratorIsReactivatedWithoutReset();
        doctorWithAdminEmailIsNeverModified();
        legacyPlaceholderIsReplaced();
        multipleAdministratorsArePreserved();
        concurrentStartupCreatesOneAdministrator();

        System.out.println(failures == 0
            ? "AdministratorBootstrapTest: all checks passed"
            : "AdministratorBootstrapTest: " + failures + " check(s) failed");
        System.exit(failures == 0 ? 0 : 1);
    }

    /* ------------------------------------------------------------- scenarios */

    private static void emptyDatabaseGetsWorkingAdministrator() throws Exception {
        String db = fresh("empty");
        configure(db, ADMIN_EMAIL, ADMIN_PASSWORD);
        migrate();
        check(count(db, "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'") == 1,
            "empty database: exactly one administrator is created");
        check(active(db, ADMIN_EMAIL), "empty database: the administrator is active");
        check(passwordWorks(db, ADMIN_EMAIL, ADMIN_PASSWORD),
            "empty database: the configured password works");
        drop(db);
    }

    private static void existingWorkingAdministratorIsPreserved() throws Exception {
        String db = fresh("existing");
        configure(db, ADMIN_EMAIL, ADMIN_PASSWORD);
        migrate();
        String before = hash(db, ADMIN_EMAIL);
        migrate();
        check(before.equals(hash(db, ADMIN_EMAIL)),
            "existing administrator: a second start does not reset the password");
        check(count(db, "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'") == 1,
            "existing administrator: no duplicate account is created");
        drop(db);
    }

    private static void inactiveAdministratorIsReactivatedWithoutReset() throws Exception {
        String db = fresh("inactive");
        configure(db, ADMIN_EMAIL, ADMIN_PASSWORD);
        migrate();
        String before = hash(db, ADMIN_EMAIL);
        exec(db, "UPDATE users SET active = 0 WHERE email = '" + ADMIN_EMAIL + "'");
        migrate();
        check(active(db, ADMIN_EMAIL), "inactive administrator: the account is reactivated");
        check(before.equals(hash(db, ADMIN_EMAIL)),
            "inactive administrator: the existing password is not reset");
        check(passwordWorks(db, ADMIN_EMAIL, ADMIN_PASSWORD),
            "inactive administrator: the original password still works");
        drop(db);
    }

    private static void doctorWithAdminEmailIsNeverModified() throws Exception {
        String db = fresh("doctor-email");
        createLegacyUsersTable(db);
        String doctorHash = PasswordHasher.hash("Doctor12345");
        insertLegacyUser(db, ADMIN_EMAIL, doctorHash, "DOCTOR", 1);

        configure(db, ADMIN_EMAIL, ADMIN_PASSWORD);
        boolean failed = false;
        try {
            migrate();
        } catch (SQLException e) {
            failed = true;
        }
        check(failed, "doctor with ADMIN_EMAIL: bootstrap refuses instead of modifying the account");
        check("DOCTOR".equals(scalar(db, "SELECT role FROM users WHERE email = '" + ADMIN_EMAIL + "'")),
            "doctor with ADMIN_EMAIL: the role is unchanged");
        check(doctorHash.equals(hash(db, ADMIN_EMAIL)),
            "doctor with ADMIN_EMAIL: the doctor password is unchanged");
        check(active(db, ADMIN_EMAIL), "doctor with ADMIN_EMAIL: the account stays active");
        check(passwordWorks(db, ADMIN_EMAIL, "Doctor12345"),
            "doctor with ADMIN_EMAIL: the doctor can still use their password");
        drop(db);
    }

    private static void legacyPlaceholderIsReplaced() throws Exception {
        String db = fresh("legacy");
        createLegacyUsersTable(db);
        insertLegacyUser(db, ADMIN_EMAIL, "test_hash_admin", "ADMIN", 1);

        configure(db, ADMIN_EMAIL, ADMIN_PASSWORD);
        migrate();
        check(!PasswordHasher.isLegacy(hash(db, ADMIN_EMAIL)),
            "legacy placeholder: a real password hash replaces the placeholder");
        check(passwordWorks(db, ADMIN_EMAIL, ADMIN_PASSWORD),
            "legacy placeholder: the configured password works");
        check(active(db, ADMIN_EMAIL), "legacy placeholder: the administrator is active");
        drop(db);
    }

    private static void multipleAdministratorsArePreserved() throws Exception {
        String db = fresh("multiple");
        configure(db, ADMIN_EMAIL, ADMIN_PASSWORD);
        migrate();
        insertLegacyUser(db, "second-admin@haven.test", PasswordHasher.hash("Second12345"), "ADMIN", 1);
        String firstHash = hash(db, ADMIN_EMAIL);
        String secondHash = hash(db, "second-admin@haven.test");

        migrate();
        check(count(db, "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'") == 2,
            "multiple administrators: both accounts are kept");
        check(firstHash.equals(hash(db, ADMIN_EMAIL)) && secondHash.equals(hash(db, "second-admin@haven.test")),
            "multiple administrators: neither password is reset");
        drop(db);
    }

    private static void concurrentStartupCreatesOneAdministrator() throws Exception {
        String db = fresh("concurrent");
        configure(db, ADMIN_EMAIL, ADMIN_PASSWORD);
        List<Throwable> errors = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        Runnable task = () -> {
            try {
                start.await();
                migrate();
            } catch (Throwable e) {
                synchronized (errors) {
                    errors.add(e);
                }
            }
        };
        Thread first = new Thread(task, "bootstrap-1");
        Thread second = new Thread(task, "bootstrap-2");
        first.start();
        second.start();
        start.countDown();
        first.join(TimeUnit.SECONDS.toMillis(60));
        second.join(TimeUnit.SECONDS.toMillis(60));

        check(errors.isEmpty(), "concurrent startup: both migrations succeed (" + errors + ")");
        check(count(db, "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'") == 1,
            "concurrent startup: only one administrator is created");
        drop(db);
    }

    /* --------------------------------------------------------------- helpers */

    private static String fresh(String name) throws SQLException {
        String db = "haven_test_boot_" + name.replace('-', '_');
        drop(db);
        execServer("CREATE DATABASE `" + db + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        return db;
    }

    private static void createLegacyUsersTable(String db) throws SQLException {
        exec(db, """
            CREATE TABLE users (
              id int NOT NULL AUTO_INCREMENT,
              email varchar(150) NOT NULL,
              password_hash varchar(255) NOT NULL,
              role enum('ADMIN','DOCTOR') NOT NULL,
              active tinyint(1) DEFAULT '1',
              PRIMARY KEY (id),
              UNIQUE KEY email (email)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
            """);
    }

    private static void insertLegacyUser(String db, String email, String hash, String role, int active)
            throws SQLException {
        try (Connection con = open(db);
             PreparedStatement st = con.prepareStatement(
                 "INSERT INTO users (email, password_hash, role, active) VALUES (?, ?, ?, ?)")) {
            st.setString(1, email);
            st.setString(2, hash);
            st.setString(3, role);
            st.setInt(4, active);
            st.executeUpdate();
        }
    }

    private static void configure(String db, String email, String password) {
        System.setProperty("DB_URL", "jdbc:mysql://" + HOST_PORT + "/" + db
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        System.setProperty("DB_USER", USER);
        System.setProperty("DB_PASSWORD", PASSWORD);
        System.setProperty("ADMIN_EMAIL", email);
        System.setProperty("ADMIN_PASSWORD", password);
    }

    private static void migrate() throws SQLException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DatabaseMigrator.migrate(new PrintStream(buffer, true, StandardCharsets.UTF_8));
    }

    private static boolean active(String db, String email) throws SQLException {
        return "1".equals(scalar(db, "SELECT active FROM users WHERE email = '" + email + "'"));
    }

    private static boolean passwordWorks(String db, String email, String password) throws SQLException {
        return PasswordHasher.verify(password, hash(db, email));
    }

    private static String hash(String db, String email) throws SQLException {
        return scalar(db, "SELECT password_hash FROM users WHERE email = '" + email + "'");
    }

    private static long count(String db, String sql) throws SQLException {
        return Long.parseLong(scalar(db, sql));
    }

    private static String scalar(String db, String sql) throws SQLException {
        try (Connection con = open(db);
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static void exec(String db, String sql) throws SQLException {
        try (Connection con = open(db); Statement st = con.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private static void execServer(String sql) throws SQLException {
        try (Connection con = DriverManager.getConnection(serverUrl(), USER, PASSWORD);
             Statement st = con.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private static void drop(String db) throws SQLException {
        execServer("DROP DATABASE IF EXISTS `" + db + "`");
    }

    private static Connection open(String db) throws SQLException {
        return DriverManager.getConnection("jdbc:mysql://" + HOST_PORT + "/" + db
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", USER, PASSWORD);
    }

    private static String serverUrl() {
        return "jdbc:mysql://" + HOST_PORT + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            failures++;
            System.out.println("FAILED: " + label);
        }
    }
}
