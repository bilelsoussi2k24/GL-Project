package config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Application configuration read from environment variables, with an optional
 * .env file as a developer convenience. Environment variables always win.
 *
 * The .env file is looked up in the working directory and in backend/ so the
 * server behaves the same whether it is started from the repository root or
 * from the backend folder.
 */
public final class AppConfig {

    private static final Map<String, String> FILE_VALUES = loadDotEnv();

    private AppConfig() {
    }

    public static String get(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) value = System.getProperty(key);
        if (value == null || value.isBlank()) value = FILE_VALUES.get(key);
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    public static int getInt(String key, int fallback) {
        String raw = get(key, null);
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Configuration value " + key + " must be a whole number, got: " + raw);
        }
    }

    public static long getLong(String key, long fallback) {
        String raw = get(key, null);
        if (raw == null) return fallback;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Configuration value " + key + " must be a whole number, got: " + raw);
        }
    }

    public static boolean getBool(String key, boolean fallback) {
        String raw = get(key, null);
        if (raw == null) return fallback;
        return switch (raw.toLowerCase()) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> throw new IllegalStateException("Configuration value " + key + " must be true or false");
        };
    }

    public static String dbUrl() {
        String url = get("DB_URL", "jdbc:mysql://localhost:3306/hospital_db");
        if (!url.contains("?")) {
            url += "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8";
        }
        return url;
    }

    public static String dbUser() {
        return get("DB_USER", "root");
    }

    public static String dbPassword() {
        return get("DB_PASSWORD", "");
    }

    public static int port() {
        int port = getInt("PORT", 8080);
        // Some hosts export PORT=0 meaning "pick one"; the API always uses a real port.
        return port < 1 || port > 65535 ? 8080 : port;
    }

    public static String bindAddress() {
        return get("BIND_ADDRESS", "127.0.0.1");
    }

    /** Comma separated list of browser origins allowed to call the API. */
    public static List<String> corsOrigins() {
        String raw = get("CORS_ORIGINS",
            "http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000");
        List<String> origins = new ArrayList<>();
        for (String origin : raw.split(",")) {
            String trimmed = origin.strip();
            if (!trimmed.isEmpty()) origins.add(trimmed);
        }
        return origins;
    }

    /** Lifetime of a staff session, in hours. */
    public static int sessionHours() {
        return Math.max(1, getInt("SESSION_HOURS", 12));
    }

    /** Base URL of the Python AI service. The API key stays in that service. */
    public static String aiServiceUrl() {
        return get("AI_SERVICE_URL", "http://127.0.0.1:8000/api/chat");
    }

    public static int aiTimeoutSeconds() {
        return Math.max(1, getInt("AI_TIMEOUT_SECONDS", 25));
    }

    public static String initialAdminEmail() {
        return get("ADMIN_EMAIL", "admin@haven.local").toLowerCase();
    }

    /** Only used when the database has no administrator yet. */
    public static String initialAdminPassword() {
        return get("ADMIN_PASSWORD", null);
    }

    public static boolean autoMigrate() {
        return getBool("AUTO_MIGRATE", true);
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> values = new LinkedHashMap<>();
        List<Path> candidates = List.of(Path.of(".env"), Path.of("backend", ".env"), Path.of("..", ".env"));
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) continue;
            try {
                for (String line : Files.readAllLines(candidate, StandardCharsets.UTF_8)) {
                    String trimmed = line.strip();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    int eq = trimmed.indexOf('=');
                    if (eq <= 0) continue;
                    String key = trimmed.substring(0, eq).strip();
                    String value = trimmed.substring(eq + 1).strip();
                    if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    values.putIfAbsent(key, value);
                }
            } catch (IOException e) {
                System.err.println("Could not read " + candidate.toAbsolutePath() + ": " + e.getMessage());
            }
        }
        return values;
    }
}
