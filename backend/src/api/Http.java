package api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Request parsing, validation and JSON responses. */
public final class Http {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final String BODY_ATTRIBUTE = "parsedBody";
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    private Http() {
    }

    /** Successful response. */
    public record Response(int status, Object body) {
        public static Response ok(Object body) {
            return new Response(200, body);
        }

        public static Response created(Object body) {
            return new Response(201, body);
        }

        public static Response noContent() {
            return new Response(204, null);
        }
    }

    public static JsonObject body(HttpExchange ex) {
        Object cached = ex.getAttribute(BODY_ATTRIBUTE);
        if (cached instanceof JsonObject object) return object;
        try {
            String raw = new String(ex.getRequestBody().readNBytes(MAX_BODY_BYTES + 1), StandardCharsets.UTF_8);
            if (raw.length() > MAX_BODY_BYTES) throw ApiException.badRequest("The request body is too large.");
            if (raw.isBlank()) throw ApiException.badRequest("A JSON request body is required.");
            JsonElement parsed = JsonParser.parseString(raw);
            if (!parsed.isJsonObject()) throw ApiException.badRequest("The request body must be a JSON object.");
            ex.setAttribute(BODY_ATTRIBUTE, parsed.getAsJsonObject());
            return parsed.getAsJsonObject();
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the request body.");
        } catch (RuntimeException e) {
            if (e instanceof ApiException api) throw api;
            throw ApiException.badRequest("The request body is not valid JSON.");
        }
    }

    public static String requiredString(JsonObject body, String key, String label, int maxLength) {
        String value = optionalString(body, key, label, maxLength);
        if (value == null) throw ApiException.badRequest(label + " is required.");
        return value;
    }

    public static String optionalString(JsonObject body, String key, String label, int maxLength) {
        if (!body.has(key) || body.get(key).isJsonNull()) return null;
        JsonElement element = body.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw ApiException.badRequest(label + " must be text.");
        }
        String value = element.getAsString().strip();
        if (value.isEmpty()) return null;
        if (value.length() > maxLength) {
            throw ApiException.badRequest(label + " must be at most " + maxLength + " characters.");
        }
        return value;
    }

    public static long requiredId(JsonObject body, String key, String label) {
        if (!body.has(key) || body.get(key).isJsonNull()) throw ApiException.badRequest(label + " is required.");
        try {
            long value = body.get(key).getAsLong();
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (RuntimeException e) {
            throw ApiException.badRequest(label + " must be a positive number.");
        }
    }

    public static Integer optionalInt(JsonObject body, String key, String label, int min, int max) {
        if (!body.has(key) || body.get(key).isJsonNull()) return null;
        int value;
        try {
            value = body.get(key).getAsInt();
        } catch (RuntimeException e) {
            throw ApiException.badRequest(label + " must be a whole number.");
        }
        if (value < min || value > max) {
            throw ApiException.badRequest(label + " must be between " + min + " and " + max + ".");
        }
        return value;
    }

    public static String email(JsonObject body, String key, String label, boolean required) {
        String value = required ? requiredString(body, key, label, 150) : optionalString(body, key, label, 150);
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(normalized).matches()) throw ApiException.badRequest("Enter a valid email address.");
        return normalized;
    }

    /** True for a syntactically valid email address. Placeholders such as "[EMAIL]" are not valid. */
    public static boolean isEmail(String value) {
        return value != null && EMAIL.matcher(value).matches();
    }

    public static String oneOf(String value, String label, List<String> allowed) {
        if (value == null || !allowed.contains(value)) {
            throw ApiException.badRequest(label + " must be one of: " + String.join(", ", allowed) + ".");
        }
        return value;
    }

    public static LocalDateTime dateTime(JsonObject body, String key, String label) {
        String raw = requiredString(body, key, label, 40);
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(label + " must look like 2026-11-25T10:00:00.");
        }
    }

    public static LocalDate date(JsonObject body, String key, String label) {
        String raw = requiredString(body, key, label, 20);
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(label + " must look like 1998-05-24.");
        }
    }

    public static LocalTime time(JsonObject body, String key, String label) {
        String raw = requiredString(body, key, label, 8);
        return parseTime(raw, label);
    }

    public static LocalTime parseTime(String raw, String label) {
        try {
            return LocalTime.parse(raw.length() == 5 ? raw + ":00" : raw);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(label + " must look like 08:30.");
        }
    }

    public static String pathParam(java.util.regex.Matcher matcher, int group) {
        try {
            long id = Long.parseLong(matcher.group(group));
            if (id <= 0) throw new NumberFormatException();
            return String.valueOf(id);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Invalid identifier in the URL.");
        }
    }

    public static Map<String, String> query(HttpExchange ex) {
        Map<String, String> values = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) return values;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            values.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return values;
    }

    public static String queryValue(HttpExchange ex, String key) {
        String value = query(ex).get(key);
        return value == null || value.isBlank() ? null : value.strip();
    }

    public static String bearerToken(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header == null) return null;
        String prefix = "Bearer ";
        return header.startsWith(prefix) ? header.substring(prefix.length()).strip() : null;
    }

    public static String clientKey(HttpExchange ex) {
        String forwarded = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].strip();
        return ex.getRemoteAddress() == null ? "unknown" : ex.getRemoteAddress().getAddress().getHostAddress();
    }

    public static String json(Object value) {
        return GSON.toJson(value);
    }

    public static void send(HttpExchange ex, int status, Object body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        if (status == 204 || body == null) {
            ex.sendResponseHeaders(status == 204 ? 204 : status, -1);
            return;
        }
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    public static void error(HttpExchange ex, int status, String message) throws IOException {
        send(ex, status, Map.of("error", message));
    }

    /** Turns a JSON array of integers into the database text format "1,2,3". */
    public static String workDays(JsonObject body, String key, String label) {
        if (!body.has(key) || body.get(key).isJsonNull()) return null;
        JsonElement element = body.get(key);
        if (!element.isJsonArray()) throw ApiException.badRequest(label + " must be a list of weekday numbers.");
        JsonArray array = element.getAsJsonArray();
        List<Integer> days = new ArrayList<>();
        for (JsonElement item : array) {
            int day;
            try {
                day = item.getAsInt();
            } catch (RuntimeException e) {
                throw ApiException.badRequest(label + " must contain weekday numbers from 0 (Sunday) to 6.");
            }
            if (day < 0 || day > 6) throw ApiException.badRequest(label + " must contain weekday numbers from 0 to 6.");
            if (!days.contains(day)) days.add(day);
        }
        if (days.isEmpty()) throw ApiException.badRequest("Choose at least one working day.");
        days.sort(Integer::compareTo);
        return String.join(",", days.stream().map(String::valueOf).toList());
    }

    public static List<Integer> parseWorkDays(String raw) {
        List<Integer> days = new ArrayList<>();
        if (raw == null || raw.isBlank()) return days;
        for (String part : raw.split(",")) {
            try {
                days.add(Integer.parseInt(part.strip()));
            } catch (NumberFormatException ignored) {
                // Skip malformed legacy values rather than failing the whole request.
            }
        }
        return days;
    }
}
