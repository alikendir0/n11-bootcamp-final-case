---
phase: 05-cart-order-skeleton
plan: 03
subsystem: order-service
tags: [spring-boot, jpa, amqp, saga, testcontainers, rest, flyway, springdoc, idempotency]

# Dependency graph
requires:
  - phase: 05-01
    provides: common-outbox library (OrderOutboxPoller extends AbstractOutboxPoller, D-09)
  - phase: 05-02
    provides: cart-service GET /cart surface for CartClient sync read

provides:
  - order-service Spring Boot module (port 8085, schema orders)
  - POST /orders with Idempotency-Key dedup (D-05 Stripe-pattern)
  - GET /orders, GET /orders/{id}, POST /orders/{id}/cancel (ORD-01..ORD-06)
  - 4 saga consumers (stock-reserved, stock-reserve-failed, payment-completed, payment-failed)
  - Order state machine (PENDING → STOCK_RESERVED → CONFIRMED; failure tails → CANCELLED)
  - Outbox publisher for order.created + order.confirmed + order.cancelled via AbstractOutboxPoller
  - 5 Testcontainers integration tests covering POST /orders flow, saga consumer idempotency,
    order listing/detail, order cancel, and PriceDrift → HTTP 409 RFC-7807 mapping

affects:
  - 05-04 (payment-service skeleton / E2E test): order-service publishes order.created outbox
    events targeting orders.tx; inventory-service OrderCreatedConsumer picks them up; payment-service
    can consume the resulting stock.reserved chain; saga E2E test can boot order-service + Postgres
    + Rabbit and exercise the full happy-path
  - 05-05 (gateway routes + docker-compose): order-service routes at /api/v1/orders/**
    (StripPrefix=2); Springdoc aggregator entry at /api/v1/orders/v3/api-docs

# Tech tracking
tech-stack:
  added:
    - "order-service Spring Boot module (new)"
    - "Stripe-pattern Idempotency-Key header on POST /orders (D-05)"
    - "PriceDriftException → RFC-7807 409 with custom updatedItems[] property (D-01)"
    - "OrderExceptionHandler @ControllerAdvice @Order(HIGHEST_PRECEDENCE) — wins over common-error"
  patterns:
    - "Sync REST calls (CartClient, ProductClient, IdentityClient) happen BEFORE @Transactional opens (Pitfall 1)"
    - "Two-service split: OrderService (sync REST + idempotency check) + OrderTransactionalService (@Transactional persist)"
    - "OrderSagaService: 4 @Transactional methods each starting with processedEvents.existsById check (ARCH-07)"
    - "4 consumers follow D-10 invariant — Message param, no Channel, NOT @Transactional"
    - "Pitfall 9 race: payment.completed accepted from PENDING or STOCK_RESERVED (fast mock payment)"

key-files:
  created:
    - order-service/build.gradle.kts
    - order-service/src/main/java/com/n11/order/OrderServiceApplication.java
    - order-service/src/main/java/com/n11/order/order/OrderStatus.java
    - order-service/src/main/java/com/n11/order/order/Order.java
    - order-service/src/main/java/com/n11/order/order/OrderItem.java + OrderItemId.java
    - order-service/src/main/java/com/n11/order/order/OrderShippingAddress.java
    - order-service/src/main/java/com/n11/order/order/OrderRepository.java
    - order-service/src/main/java/com/n11/order/idempotency/OrderIdempotencyKey.java + OrderIdempotencyKeyId.java
    - order-service/src/main/java/com/n11/order/idempotency/OrderIdempotencyKeyRepository.java
    - order-service/src/main/java/com/n11/order/clients/CartClient.java + CartView.java
    - order-service/src/main/java/com/n11/order/clients/ProductClient.java + ProductSnapshot.java
    - order-service/src/main/java/com/n11/order/clients/IdentityClient.java + AddressSnapshot.java
    - order-service/src/main/java/com/n11/order/messaging/OrderRabbitConfig.java
    - order-service/src/main/java/com/n11/order/messaging/StockReservedConsumer.java + StockReserveFailedConsumer.java
    - order-service/src/main/java/com/n11/order/messaging/PaymentCompletedConsumer.java + PaymentFailedConsumer.java
    - order-service/src/main/java/com/n11/order/messaging/OrderSagaService.java
    - order-service/src/main/java/com/n11/order/order/OrderService.java
    - order-service/src/main/java/com/n11/order/order/OrderTransactionalService.java
    - order-service/src/main/java/com/n11/order/order/OrderQueryService.java
    - order-service/src/main/java/com/n11/order/order/OrderCancellationService.java
    - order-service/src/main/java/com/n11/order/order/OrderController.java
    - order-service/src/main/java/com/n11/order/order/OrderExceptionHandler.java
    - order-service/src/main/java/com/n11/order/outbox/OrderOutboxPoller.java + OrderOutboxRepository.java
    - order-service/src/main/resources/db/migration/V1__init_processed_events.sql
    - order-service/src/main/resources/db/migration/V2__init_orders.sql
    - config-server/src/main/resources/config/order-service.yml
    - 5 test classes (OrderCreationFlowTest, SagaConsumerIdempotencyTest,
      OrderControllerPriceDriftMvcTest, OrderListingAndDetailTest, OrderCancelTest)
  modified:
    - settings.gradle.kts (added "order-service")

key-decisions:
  - "Idempotency-Key count check in SagaConsumerIdempotencyTest changed from processedEventsRepository.count() == 1 to per-eventId filter: tests share DB context in SpringBootTest; total count grows across methods in the class (Rule 1 auto-fix)"
  - "OrderExceptionHandler at @Order(HIGHEST_PRECEDENCE) to beat common-error ProblemDetailControllerAdvice's generic Exception handler"
  - "awaitility kept at 4.2.0 (plan specified 4.3.1 which doesn't exist in Maven Central — same fix as Plan 05-02)"

# Metrics
duration: ~14 min
completed: 2026-04-30
---

# Phase 5 Plan 03: order-service Spring Boot Module Summary

**order-service module: full REST surface (4 endpoints) + Stripe-pattern Idempotency-Key + price drift detection + 4 saga consumers + order state machine + AbstractOutboxPoller + 5 integration tests all green**

## Performance

- **Duration:** ~14 min
- **Started:** 2026-04-30T08:18:30Z
- **Completed:** 2026-04-30T08:32:30Z
- **Tasks:** 3 of 3
- **Files modified:** 52 (51 created, 1 modified)

## Accomplishments

- order-service Spring Boot module scaffolded: `@EntityScan("com.n11")` + `@EnableScheduling` per 05-01 patterns; Flyway V1 (processed_events) + V2 (5 tables: orders, order_items, order_shipping_addresses, order_idempotency_keys, outbox); config-server overlay (port 8085, orders_user, search_path=orders,public)
- D-05 Stripe-pattern Idempotency-Key: repeat `(idempotency_key, user_id)` → same orderId with HTTP 200; first call → 202 ACCEPTED; cross-user collision → 400 Bad Request
- D-01 price drift detection: strict equality check against product-service BEFORE `@Transactional` opens; throws `PriceDriftException` which maps to HTTP 409 RFC-7807 with custom `updatedItems[]` property
- 4 saga consumers following D-10 invariant (Message param, no Channel, NOT @Transactional on listener method): StockReservedConsumer, StockReserveFailedConsumer, PaymentCompletedConsumer, PaymentFailedConsumer
- Order state machine (D-08): PENDING → STOCK_RESERVED → CONFIRMED; failure tails STOCK_FAILED and PAYMENT_FAILED → CANCELLED; USER_CANCELLED direct from PENDING/STOCK_RESERVED
- OrderSagaService: 4 @Transactional methods each with processedEvents idempotency check INSIDE the transaction (ARCH-07); publishes order.confirmed and order.cancelled outbox rows
- OrderTransactionalService: single @Transactional boundary — order + items + address + idempotency_key + outbox(order.created) in one tx; Pitfall 2 DataIntegrityViolationException race handled
- OrderCancellationService: CD-09 — cancel PENDING/STOCK_RESERVED → CANCELLED + outbox(order.cancelled); 409 for non-cancellable states; 404 for foreign user
- OrderOutboxPoller extends AbstractOutboxPoller (3-arg ctor only); OrderOutboxRepository schema-qualifies `FROM orders.outbox` (D-09)
- 5 integration tests all green (Testcontainers Postgres + @MockBean for external clients)

## 4 Saga Consumer State Transitions

| Consumer | Source States | Target State | Outbox Event |
|----------|--------------|-------------|--------------|
| StockReservedConsumer | PENDING | STOCK_RESERVED | none |
| StockReserveFailedConsumer | PENDING | CANCELLED | order.cancelled (OUT_OF_STOCK) |
| PaymentCompletedConsumer | PENDING, STOCK_RESERVED | CONFIRMED | order.confirmed |
| PaymentFailedConsumer | PENDING, STOCK_RESERVED | CANCELLED | order.cancelled (PAYMENT_DECLINED) |

Note: PaymentCompletedConsumer accepts PENDING as a source state (Pitfall 9 race — fast mock payment may fire before stock.reserved arrives at order-service).

## Idempotency-Key Flow Summary

1. `POST /orders` with `Idempotency-Key: <UUID>` header (required — 400 if absent)
2. Check `order_idempotency_keys(idempotency_key, user_id)` — if found, return existing orderId with HTTP 200
3. Check for cross-user collision (`findFirstByIdempotencyKey`) — 400 if another user owns the same key
4. Sync REST calls (cart, product, address) + price-drift check — BEFORE `@Transactional`
5. Single `@Transactional`: persist order + items + address + `order_idempotency_keys` row + outbox(order.created)
6. DataIntegrityViolationException on PK insert → concurrent winner → re-fetch and replay (HTTP 200)

## Task Commits

1. **Task 1: order-service module scaffold** - `21313ec` (feat)
2. **Task 2: Order create flow + Idempotency-Key + saga consumers + cancel** - `f847684` (feat)
3. **Task 3: Integration tests — 5 test classes all green** - `981bc4f` (feat)

## Files Created

- 52 files total (51 created, 1 modified)
- 30 main source files: application class, 4 domain entities + 5 repositories, 4 DTOs, 3 sync clients, 8 messaging files (4 consumers + saga service + processed event entity + repo + rabbit config), 4 service beans, 1 controller, 1 exception handler, 2 outbox files
- 2 Flyway migrations (V1 processed_events + V2 orders schema with 5 tables)
- 3 resource files (application.yml + 2 test configs) + 1 config-server overlay
- 5 test classes

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] processedEventsRepository.count() assertion changed to per-eventId filter**
- **Found during:** Task 3 (SagaConsumerIdempotencyTest)
- **Issue:** `assertThat(processedEventsRepository.count()).isEqualTo(1)` failed (count was 3) because all test methods in `SagaConsumerIdempotencyTest` share the same Spring context and the same Testcontainers DB; processed events from all earlier test methods accumulate in the table.
- **Fix:** Changed to `assertThat(processedEventsRepository.findAll().stream().filter(e -> e.getEventId().equals(eventId)).count()).isEqualTo(1)` — proves idempotency for the specific eventId without being sensitive to other tests.
- **Files modified:** `order-service/src/test/java/com/n11/order/messaging/SagaConsumerIdempotencyTest.java`
- **Commit:** `981bc4f` (applied before commit)

**2. [Rule 1 - Bug] awaitility kept at 4.2.0**
- **Found during:** Task 1 (reviewing 05-02 SUMMARY)
- **Issue:** Plan specified `awaitility:4.3.1` but only 4.2.0 and 4.2.1 exist in Maven Central. Plan 05-02 already documented this deviation.
- **Fix:** Used 4.2.0 in build.gradle.kts (matching inventory-service and cart-service).
- **Files modified:** `order-service/build.gradle.kts`
- **Commit:** `21313ec` (applied before commit)

---

**Total deviations:** 2 auto-fixed (test isolation issue, version typo in plan)
**Impact on plan:** None — both are minor fixes that don't affect functional correctness.

## Note for Plan 05-04: payment-service skeleton + saga E2E test

- order-service publishes `order.created` outbox events to `orders.tx` exchange with routing key `order.created`
- inventory-service's existing `OrderCreatedConsumer` (Plan 04-02) picks this up via `inventory.q.order-created` queue
- inventory-service reserves stock and publishes `stock.reserved` to `inventory.tx`
- payment-service skeleton should consume `payment.q.stock-reserved` bound to `inventory.tx`/`stock.reserved`
- payment-service should publish `payment.completed` to `payments.tx`
- order-service's `order.q.payment-completed` consumer (PaymentCompletedConsumer) will transition order to CONFIRMED
- order-service publishes `order.confirmed` to `orders.tx`; cart-service's `cart.q.order-confirmed` consumer (Plan 05-02) will clear the cart
- For the saga E2E test: boot Postgres + RabbitMQ (Testcontainers) + all 4 service Spring contexts; publish a synthetic `order.created` envelope OR call `OrderTransactionalService.persistOrder()` directly to seed the outbox row; wait with Awaitility for the order to reach CONFIRMED status

## Note for Plan 05-05: gateway routes + docker-compose

- order-service routes: `/api/v1/orders/**` (StripPrefix=2, per api-contracts.md §2 and Plan 04-03 StripPrefix=2 fix)
- Springdoc aggregator entry: `/api/v1/orders/v3/api-docs`
- docker-compose service: `order-service`, port 8085, depends on postgres + rabbitmq + eureka-server + config-server
- Jib pre-build required before docker-compose up: `./gradlew :order-service:jibDockerBuild`

## Known Stubs

None. All 4 endpoints are fully wired to persistence. Sync REST clients are real HTTP clients (URLs configurable via `app.clients.{cart,product,identity}.base-url`; defaulting to service-name:port for Docker network).

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: missing_auth_enforcement | order-service/OrderController.java | X-User-Id is read from header but no validation that the gateway populated it (gateway strips auth and injects header per Phase 3 D-15 — reliance is correct by design, but direct calls bypassing the gateway succeed with any UUID) |

This is by design (Phase 3 D-15 pattern). Documented for awareness only.

## Self-Check: PASSED

All 3 task commits (21313ec, f847684, 981bc4f) verified in git log. Key files verified present.
