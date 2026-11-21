import api.HospitalServer;
import config.AppConfig;
import config.DatabaseMigrator;
import security.AuthService;

import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;

/** Haven Hospital backend entry point. */
public final class Main {

    public static void main(String[] args) throws Exception {
        if (AppConfig.autoMigrate()) {
            try {
                DatabaseMigrator.migrate(System.out);
            } catch (SQLException e) {
                System.err.println("Database migration failed: " + e.getMessage());
                System.err.println("Check that MySQL is running and that DB_URL, DB_USER and DB_PASSWORD");
                System.err.println("in backend/.env are correct. The API will still start, but data");
                System.err.println("endpoints will answer 503 until the database is reachable.");
            }
        }

        try {
            AuthService.purgeExpiredSessions();
        } catch (SQLException ignored) {
            // The database may not be reachable yet; the server reports that on /api/health.
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Haven Hospital API stopped.")));
        HospitalServer.start();

        // Keep the process alive for the executor threads and for Ctrl+C handling.
        new CountDownLatch(1).await();
    }
}
