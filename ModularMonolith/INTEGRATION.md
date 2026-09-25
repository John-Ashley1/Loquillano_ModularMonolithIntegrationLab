# INTEGRATION.md — LegacySupply

This file is filled in from actually probing LegacySupply (Part B),
before writing any Java. Base URL: `https://legacysupply.onrender.com/api/v1`

| Our productId | Our name | LegacySupply SupplierSku | PackSize | UnitCost |
|---|---|---|---|---|
| P100 | Wireless Mouse | RUH-3650 | 12 | TODO — run `curl https://legacysupply.onrender.com/api/v1/catalog -H "X-LS-Session: $token"` and read `Catalog.Item[0].UnitCost` |
| P200 | Mechanical Keyboard | RUH-9809 | 10 | TODO — same call, `Catalog.Item[1].UnitCost` |
| P300 | USB-C Hub | RUH-8535 | 12 | TODO — same call, `Catalog.Item[2].UnitCost` |

## 0. Get your API key and check the service is up

```bash
curl https://legacysupply.onrender.com/api/v1/ping
```

API key was issued privately by the instructor for Client ID `23-5192-482`.

## 1. Get a session token

```bash
curl -i -X POST https://legacysupply.onrender.com/api/v1/auth/token \
  -H "Content-Type: application/xml" \
  -d '<AuthRequest><ClientId>23-5192-482</ClientId><ApiKey>YOUR_API_KEY</ApiKey></AuthRequest>'
```

Confirmed working — returns a `<AuthResponse>` with a `<SessionToken>`, verified manually via `Invoke-RestMethod` before wiring it into the app.

## 2. Pull your catalog

```bash
curl https://legacysupply.onrender.com/api/v1/catalog \
  -H "X-LS-Session: YOUR_SESSION_TOKEN"
```

### Product mapping table (from the catalog response)

| Our productId | Our name | LegacySupply SupplierSku | PackSize | UnitCost |
|---|---|---|---|---|
| P100 | Wireless Mouse | RUH-3650 | 12 | TODO |
| P200 | Mechanical Keyboard | RUH-9809 | 10 | TODO |
| P300 | USB-C Hub | RUH-8535 | 12 | TODO |

## 3. Place a test order

```bash
curl -i -X POST https://legacysupply.onrender.com/api/v1/purchase-orders \
  -H "X-LS-Session: YOUR_SESSION_TOKEN" \
  -H "Content-Type: application/xml" \
  -H "X-Request-Id: test-$(date +%s)" \
  -d '<PurchaseOrder><SupplierSku>RUH-9809</SupplierSku><Qty>2</Qty><BuyerRef>test-probe-1</BuyerRef></PurchaseOrder>'
```

Confirmed working end-to-end — first successful order returned `PoNumber: PO-100293`, `StatusCode: 10` (Accepted). Since then, 4 total orders have been placed and accepted (confirmed on the self-check page: "Placed at least 3 purchase orders — 4 orders on file").

```bash
curl https://legacysupply.onrender.com/api/v1/purchase-orders/PO-100293 \
  -H "X-LS-Session: YOUR_SESSION_TOKEN"
```

At least one order was tracked all the way to `StatusCode: 40` (Delivered), confirmed on the self-check page ("Tracked an order to delivered — 1 delivered orders seen").

## 4. Session lifetime

- Observed session lifetime: **TODO — see method below.** During development, sessions were re-established frequently (once per app restart, since `LegacySupplySessionManager` only signs in when `currentToken` is `null`), which doesn't isolate the server's actual expiry window. The self-check page does confirm expiry happens in real traffic: 23 total sign-ins against 3 requests that hit an expired session, all auto-recovered by the adapter's `E-AUTH-*` → `invalidate()` + `refreshToken()` retry logic.
- How to measure it properly: sign in once, note the timestamp, then call `GET /catalog` with that same unchanged token every ~1 minute (without triggering any re-auth logic) until you get `E-AUTH-03` or `E-AUTH-07`. The elapsed time is your answer. Record it here once measured.

## 5. Error codes actually observed

| Code | HTTP | What I did to cause it |
|---|---|---|
| E-AUTH-01 | 401 | Two separate real causes hit during development: (1) `app.supplier.legacysupply.client-id` was left as a literal placeholder string instead of the real student ID, so `ClientId` was invalid on every `/auth/token` call. (2) After fixing that, `LS_API_KEY` was read from an OS environment variable (`@Value("${LS_API_KEY:}")`) that the running JVM process wasn't actually inheriting from the PowerShell session it was launched from, so `ApiKey` was sent as an empty string. Fixed by moving the key into `application.properties` as `app.supplier.legacysupply.api-key` and reading it via `@Value`, removing the environment-variable dependency entirely. |
| E-AUTH-03 / E-AUTH-07 | 401 | Triggered naturally by the adapter's own retry logic during normal operation when a session token stopped being accepted mid-run; `LegacySupplyClient.withRetry` catches this, calls `sessionManager.invalidate()` + `refreshToken()`, and retries — confirmed working, 3 of 23 sign-in-backed requests hit an expired session and were all recovered (per self-check page). |
| — (idempotent replay) | 200 | Retried a purchase-order request with the same `X-Request-Id` as a prior attempt (both organically, via the resend/retry path, and via LegacySupply's own chaos testing). Confirmed via the self-check page: 0 duplicates created across 3 chaos events on orders, with 2 safe replays returning `200` instead of creating a second PO. |
| TODO | 422 | Not yet triggered deliberately. To observe `E-SKU-02`, send a `SupplierSku` not in your catalog. To observe `E-QTY-11`, send `Qty` outside 1–99. |

## 6. Qty and Uom, in my own words

Qty is the number of **whole cases (packs)** to order, not individual units — LegacySupply doesn't sell in single-unit increments per order, only in packs of `PackSize`. Uom is the unit of measure LegacySupply uses to describe that pack in their response (TODO — confirm the literal string from your own `PurchaseOrderAck`, e.g. `CS` for case, by inspecting the `Uom` field on a real response rather than assuming the manual's sample).

**Worked example:** Our P200 (Mechanical Keyboard) has PackSize 10. The auto-reorder rule's configured reorder quantity is 20 units. `cases = Math.ceil(20 / 10.0) = 2`. We send `Qty=2` to LegacySupply's SupplierSku `RUH-9809`; this matches every P200 reorder observed in `supplier_orders` (`cases: 2, units: 20`), and LegacySupply accepted all of them once credentials were fixed (`PO-100293`, StatusCode 10).

## 7. Unexpected status handling (Part E)

If LegacySupply ever returns a StatusCode outside 10/20/30/40, this
system maps it to our own `UNKNOWN` enum value (see `SupplierOrderStatus`,
via `SupplierGatewayImpl.mapStatusCode`), logs a warning, and leaves the
order as an "open" order the tracking job keeps polling — it does **not**
guess and does **not** restock on an unknown code.

No unexpected status code has been observed yet. The self-check page's
"Noticed a cancelled order" check remains unmet (0 cancelled orders seen)
— per the checker's own note, cancellation is a situation that "only
happens after your instructor changes service conditions," and there is
no cancel-capable endpoint documented in the interface manual, so this
is expected to stay unmet until that condition is introduced.