package edu.cit.loquillano.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.loquillano.config.AppInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The only class that speaks HTTP/JSON to Tiangge. Every call carries the
 * three required headers (X-Client-Id, Authorization, X-Client-Instance),
 * has a short timeout, and is retried with a small backoff on timeouts,
 * 429 and 5xx. Permanent 4xx errors are thrown immediately.
 *
 * All the calls here are safe to repeat (PUTs replace, decisions /
 * resolutions / confirmations are idempotent on Tiangge's side).
 */
@Component
class TianggeClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final AppInstance instance;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    TianggeClient(@Value("${app.channel.tiangge.base-url}") String baseUrl,
                  @Value("${app.channel.tiangge.client-id}") String clientId,
                  @Value("${app.channel.tiangge.api-key}") String apiKey,
                  AppInstance instance) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.instance = instance;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    // --- wire-level records (package-private) ---------------------------

    record Listing(String sellerSku, String title, String supplierSku) {
    }

    record StockLevel(String sellerSku, int available) {
    }

    record Line(String sellerSku, int qty) {
    }

    record FeedEvent(long seq, String eventId, String type, String orderId,
                     Instant placedAt, Instant decisionDeadline,
                     Instant cancelledAt, Instant confirmDeadline,
                     List<Line> lines) {
    }

    record FeedPage(List<FeedEvent> events, long nextCursor) {
    }

    // --- endpoints -------------------------------------------------------

    void heartbeat(String appName, Instant startedAt, long uptimeSeconds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appName", appName);
        body.put("startedAt", startedAt.toString());
        body.put("uptimeSeconds", uptimeSeconds);
        call("POST", "/instances/heartbeat", body, 2);
    }

    void putListings(List<Listing> listings) {
        List<Map<String, Object>> body = new ArrayList<>();
        for (Listing l : listings) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sellerSku", l.sellerSku());
            m.put("title", l.title());
            m.put("supplierSku", l.supplierSku());
            body.add(m);
        }
        call("PUT", "/listings", body, 3);
    }

    void putStock(List<StockLevel> levels) {
        List<Map<String, Object>> body = new ArrayList<>();
        for (StockLevel s : levels) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sellerSku", s.sellerSku());
            m.put("available", Math.max(0, s.available()));
            body.add(m);
        }
        call("PUT", "/stock", body, 2);
    }

    FeedPage getFeed(long after, int limit) {
        JsonNode root = call("GET", "/feed?after=" + after + "&limit=" + limit, null, 2);
        List<FeedEvent> events = new ArrayList<>();
        for (JsonNode n : root.path("events")) {
            List<Line> lines = new ArrayList<>();
            for (JsonNode l : n.path("lines")) {
                lines.add(new Line(l.path("sellerSku").asText(), l.path("qty").asInt()));
            }
            events.add(new FeedEvent(
                    n.path("seq").asLong(),
                    n.path("eventId").asText(),
                    n.path("type").asText(),
                    n.path("orderId").asText(),
                    instant(n, "placedAt"), instant(n, "decisionDeadline"),
                    instant(n, "cancelledAt"), instant(n, "confirmDeadline"),
                    lines));
        }
        return new FeedPage(events, root.path("nextCursor").asLong(after));
    }

    void postDecision(String orderId, String decision, String shopOrderId, int attempts) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision);
        body.put("shopOrderId", shopOrderId);
        call("POST", "/orders/" + orderId + "/decision", body, attempts);
    }

    void postResolution(String orderId, String status, int attempts) {
        call("POST", "/orders/" + orderId + "/resolution", Map.of("status", status), attempts);
    }

    void postCancellation(String orderId, boolean restocked, int attempts) {
        call("POST", "/orders/" + orderId + "/cancellation", Map.of("restocked", restocked), attempts);
    }

    // --- plumbing --------------------------------------------------------

    private JsonNode call(String method, String path, Object body, int attempts) {
        TianggeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return callOnce(method, path, body);
            } catch (TianggeException e) {
                last = e;
                if (!e.isRetryable() || attempt == attempts) {
                    throw e;
                }
                sleep(250L * (1L << (attempt - 1)));
            }
        }
        throw last; // unreachable: attempts >= 1
    }

    private JsonNode callOnce(String method, String path, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .header("X-Client-Id", clientId)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-Client-Instance", instance.getId());

            if (body != null) {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String text = response.body();

            if (status >= 200 && status < 300) {
                return (text == null || text.isBlank()) ? json.createObjectNode() : json.readTree(text);
            }

            String code = null;
            String message = "HTTP " + status;
            try {
                JsonNode err = json.readTree(text);
                code = err.path("error").asText(null);
                message = err.path("message").asText(message);
            } catch (Exception ignored) {
                // body was not JSON; keep the plain HTTP status
            }
            boolean retryable = status == 429 || status >= 500;
            throw new TianggeException(message, status, code, retryable);

        } catch (IOException e) {
            // connect failures, read timeouts and unparsable bodies: all worth a retry
            throw new TianggeException("I/O error calling Tiangge: " + e.getMessage(), 0, null, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TianggeException("Interrupted calling Tiangge", 0, null, false);
        }
    }

    private static Instant instant(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            return null;
        }
        try {
            return Instant.parse(node.get(field).asText());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
