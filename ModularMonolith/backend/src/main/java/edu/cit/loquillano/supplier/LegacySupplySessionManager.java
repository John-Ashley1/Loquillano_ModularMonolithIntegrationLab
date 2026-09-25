package edu.cit.loquillano.supplier;

/**
 * Holds the current LegacySupply session token in memory and knows how to
 * get a fresh one. LegacySupplyClient calls invalidate() whenever a call
 * comes back with a session-related error (E-AUTH-02/03/07) and retries
 * once with a freshly issued token — so nobody ever pastes a token by
 * hand, and a session expiring mid-run is self-healing.
 *
 * How long a session actually lasts isn't documented by LegacySupply; see
 * INTEGRATION.md for the measured value from probing this by hand.
 */
class LegacySupplySessionManager {

    private final LegacySupplyHttp http;
    private final String clientId;
    private final String apiKey;

    private volatile String currentToken;

    LegacySupplySessionManager(LegacySupplyHttp http, String clientId, String apiKey) {
        this.http = http;
        this.clientId = clientId;
        this.apiKey = apiKey;
    }

    synchronized String getToken() {
        if (currentToken == null) {
            currentToken = signIn();
        }
        return currentToken;
    }

    synchronized String refreshToken() {
        currentToken = signIn();
        return currentToken;
    }

    synchronized void invalidate() {
        currentToken = null;
    }

    private String signIn() {
        AuthResponseXml response = http.authenticate(new AuthRequestXml(clientId, apiKey));
        return response.sessionToken;
    }
}
