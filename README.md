# Modular Monolith Integration Lab — Order, Inventory & Notification

A single Spring Boot application with three in-process modules —
**Order** (`edu.cit.loquillano.shop`), **Inventory**
(`edu.cit.loquillano.inventory`), and **Notification**
(`edu.cit.loquillano.notification`) — sharing one Supabase (Postgres)
database, plus a React (Vite) frontend that talks to it over REST.

This is Lab 2, extending Lab 1's Order/Inventory monolith with: multi-item
orders with all-or-nothing rollback, order cancellation with restock,
live inventory/order read endpoints, and in-process domain events feeding
a new Notification module (order confirmations/rejections + low-stock
alerts).

## Project structure

```
.
├── backend/     Spring Boot app (Java 17, Maven, mvnw wrapper included)
│   └── src/main/java/edu/cit/loquillano/
│       ├── ShopApplication.java       # @SpringBootApplication, parent package
│       ├── config/CorsConfig.java
│       ├── event/                     # Shared event contracts (Lab 2)
│       │   ├── OrderPlacedEvent.java
│       │   ├── OrderItemEvent.java
│       │   ├── OrderRejectedEvent.java
│       │   └── LowStockEvent.java
│       ├── inventory/                 # Inventory module
│       │   ├── InventoryItem.java
│       │   ├── InventoryItemDTO.java
│       │   ├── InventoryRepository.java
│       │   ├── InventoryController.java    (GET /api/inventory)
│       │   ├── InventoryService.java       (public interface)
│       │   ├── InventoryServiceImpl.java   (package-private)
│       │   ├── InsufficientStockException.java
│       │   └── ProductNotFoundException.java
│       ├── shop/                      # Order module
│       │   ├── Order.java             # now holds a List<OrderItem>
│       │   ├── OrderItem.java
│       │   ├── OrderRepository.java
│       │   ├── OrderService.java      (depends only on InventoryService + events)
│       │   ├── OrderController.java   (POST/GET /api/orders, POST .../cancel)
│       │   ├── OrderNotFoundException.java
│       │   ├── OrderAlreadyCancelledException.java
│       │   └── dto/
│       └── notification/              # Notification module (Lab 2)
│           ├── Notification.java
│           ├── NotificationDTO.java
│           ├── NotificationRepository.java
│           ├── NotificationEventListener.java  (package-private)
│           └── NotificationController.java     (GET /api/notifications)
├── frontend/    React (Vite) app — cart, inventory table, order history, activity feed
├── sql/create_tables.sql   Full schema (recreated from scratch) + seed data
└── README.md
```

## 1. Supabase setup (from scratch)

Unchanged from Lab 1:

1. Go to [supabase.com](https://supabase.com) (or [database.new](https://database.new)) and sign up / log in.
2. Click **New project**. Name it, set a strong database password (save
   it), pick a region, and create it.
3. Open **SQL Editor → New query**, paste in the full contents of
   [`sql/create_tables.sql`](sql/create_tables.sql), and run it. This
   **drops and recreates** `inventory`, `orders`, `order_items`, and
   `notifications` from scratch, then reseeds the three products — so
   re-running it always gives you a clean, current schema instead of
   hand-editing tables in the Supabase UI.
4. Click **Connect** at the top of the dashboard → choose the **Session
   pooler** (port `5432`), not the Transaction pooler (port `6543`) —
   Hibernate's prepared statements need session mode.
5. Copy the connection details into your env vars (see below). Add
   `sslmode=require` to the URL.

## 2. Environment variables (backend)

```bash
export SUPABASE_DB_URL="jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require"
export SUPABASE_DB_USERNAME="postgres.<project-ref>"
export SUPABASE_DB_PASSWORD="<your-db-password>"
export CORS_ALLOWED_ORIGIN="http://localhost:5173"        # optional, this is the default
export LOW_STOCK_THRESHOLD="5"                             # optional, this is the default
```

Never commit real values — `application.properties` only reads these via
`${...}` placeholders. For local development without exporting vars every
time, use `application-local.properties` (already `.gitignore`'d) with a
`local` Spring profile:

```bash
export SPRING_PROFILES_ACTIVE=local
```

## 3. Run the backend

```bash
cd backend
./mvnw spring-boot:run      # or .\mvnw.cmd spring-boot:run on Windows
```

API comes up on `http://localhost:8080`.

## 4. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

## New in Lab 2

### Multi-item orders with all-or-nothing rollback

`POST /api/orders` now takes:

```json
{ "items": [{ "productId": "P100", "quantity": 2 }, { "productId": "P200", "quantity": 1 }] }
```

`OrderService.placeOrder` runs a **two-pass** flow: first it validates
every line item against current stock (no writes yet), and only if
*every* item passes does it call `InventoryService.reserve()` for each
one. If any single item fails, the whole order is `REJECTED` and nothing
is deducted — no partial fulfillment. Response shape:

```json
{
  "orderId": 12,
  "status": "REJECTED",
  "reason": "P300: no such product",
  "items": [
    { "productId": "P100", "outcome": "OK" },
    { "productId": "P300", "outcome": "NOT_FOUND" }
  ],
  "inventory": [{ "productId": "P100", "name": "Wireless Mouse", "stock": 25 }]
}
```

(On a `CONFIRMED` order, each item's outcome is `RESERVED` instead.)

### Order cancellation & restock

`POST /api/orders/{orderId}/cancel` — 404 if the order doesn't exist, 409
if it's already `CANCELLED`. If the order was `CONFIRMED`, every line
item's quantity is returned to stock via `InventoryService.restock()`
(added to the interface, implementation still package-private, same rule
as `reserve()`).

### Read endpoints

- `GET /api/inventory` — current stock for every product, each row
  flagged `lowStock: true/false` based on `app.inventory.low-stock-threshold`.
- `GET /api/orders` — order history (status, reason, line items),
  newest first.
- `GET /api/notifications` — the activity feed, newest first.

### Domain events → Notification module

`OrderService` never calls the Notification module directly. Instead it
publishes `OrderPlacedEvent` / `OrderRejectedEvent` via Spring's
`ApplicationEventPublisher`, and `LowStockEvent` whenever a `reserve()`
leaves a product below the configured threshold. `NotificationEventListener`
(package-private, in the `notification` package) listens with
`@EventListener` and writes a row to the `notifications` table. It
imports **only** from `edu.cit.loquillano.event` — never `InventoryService`
or `OrderService` — and neither of those modules imports anything from
`notification`. That's the enforced boundary for this module, the same
idea as `InventoryServiceImpl` being package-private.

**On `@Async`:** the listener is left synchronous (Spring's default), not
`@Async`. Reasoning: a synchronous listener runs inside the same
request/transaction as the order, so if writing the notification failed
it would surface immediately instead of failing silently on a background
thread with no one watching. It also means the notification row reliably
exists by the time the response comes back to the frontend, which matters
for this lab's demo flow (the UI refreshes the activity feed right after
placing an order — with `@Async`, that refresh could race the listener
and occasionally show a stale feed). The trade-off is that a slow
notification write adds to the order's response latency; at this scale
(a lab, three modules, one DB) that cost is negligible, but it's the
first thing I'd revisit if Notification grew heavier work (e.g. sending
real emails).

## 5. Testing (capture Network tab evidence for all four)

1. **Multi-item order, all succeed** — cart with `P100` × 2 and `P200` × 1
   → `CONFIRMED`, both items `RESERVED`.
2. **Multi-item order, one fails** — cart with `P100` × 2 and `P300` × 1
   (0 stock) → `REJECTED`, check `GET /api/inventory` afterward to confirm
   `P100`'s stock was **not** touched.
3. **Cancel with restock** — cancel a `CONFIRMED` order, then `GET /api/inventory`
   to confirm the quantities came back.
4. **Notification feed** — after the above, `GET /api/notifications` (or
   the Activity Feed panel) should show a confirmed-order entry, a
   rejected-order entry, and (if any item dropped below the threshold) a
   distinct "Reorder needed" entry.

### Network tab evidence lab 1
- InventoryServiceImpl must be package-private --> Order module may depend only on the InventoryService interface (constructor injection)
(./confirmed.png)

- Supabase credentials must be kept out of the repo (environment variables / .gitignore'd config)
(./Rejected.png)

- Test both the confirmed and rejected paths end-to-end and capture Network tab evidence
(./SupaBase.png)

### Network tab evidence lab2
- A multi-item order where all items succeed (CONFIRMED)
(./succeedconfirmed.png)

- A multi-item order where one item fails and the whole order is REJECTED with no partial reservation
(./wholeorderRejected.png)

- A cancel with restock reflected in GET /api/inventory afterward
(./Cancel.png)

- The notification feed showing a confirmed order, a rejected order, and a low-stock alert
(./notifications.png)

## Reflection lab 1

**1. In-process vs. microservices — what's free, what would you add back?**

Calling `InventoryService` in-process means Order and Inventory share a
call stack, a transaction, and a JVM. I get atomicity for free: if
`OrderService.placeOrder` reserves stock and then fails to save the order,
both operations roll back together under one Spring `@Transactional`
boundary, because they're really just two repositories hitting the same
database connection. I also get free reliability and latency — a direct
method call can't time out, can't drop a packet, and doesn't need retries,
circuit breakers, or a service registry, because there's no network in the
middle. Deployment is simpler too: one JAR, one process to monitor, one
set of logs to correlate.

If I split Inventory into its own service reached over the network, all of
that has to be rebuilt deliberately. Atomicity becomes a distributed
transaction problem — I'd likely need a saga (reserve stock, then create
the order, with a compensating "release stock" step if order creation
fails) instead of a single database transaction. I'd need to handle
partial failure: timeouts, retries with idempotency keys so a retried
reservation doesn't double-deduct stock, and probably a circuit breaker so
a slow Inventory service doesn't cascade into Order. I'd also need
service discovery, network-level auth between services, and observability
(distributed tracing) to follow a request across two processes instead of
one stack trace.

**2. Why does package-private `InventoryServiceImpl` matter?**

Making the implementation class package-private means the Java compiler —
not just a code review convention — enforces that no code outside the
`inventory` package can even name `InventoryServiceImpl`, let alone
`new` it up, cast to it, or call a method that only exists on the
implementation. The Order module is structurally forced to depend on the
`InventoryService` interface. That's what makes it a *module* boundary
instead of just a folder: Inventory is free to change its internal
implementation, add caching, swap the persistence approach, or add
methods to `InventoryServiceImpl` without ever breaking Order, because
Order never sees any of that.

If `InventoryServiceImpl` were public, nothing would break immediately —
Spring would still wire it the same way — but the boundary would become
advisory rather than enforced. Someone under deadline pressure could
`@Autowired InventoryServiceImpl` directly, call an implementation-only
method, or even instantiate it manually and bypass Spring's transaction
management. Once one class does that, refactoring the Inventory module's
internals risks breaking Order in ways the compiler won't catch, and the
"module" is really just two packages with an interface nobody is required
to use.

**3. When would you extract Inventory into its own microservice?**

I'd extract it once Inventory needs to scale, deploy, or fail
independently from Order — for example, if inventory checks become a much
higher-traffic path (e.g., a public stock-lookup API) than order
placement, if a different team owns inventory and needs to deploy on its
own schedule, or if Inventory needs a different datastore (e.g., a fast
cache-backed store) that doesn't fit Order's transactional needs.

To do it, I'd keep the `InventoryService` interface as the contract, but
replace `InventoryServiceImpl` with an HTTP (or gRPC) client
implementation that calls the new Inventory service, translating its
responses into `InventoryItem`/exceptions so `OrderService` doesn't
change at all. I'd split the database so Inventory owns its own
`inventory` table and Order can no longer join across it directly. I'd
add resilience (timeouts, retries, a circuit breaker), replace the
implicit shared transaction with a saga or outbox pattern for the
reserve-then-order flow, and add versioned API contracts plus
authentication between the two services.


## Reflection lab 2

**1. What keeps multi-item orders atomic in-process, and what would
splitting Order/Inventory over a network require?**

In-process, `OrderService.placeOrder` calls `InventoryService.reserve()`
multiple times, but all of it — the validation pass, every reserve call,
and the final `orderRepository.save()` — runs inside one
`@Transactional` method on one database connection. If anything threw
partway through the reserve loop, Spring would roll the whole transaction
back automatically, undoing every deduction made so far, because they're
all just row updates in the same commit. I don't have to write any
rollback logic myself; the two-pass validate-then-reserve design is
mostly there to avoid needing compensating logic at all — by the time I
start reserving, I already know every item will succeed.

If Order and Inventory were split across a network, that free rollback
disappears. Each `reserve()` call becomes its own remote transaction
against a separate Inventory service/database, so there's no single
commit boundary spanning all of them. I'd need a saga: reserve items one
at a time, and if any reservation fails partway through, explicitly call
a compensating "release" (essentially `restock()`) for every item already
reserved in that order, in reverse. I'd also need idempotency keys on the
reserve/release calls so a retried request (after a timeout, say) can't
double-deduct or double-release stock, plus some way to detect and clean
up an order that got interrupted mid-saga (e.g. a background reconciler
that finds orders stuck in a "reserving" state and finishes or reverses
them).

**2. How does publishing an event change the coupling between OrderService
and Notification, and what would a separate Notification microservice need?**

Right now, `OrderService` doesn't know Notification exists — it publishes
a plain event object to Spring's `ApplicationEventPublisher` and moves on.
It would behave identically if Notification's listener were deleted
entirely (Order would just lose a side effect it never depended on). That's
much looser than a direct call: Order isn't blocked by Notification's
speed, can't fail because Notification threw, and doesn't need
Notification's dependency in its build.

If Notification became a separate microservice, that publish/subscribe
relationship needs a real message broker (e.g. RabbitMQ or Kafka) in
place of the in-process event bus, since a plain Java method call can't
cross a process boundary. I'd need delivery guarantees — at-least-once
delivery with an idempotent consumer, since a broker can redeliver a
message after a lost acknowledgment — and probably an outbox pattern on
the Order side, so that "save the order" and "publish the event" can't
diverge if the process crashes between the two (writing the event to an
outbox table in the same transaction, then a separate process relays it
to the broker). Notification would also need its own retry/dead-letter
handling for messages it can't process, since there's no shared
transaction to roll back into anymore.

**3. Which of the three modules would you extract first, and what would
change in the code?**

I'd extract **Notification** first. It's the module with the loosest
coupling already — Order and Inventory don't call it and don't import
anything from it, only the shared event classes — so extracting it
changes the least on the Order/Inventory side. It's also the lowest-risk
module to get wrong: if Notification is briefly unavailable, orders can
still be placed and inventory still updates correctly; nothing else in
the system depends on Notification succeeding.

To do it, I'd swap the in-process `ApplicationEventPublisher.publishEvent()`
calls for publishing to a message broker topic instead (same event data,
serialized as JSON), delete the `NotificationEventListener` and
`notification` package from this codebase, and stand up Notification as
its own Spring Boot app with its own database (just the `notifications`
table) subscribing to that topic. Order and Inventory wouldn't need any
other changes, since they never depended on Notification directly — only
on the event *shape*, which stays the same, just serialized instead of
passed as a Java object.
