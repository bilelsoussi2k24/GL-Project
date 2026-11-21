package tool;

import config.DatabaseConnection;
import config.DatabaseMigrator;

import java.sql.Connection;
import java.util.List;

/**
 * Applies the database schema and reports accounts that can no longer sign in
 * because they still hold the placeholder hashes from the original SQL dump.
 *
 * Usage: java -cp "out;lib/*" tool.DbSetup
 */
public final class DbSetup {

    public static void main(String[] args) throws Exception {
        DatabaseMigrator.migrate(System.out);
        try (Connection con = DatabaseConnection.getConnection()) {
            List<String> legacy = DatabaseMigrator.legacyAccounts(con);
            if (!legacy.isEmpty()) {
                System.out.println();
                System.out.println("These accounts still hold the old placeholder password hashes");
                System.out.println("and cannot sign in until a password is set:");
                for (String email : legacy) System.out.println("  - " + email);
                System.out.println("Set one with: java -cp \"out;lib/*\" tool.SetPassword <email> <new-password>");
            }
        }
        System.out.println("Database setup finished.");
    }
}
