import api.ApiException;
import api.Http;

import com.google.gson.JsonObject;

/**
 * Checks that password input is read exactly as sent.
 *
 * <p>Passwords must never be trimmed: a leading or trailing space is part of the
 * secret, and silently removing it makes sign-in depend on invisible characters.
 * Display fields keep their trimming behaviour.</p>
 *
 * <p>Run from the repository root after building the backend:</p>
 * <pre>
 * javac -encoding UTF-8 -d .tmp/test-classes -cp "backend/out;backend/lib/*" tests/java/PasswordInputTest.java
 * java -cp ".tmp/test-classes;backend/out;backend/lib/*" PasswordInputTest
 * </pre>
 */
public final class PasswordInputTest {

    private static int failures = 0;

    public static void main(String[] args) {
        JsonObject body = new JsonObject();
        body.addProperty("password", "  Doctor12345  ");
        check("  Doctor12345  ".equals(Http.password(body, "password", "Password")),
            "leading and trailing spaces are preserved");
        body.addProperty("tabbed", "\tPass12345\n");
        check("\tPass12345\n".equals(Http.password(body, "tabbed", "Password")),
            "tabs and newlines are preserved");

        JsonObject trimmed = new JsonObject();
        trimmed.addProperty("name", "  Dr. Smith  ");
        check("Dr. Smith".equals(Http.optionalString(trimmed, "name", "Name", 50)),
            "display fields still trim surrounding whitespace");

        rejects("empty string", "", "must reject an empty password");
        rejects("whitespace only", "   ", "must reject a whitespace-only password");
        rejects("missing key", null, "must reject a missing password");

        JsonObject number = new JsonObject();
        number.addProperty("password", 12345678);
        checkBadRequest(() -> Http.password(number, "password", "Password"),
            "must reject a non-string password");

        JsonObject nullValue = new JsonObject();
        nullValue.addProperty("password", (String) null);
        checkBadRequest(() -> Http.password(nullValue, "password", "Password"),
            "must reject a null password");

        JsonObject tooLong = new JsonObject();
        tooLong.addProperty("password", "A1" + "x".repeat(Http.maxPasswordLength()));
        checkBadRequest(() -> Http.password(tooLong, "password", "Password"),
            "must reject a password longer than the maximum");

        JsonObject exact = new JsonObject();
        exact.addProperty("password", "A1" + "x".repeat(Http.maxPasswordLength() - 2));
        check(Http.password(exact, "password", "Password").length() == Http.maxPasswordLength(),
            "must accept a password at the maximum length");

        System.out.println(failures == 0
            ? "PasswordInputTest: all checks passed"
            : "PasswordInputTest: " + failures + " check(s) failed");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void rejects(String label, String value, String message) {
        JsonObject body = new JsonObject();
        JsonObject target = value == null ? new JsonObject() : body;
        if (value != null) target.addProperty("password", value);
        checkBadRequest(() -> Http.password(target, "password", "Password"), message);
    }

    private static void checkBadRequest(Runnable action, String message) {
        try {
            action.run();
            failures++;
            System.out.println("FAILED: " + message + " (no error raised)");
        } catch (ApiException e) {
            if (e.getStatus() != 400) {
                failures++;
                System.out.println("FAILED: " + message + " (status " + e.getStatus() + ")");
            }
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            failures++;
            System.out.println("FAILED: " + label);
        }
    }
}
