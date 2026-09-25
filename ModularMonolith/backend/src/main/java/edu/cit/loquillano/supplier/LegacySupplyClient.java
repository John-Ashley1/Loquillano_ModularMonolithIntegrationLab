package edu.cit.loquillano.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The one place that actually talks to LegacySupply with resilience baked
 * in: retries failed calls with backoff (Part D), refreshes the session
 * automatically on an auth error, and never sends a reorder with a fresh
 * X-Request-Id on retry — the same id is reused across all attempts (and
 * across restarts, since it's persisted on the SupplierOrder row) so a
 * retried call can never create a duplicate purchase order.
 */
@Component
class LegacySupplyClient {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyClient.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MILLIS = 300;

    private final LegacySupplyHttp http;
    private final LegacySupplySessionManager sessionManager;

    LegacySupplyClient(
            @Value("${app.supplier.legacysupply.base-url}") String baseUrl,
            @Value("${app.supplier.legacysupply.client-id}") String clientId,
            @Value("${app.supplier.legacysupply.api-key}") String apiKey) {
        this.http = new LegacySupplyHttp(baseUrl);
        this.sessionManager = new LegacySupplySessionManager(http, clientId, apiKey);
    }

    /**
     * Places (or re-places, on resend) a purchase order. requestId and
     * buyerRef must be the same across every attempt for the same
     * reorder - the caller (SupplierGatewayImpl / the resend job) is
     * responsible for that, since idempotency only works if the id
     * doesn't change between tries.
     */
    PurchaseOrderAckXml createPurchaseOrder(String supplierSku, int cases, String buyerRef, String requestId) {
        PurchaseOrderRequestXml request = new PurchaseOrderRequestXml(supplierSku, cases, buyerRef);
        return withRetry(() -> http.createPurchaseOrder(request, sessionManager.getToken(), requestId));
    }

    PurchaseOrderAckXml getPurchaseOrder(String poNumber) {
        return withRetry(() -> http.getPurchaseOrder(poNumber, sessionManager.getToken()));
    }

    private <T> T withRetry(java.util.function.Supplier<T> call) {
        LegacySupplyException lastError = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (LegacySupplyException e) {
                lastError = e;

                boolean isAuthError = e.errorCode != null && e.errorCode.startsWith("E-AUTH");
                if (isAuthError) {
                    log.warn("LegacySupply session rejected ({}), refreshing and retrying", e.errorCode);
                    sessionManager.invalidate();
                    sessionManager.refreshToken();
                    continue; // retry immediately with the fresh session, doesn't count against backoff
                }

                if (!e.retryable || attempt == MAX_ATTEMPTS) {
                    throw e;
                }

                long backoff = BASE_BACKOFF_MILLIS * (1L << (attempt - 1)); // 300ms, 600ms, ...
                log.warn("LegacySupply call failed (attempt {}/{}): {} - retrying in {}ms",
                        attempt, MAX_ATTEMPTS, e.getMessage(), backoff);
                sleep(backoff);
            }
        }

        throw lastError;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}