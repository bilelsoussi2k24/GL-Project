import security.PasswordHasher;

/**
 * Dependency-free unit checks for the password and token hashing helpers.
 *
 * Compile and run from the repository root after building the backend:
 *
 *   cd backend && javac -encoding UTF-8 -d out -cp "lib/*" $(find src -name "*.java")
 *   cd .. && javac -encoding UTF-8 -d .tmp/test-classes -cp "backend/out" tests/java/PasswordHasherTest.java
 *   java -cp ".tmp/test-classes;backend/out" PasswordHasherTest      (Windows)
 *   java -cp ".tmp/test-classes:backend/out" PasswordHasherTest      (macOS/Linux)
 */
public final class PasswordHasherTest {

    private static int failures = 0;

    public static void main(String[] args) {
        String hash = PasswordHasher.hash("Doctor12345");

        check(hash.startsWith("pbkdf2_sha256$210000$"), "hash has the expected format");
        check(hash.split("\\$").length == 4, "hash has four sections");
        check(PasswordHasher.verify("Doctor12345", hash), "correct password verifies");
        check(!PasswordHasher.verify("Doctor12346", hash), "wrong password is rejected");
        check(!PasswordHasher.verify(null, hash), "null password is rejected");
        check(!PasswordHasher.verify("Doctor12345", null), "null hash is rejected");
        check(!PasswordHasher.verify("Doctor12345", "test_hash_abc"), "legacy placeholder is rejected");
        check(!PasswordHasher.verify("Doctor12345", "pbkdf2_sha256$notanumber$c2FsdA==$aGFzaA=="),
            "malformed hash is rejected instead of throwing");

        String second = PasswordHasher.hash("Doctor12345");
        check(!hash.equals(second), "two hashes of the same password differ (random salt)");
        check(PasswordHasher.verify("Doctor12345", second), "second hash verifies too");

        check(PasswordHasher.isLegacy("test_hash_abc"), "placeholder is reported as legacy");
        check(!PasswordHasher.isLegacy(hash), "a real hash is not legacy");

        check(PasswordHasher.isStrongEnough("Doctor12345"), "letter + digit + length is strong enough");
        check(!PasswordHasher.isStrongEnough("short1"), "too short is rejected");
        check(!PasswordHasher.isStrongEnough("onlyletters"), "no digit is rejected");
        check(!PasswordHasher.isStrongEnough("12345678"), "no letter is rejected");
        check(!PasswordHasher.isStrongEnough(null), "null is rejected");

        String tokenHash = PasswordHasher.sha256Hex("token-value");
        check(tokenHash.length() == 64, "token hash is 64 hex characters");
        check(tokenHash.equals(PasswordHasher.sha256Hex("token-value")), "token hashing is deterministic");
        check(!tokenHash.equals(PasswordHasher.sha256Hex("token-value2")), "different tokens hash differently");

        System.out.println(failures == 0
            ? "PasswordHasherTest: all checks passed"
            : "PasswordHasherTest: " + failures + " check(s) failed");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            failures++;
            System.out.println("FAILED: " + label);
        }
    }
}
