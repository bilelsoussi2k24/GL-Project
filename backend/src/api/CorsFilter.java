package api;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import config.AppConfig;

import java.io.IOException;
import java.util.List;

/** Allows the configured frontend origins to call the API, including preflight requests. */
public final class CorsFilter extends Filter {

    private final List<String> allowedOrigins = AppConfig.corsOrigins();

    @Override
    public String description() {
        return "CORS allowlist for the Haven Hospital frontend";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        var headers = exchange.getResponseHeaders();
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && allowedOrigins.contains(origin)) {
            headers.set("Access-Control-Allow-Origin", origin);
            headers.set("Vary", "Origin");
            headers.set("Access-Control-Allow-Credentials", "true");
        }
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.set("Access-Control-Max-Age", "600");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }
        chain.doFilter(exchange);
    }
}
