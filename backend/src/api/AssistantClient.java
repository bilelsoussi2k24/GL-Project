package api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import config.AppConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-side gateway to the Python AI service. Keeping the call here means the
 * Gemini key never reaches the browser and the browser never talks cross-origin.
 */
public final class AssistantClient {

    // Uvicorn serves HTTP/1.1 only. The JDK client defaults to HTTP/2 and would send an
    // h2c upgrade request, which the Python service rejects, so pin HTTP/1.1.
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private AssistantClient() {
    }

    public static Map<String, Object> ask(String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("message", message);

        HttpRequest request = HttpRequest.newBuilder(URI.create(AppConfig.aiServiceUrl()))
            .timeout(Duration.ofSeconds(AppConfig.aiTimeoutSeconds()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
            .build();

        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 503) {
                throw new ApiException(503, "The hospital assistant is not configured yet.");
            }
            if (response.statusCode() >= 400) {
                throw new ApiException(502, "The hospital assistant is temporarily unavailable.");
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String reply = json.has("response") && json.get("response").isJsonPrimitive()
                ? json.get("response").getAsString() : null;
            if (reply == null || reply.isBlank()) {
                throw new ApiException(502, "The hospital assistant returned an empty answer.");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("response", reply);
            return result;
        } catch (HttpTimeoutException e) {
            throw new ApiException(504, "The hospital assistant took too long to answer. Try again.");
        } catch (java.net.ConnectException e) {
            throw new ApiException(503, "The hospital assistant is offline. Start the AI service and try again.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(503, "The hospital assistant request was interrupted.");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(502, "The hospital assistant is temporarily unavailable.");
        }
    }
}
