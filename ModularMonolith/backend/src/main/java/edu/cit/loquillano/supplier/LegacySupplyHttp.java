package edu.cit.loquillano.supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Raw wire-level calls to LegacySupply: builds requests, serializes/parses
 * XML, and turns non-2xx responses into LegacySupplyException. Deliberately
 * has NO retry or session-refresh logic — that orchestration lives in
 * LegacySupplyClient, one layer up, so this class stays a simple, testable
 * transport.
 */
class LegacySupplyHttp {

    // 3-second timeout per Part D's "3 seconds or less is reasonable."
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper xmlMapper;

    LegacySupplyHttp(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.xmlMapper = new XmlMapper();
    }

    AuthResponseXml authenticate(AuthRequestXml request) {
        HttpResponse<String> response = send("POST", "/auth/token", request, null, null);
        return parse(response, AuthResponseXml.class);
    }

    PurchaseOrderAckXml createPurchaseOrder(PurchaseOrderRequestXml request, String sessionToken, String requestId) {
        HttpResponse<String> response = send("POST", "/purchase-orders", request, sessionToken, requestId);
        return parse(response, PurchaseOrderAckXml.class);
    }

    PurchaseOrderAckXml getPurchaseOrder(String poNumber, String sessionToken) {
        HttpResponse<String> response = send("GET", "/purchase-orders/" + poNumber, null, sessionToken, null);
        return parse(response, PurchaseOrderAckXml.class);
    }

    private HttpResponse<String> send(String method, String path, Object body, String sessionToken, String requestId) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/xml");

            if (sessionToken != null) {
                builder.header("X-LS-Session", sessionToken);
            }
            if (requestId != null) {
                builder.header("X-Request-Id", requestId);
            }

            HttpRequest.BodyPublisher bodyPublisher;
            if (body != null) {
                String xml = xmlMapper.writeValueAsString(body);
                builder.header("Content-Type", "application/xml");
                bodyPublisher = HttpRequest.BodyPublishers.ofString(xml);
            } else {
                bodyPublisher = HttpRequest.BodyPublishers.noBody();
            }
            builder.method(method, bodyPublisher);

            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            // Covers connect failures and read timeouts alike - both retryable.
            throw new LegacySupplyException("I/O error calling LegacySupply: " + e.getMessage(), e, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LegacySupplyException("Interrupted calling LegacySupply", e, true);
        }
    }

    private <T> T parse(HttpResponse<String> response, Class<T> type) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            try {
                return xmlMapper.readValue(response.body(), type);
            } catch (Exception e) {
                throw new LegacySupplyException("Could not parse LegacySupply response: " + e.getMessage(), e, false);
            }
        }

        String errorCode = null;
        String message = "HTTP " + status;
        try {
            LSErrorXml error = xmlMapper.readValue(response.body(), LSErrorXml.class);
            errorCode = error.code;
            message = error.message;
        } catch (Exception ignored) {
            // Body wasn't a well-formed LSError - fall back to the plain HTTP status.
        }

        boolean retryable = status == 429 || status == 503 || status == 500;
        throw new LegacySupplyException(message, status, errorCode, retryable);
    }

    static String newRequestId() {
        return UUID.randomUUID().toString();
    }
}
