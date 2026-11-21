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

    /**
     * Database availability for the public /api/health endpoint.
     *
     * <p>Only "ok" or "unavailable" is returned. Driver messages can contain the
     * host, port, user and database name, so the detailed error is written to the
     * server log instead of the HTTP response.</p>
     */
    public static String status() {
        try (Connection con = getConnection(); Statement st = con.createStatement()) {
            st.execute("SELECT 1");
            return "ok";
        } catch (SQLException e) {
            System.err.println("Database health check failed: " + e.getMessage());
            return "unavailable";
        }
    }
}