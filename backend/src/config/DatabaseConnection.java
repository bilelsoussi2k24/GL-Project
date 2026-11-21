package config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates MySQL connections from configuration (DB_URL, DB_USER, DB_PASSWORD). */
public final class DatabaseConnection {

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(AppConfig.dbUrl(), AppConfig.dbUser(), AppConfig.dbPassword());
    }

    /** Database status for /api/health. */
    public static String status() {
        try (Connection con = getConnection(); Statement st = con.createStatement()) {
            st.execute("SELECT 1");
            return "ok (MySQL " + con.getMetaData().getDatabaseProductVersion() + ")";
        } catch (SQLException e) {
            return "unavailable: " + e.getMessage();
        }
    }
}