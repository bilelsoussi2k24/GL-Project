package tool;

import dao.SessionDAO;
import dao.UserDAO;
import model.User;
import security.PasswordHasher;

/**
 * Sets a staff password from the command line. Useful when an administrator
 * loses access; it never prints the password.
 *
 * Usage: java -cp "out;lib/*" tool.SetPassword <email> <new-password>
 */
public final class SetPassword {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java -cp \"out;lib/*\" tool.SetPassword <email> <new-password>");
            System.exit(1);
        }
        if (!PasswordHasher.isStrongEnough(args[1])) {
            System.err.println("Use at least 8 characters, including a letter and a number.");
            System.exit(1);
        }

        UserDAO users = new UserDAO();
        User user = users.findByEmail(args[0]);
        if (user == null) {
            System.err.println("No account found for " + args[0]);
            System.exit(1);
        }

        users.updatePassword(user.getId(), PasswordHasher.hash(args[1]));
        new SessionDAO().deleteForUser(user.getId(), null);
        System.out.println("Password updated for " + user.getEmail() + " (" + user.getRole() + ").");
    }
}
