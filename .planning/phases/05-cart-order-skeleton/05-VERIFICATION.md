---
phase: 05-cart-order-skeleton
verified: 2026-04-30T14:00:00Z
status: human_needed
score: 4/5 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Confirm CartView KDV breakdown + shipping preview scope is intentionally deferred to Phase 10 frontend"
    expected: "Either (a) accept that Phase 5 SC-1 'KDV breakdown + shipping preview via REST' means frontend-computed display from CartView items[], OR (b) require cart-service to extend CartView with subtotal + kdvTotal + shippingFee fields before Phase 5 closes"
    why_human: "ROADMAP SC-1 says 'view (with line totals + KDV breakdown + shipping preview)...via REST' but CartView only returns items[] with lineTotal. The PLAN must_have for CART-02 deliberately scoped down to items[]+lineTotal (plan text: 'GET /cart returns CartView with userId, items[]...updatedAt'). Phase 10 SC-2/3 addresses KDV display as frontend-computed. Automated verification cannot determine whether the ROADMAP 'via REST' phrasing was intentionally narrowed in the PLAN or is a genuine omission."
---

# Phase 5: Cart & Order Skeleton Verification Report

**Phase Goal:** Build cart-service (per-user cart state, single source of truth for both web UI and chat assistant) and order-service (order lifecycle, saga initiator, transactional outbox), and prove the saga skeleton end-to-end with a mocked payment producer — so Phase 6 swaps in real Iyzico against a frozen contract.
**Verified:** 2026-04-30T14:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | SC-1: User can add, view (with line totals + KDV breakdown + shipping preview), update qty, remove cart items via REST; cart persists per userId | ? UNCERTAIN | CartController has 4 endpoints (GET, POST /items, PATCH /items/{id}, DELETE /items/{id}); CartView returns items[] with lineTotal; lazy-create on first add; X-User-Id resolved. BUT CartView lacks subtotal, KDV breakdown, shipping preview — see Human Verification below |
| 2 | SC-2: POST /orders writes order+outbox atomically; outbox poller publishes order.created; inventory consumes and emits stock.reserved — verified by Testcontainers + Awaitility test | ✓ VERIFIED | OrderTransactionalService @Transactional writes 5 tables + outbox in one boundary; OrderOutboxPoller extends AbstractOutboxPoller (D-09); inventory-service OrderCreatedConsumerIntegrationTest uses Awaitility to assert stock.reserved outbox row; SagaHappyPathE2ETest boots payment-service + Testcontainers Postgres+RabbitMQ and asserts payment.completed via Awaitility within 15s |
| 3 | SC-3: User can list orders sorted by date desc; view order detail with status enum; saga state machine PENDING → STOCK_RESERVED → PAID → CONFIRMED + FAILED/CANCELLED | ✓ VERIFIED | GET /orders and GET /orders/{id} on OrderController; OrderDetailDto includes id, status, cancelReason, updatedAt, items, shippingAddress; OrderStatus enum has PENDING, STOCK_RESERVED, PAID, CONFIRMED, STOCK_FAILED, PAYMENT_FAILED, CANCELLED; 4 saga consumers (StockReservedConsumer, StockReserveFailedConsumer, PaymentCompletedConsumer, PaymentFailedConsumer) each transition status atomically in OrderSagaService @Transactional |
| 4 | SC-4: Saga consumers are idempotent — processed_events inbox table per service; integration test re-delivers same eventId and asserts single side effect; correlationId flows through MDC | ✓ VERIFIED (with known limitation) | V1__init_processed_events.sql in cart/order/payment-service; OrderConfirmedConsumerIdempotencyTest delivers event twice asserting processedEventsRepository count=1 and cart_items cleared once; SagaConsumerIdempotencyTest asserts no duplicate state transition on redelivery; RabbitListenerCorrelationAspect (@Aspect @Component in com.n11.logging) is scanned by all three services via scanBasePackages="com.n11" and populates MDC on each consumer invocation. KNOWN LIMITATION: cart/order/payment-service lack logback-spring.xml JSON encoder, so correlationId is in MDC but not visible in structured log output — pre-existing observability gap per context note, not a Phase 5 regression |
| 5 | SC-5: Both services expose Springdoc Swagger UI; each has at least one critical-path integration test (Testcontainers Postgres + RabbitMQ) | ✓ VERIFIED | springdoc-openapi-starter-webmvc-ui:2.8.17 in cart-service and order-service build.gradle.kts; gateway aggregator shows 5 entries confirmed by smoke Step 11; cart-service has CartItemRepositoryUpsertTest + OrderConfirmedConsumerIdempotencyTest (Testcontainers); order-service has 5 test classes (OrderCreationFlowTest, SagaConsumerIdempotencyTest, OrderCancelTest, OrderControllerPriceDriftMvcTest, OrderListingAndDetailTest); payment-service has StockReservedConsumerIntegrationTest |

**Score:** 4/5 (SC-1 UNCERTAIN pending human decision on KDV scope; SC-2, SC-3, SC-4, SC-5 VERIFIED)

### Deferred Items

No items explicitly deferred to later phases — the correlationId JSON log gap is a known limitation
per context note (not a Phase 5 regression) and is recommended for Phase 7 or Phase 11.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `common-outbox/build.gradle.kts` | Gradle java-library module | ✓ VERIFIED | `plugins { java-library }` present |
| `common-outbox/src/main/java/com/n11/outbox/OutboxEvent.java` | JPA entity with @Table(name="outbox") | ✓ VERIFIED | @Table(name = "outbox") present |
| `common-outbox/src/main/java/com/n11/outbox/OutboxRepository.java` | Base JpaRepository<OutboxEvent, UUID> | ✓ VERIFIED | extends JpaRepository<OutboxEvent, UUID> |
| `common-outbox/src/main/java/com/n11/outbox/OutboxMessagePostProcessor.java` | @Component with props.setMessageId(eventId) | ✓ VERIFIED | @Component; props.setMessageId(eventId) present |
| `common-outbox/src/main/java/com/n11/outbox/AbstractOutboxPoller.java` | @Scheduled(fixedDelay=5000) driver with messagePostProcessor | ✓ VERIFIED | @Scheduled(fixedDelay = 5000) present; messagePostProcessor referenced 4 times |
| `infra-tests/.../AmqpAckModeArchTest.java` | D-10 ArchUnit gate (note: in com.n11.infra.arch per deviation) | ✓ VERIFIED | EXISTS at `/infra-tests/src/test/java/com/n11/infra/arch/AmqpAckModeArchTest.java`; RabbitListener.class and Channel.class present |
| `identity-service/.../OutboxPoller.java` | extends AbstractOutboxPoller (ctor-only) | ✓ VERIFIED | extends AbstractOutboxPoller |
| `inventory-service/.../OutboxPoller.java` | extends AbstractOutboxPoller (ctor-only) | ✓ VERIFIED | extends AbstractOutboxPoller |
| `identity-service/outbox/OutboxEvent.java` | DELETED (superseded) | ✓ VERIFIED | File does not exist |
| `inventory-service/outbox/OutboxEvent.java` | DELETED (superseded) | ✓ VERIFIED | File does not exist |
| `cart-service/build.gradle.kts` | Spring Boot module (note: common-outbox NOT added per plan deviation — cart is consumer-only) | ✓ VERIFIED | springdoc + common-events + common-logging + AMQP dependencies; plan text confirms common-outbox omitted intentionally (cart is consumer-only, no outbox poller) |
| `cart-service/src/main/java/.../CartItem.java` | @IdClass(CartItemId.class) composite PK | ✓ VERIFIED | @IdClass(CartItemId.class) present |
| `cart-service/src/main/java/.../CartItemRepository.java` | Native UPSERT with ON CONFLICT | ✓ VERIFIED | ON CONFLICT (user_id, product_id) DO UPDATE present |
| `cart-service/src/main/java/.../CartController.java` | 4 endpoints with @PatchMapping | ✓ VERIFIED | GET, POST /items, PATCH /items/{productId}, DELETE /items/{productId} |
| `cart-service/src/main/java/.../OrderConfirmedConsumer.java` | @RabbitListener | ✓ VERIFIED | @RabbitListener present; Message parameter, no Channel parameter (D-10) |
| `cart-service/src/main/resources/db/migration/V2__init_cart.sql` | PRIMARY KEY (user_id, product_id) | ✓ VERIFIED | PRIMARY KEY (user_id, product_id) present |
| `config-server/.../config/cart-service.yml` | port 8084, schema cart | ✓ VERIFIED | port: 8084; schema: cart present |
| `order-service/src/main/java/.../OrderStatus.java` | 7-state enum | ✓ VERIFIED | PENDING, STOCK_RESERVED, PAID, CONFIRMED, STOCK_FAILED, PAYMENT_FAILED, CANCELLED |
| `order-service/src/main/java/.../OrderController.java` | 4 endpoints with @RequestHeader("Idempotency-Key") | ✓ VERIFIED | POST /orders, GET /orders, GET /orders/{id}, POST /orders/{id}/cancel; Idempotency-Key header |
| `order-service/src/main/java/.../OrderTransactionalService.java` | @Transactional single boundary | ✓ VERIFIED | @Transactional present |
| `order-service/src/main/java/.../OrderExceptionHandler.java` | @ExceptionHandler(PriceDriftException.class) → 409 | ✓ VERIFIED | PRICE_DRIFT_TYPE URI + @ExceptionHandler + HTTP 409 + updatedItems[] property |
| `order-service/src/main/java/.../OrderRabbitConfig.java` | 4 queues (stock-reserved, stock-failed, payment-completed, payment-failed) | ✓ VERIFIED | 8 matching strings found (queue + DLQ per consumer) |
| `order-service/src/main/resources/db/migration/V2__init_orders.sql` | 5 tables including outbox | ✓ VERIFIED | File exists |
| `payment-service/build.gradle.kts` | Spring Boot module with common-outbox | ✓ VERIFIED | implementation(project(":common-outbox")) present |
| `payment-service/src/main/java/.../StockReservedConsumer.java` | @RabbitListener | ✓ VERIFIED | @RabbitListener present |
| `payment-service/src/main/java/.../PaymentSagaService.java` | payment.completed outbox write | ✓ VERIFIED | "payment.completed" present |
| `payment-service/src/main/resources/db/migration/V2__init_payment.sql` | CREATE TABLE payments | ✓ VERIFIED | CREATE TABLE payments present |
| `config-server/.../config/payment-service.yml` | port 8086, schema payment | ✓ VERIFIED | File exists |
| `inventory-service/.../PaymentFailedConsumer.java` | @RabbitListener + releaseStockForOrder | ✓ VERIFIED | @RabbitListener + releaseStockForOrder present |
| `inventory-service/.../OrderCancelledConsumer.java` | @RabbitListener + releaseStockForOrder | ✓ VERIFIED | @RabbitListener + releaseStockForOrder present |
| `infra-tests/.../SagaHappyPathE2ETest.java` | Awaitility-polled payment.completed assertion | ✓ VERIFIED | Awaitility imported; payment.completed referenced 7 times |
| `config-server/.../config/api-gateway.yml` | /api/v1/cart/** route + cart + order Springdoc entries | ✓ VERIFIED | lb://CART-SERVICE + lb://ORDER-SERVICE with StripPrefix=2; 2 Springdoc entries |
| `docker-compose.yml` | cart-service, order-service, payment-service blocks | ✓ VERIFIED | All 3 service blocks present |
| `identity-service/.../AddressController.java` | GET /addresses/{id} (mid-flight fix) | ✓ VERIFIED | @GetMapping("/{id}") with @PathVariable UUID id added |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `cart-service/CartService.java` | product-service /products/{id} | ProductClient.fetchSnapshot | ✓ WIRED | ProductClient contains /api/v1/products path; CartService calls productClient.fetchSnapshot before @Transactional |
| `cart-service/OrderConfirmedConsumer.java` | cart_items DELETE | CartSagaService.processOrderConfirmed | ✓ WIRED | Consumer delegates to CartSagaService; deleteByUserId or DELETE FROM cart_items path verified |
| `cart-service/CartRabbitConfig.java` | orders.tx exchange / order.confirmed routing key | TopicExchange + Queue + Binding | ✓ WIRED | "order.confirmed" present twice in CartRabbitConfig |
| `order-service/OrderService.java` | cart-service GET /cart | CartClient.getCart before @Transactional | ✓ WIRED | CartClient referenced; REST call at line 65 BEFORE @Transactional persist at line 88+ |
| `order-service/OrderCancellationService.java` | outbox order.cancelled | OutboxEvent write | ✓ WIRED | "order.cancelled" literal present in outboxRepository.save call |
| `payment-service/StockReservedConsumer.java` | PaymentSagaService.processStockReserved | delegate (consumer not @Transactional) | ✓ WIRED | paymentSagaService.processStockReserved pattern confirmed |
| `payment-service/PaymentSagaService.java` | outbox payment.completed | outboxRepository.save with "payment.completed" | ✓ WIRED | "payment.completed" written to outbox in PaymentTransactionalService |
| `inventory-service/PaymentFailedConsumer.java` | InventoryOrderService.releaseStockForOrder | delegate | ✓ WIRED | releaseStockForOrder call confirmed |
| `config-server/api-gateway.yml` | cart-service Eureka | lb://CART-SERVICE | ✓ WIRED | lb://CART-SERVICE present |
| `config-server/api-gateway.yml` | order-service Eureka | lb://ORDER-SERVICE | ✓ WIRED | lb://ORDER-SERVICE present |
| `identity-service/OutboxPoller.java` | common-outbox AbstractOutboxPoller | extends | ✓ WIRED | extends AbstractOutboxPoller |
| `inventory-service/OutboxPoller.java` | common-outbox AbstractOutboxPoller | extends | ✓ WIRED | extends AbstractOutboxPoller |
| `order-service/outbox/OrderOutboxPoller.java` | common-outbox AbstractOutboxPoller | extends | ✓ WIRED | extends AbstractOutboxPoller; 5 references to messagePostProcessor |
| `payment-service/outbox/PaymentOutboxPoller.java` | common-outbox AbstractOutboxPoller | extends | ✓ WIRED | extends AbstractOutboxPoller |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|-------------------|--------|
| `CartPersistenceService.loadCartView` | lines (CartLineView list) | cartItemRepository.findByUserIdOrderByAddedAt | Yes — real JPA query from cart_items table | ✓ FLOWING |
| `OrderService.createOrder` | cart (CartView) | CartClient.getCart → cart-service REST | Yes — live cart-service call | ✓ FLOWING |
| `PaymentTransactionalService.persistAndPublish` | amount | payload.totalAmount() from stock.reserved envelope | Yes — traces to order.created totalAmount propagated through inventory-service | ✓ FLOWING |
| `SagaHappyPathE2ETest` | payment.completed message | RabbitMQ Awaitility poll on payments.tx exchange | Yes — real AMQP delivery from PaymentOutboxPoller | ✓ FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED — Requires running docker-compose stack (server not available in verification context). Live smoke test with 12/12 checklist items was performed by human operator and signed off (2026-04-30). Commit db25041 contains three runtime fixes discovered during smoke execution.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| CART-01 | 05-02 | User can add product to cart | ✓ SATISFIED | POST /cart/items with UPSERT; CartController.addItem + upsertAddQty |
| CART-02 | 05-02 | Cart contents with line totals, subtotal, shipping preview, KDV breakdown | ? PARTIAL | CartView has items[] + lineTotal; lacks subtotal/KDV/shipping fields — see Human Verification |
| CART-03 | 05-02 | Update line item quantity | ✓ SATISFIED | PATCH /cart/items/{productId}; CartService.updateQty re-fetches snapshot |
| CART-04 | 05-02 | Remove line items | ✓ SATISFIED | DELETE /cart/items/{productId}; CartService.removeItem |
| CART-05 | 05-02 | Cart persists per logged-in user | ✓ SATISFIED | Lazy-create on first POST; getOrCreateCartView; X-User-Id resolution |
| CART-06 | 05-05 | cart-service Springdoc OpenAPI | ✓ SATISFIED | springdoc-openapi-starter-webmvc-ui:2.8.17; gateway aggregator entry at /api/v1/cart/v3/api-docs; smoke Step 11 confirmed |
| ORD-01 | 05-03 | POST /orders accepts addressId + paymentMethod | ✓ SATISFIED | POST /orders on OrderController; CreateOrderRequest DTO |
| ORD-02 | 05-03 | order-service publishes order.created event | ✓ SATISFIED | OrderTransactionalService writes outbox row; OrderOutboxPoller extends AbstractOutboxPoller |
| ORD-03 | 05-03 | User can view order list sorted by date desc | ✓ SATISFIED | GET /orders on OrderController; ORDER BY created_at DESC |
| ORD-04 | 05-03 | User can view order detail with status timeline | ✓ SATISFIED | GET /orders/{id}; OrderDetailDto with status, cancelReason, updatedAt, items, shippingAddress; PLAN confirms Turkish display labels are FRONTEND ONLY |
| ORD-05 | 05-03 | Order status reflects saga progress | ✓ SATISFIED | 4 consumers each transition status; OrderStatus has all 7 states |
| ORD-06 | 05-05 | order-service Springdoc OpenAPI | ✓ SATISFIED | springdoc-openapi-starter-webmvc-ui:2.8.17; gateway aggregator entry; smoke Step 11 confirmed |
| ARCH-06 | 05-03, 05-04 | SAGA choreography happy path + compensation paths | ✓ SATISFIED | stock-fail (StockReserveFailedConsumer), payment-fail (PaymentFailedConsumer), user-cancel (OrderCancellationService + OrderCancelledConsumer inventory); payment-timeout is Phase 6 scope per plan |
| ARCH-07 | 05-01..04 | Saga consumers idempotent with processed_events inbox | ✓ SATISFIED | V1__init_processed_events.sql in 3 services; processedEventsRepository.existsById checks in all saga consumer services; idempotency integration tests in all services |
| ARCH-08 | 05-01..04 | Saga events carry correlationId; flows through MDC | ✓ SATISFIED (with known limitation) | OutboxMessagePostProcessor sets correlationId on every published message; RabbitListenerCorrelationAspect (@Aspect @Component, scanned via scanBasePackages="com.n11") populates MDC on each consumer; KNOWN LIMITATION: cart/order/payment lack JSON logback-spring.xml so correlationId is in MDC but not in structured log output — pre-existing gap per context note |
| QUAL-03 | 05-01..04 | 1-2 integration tests per service critical path | ✓ SATISFIED | cart-service: 2 Testcontainers tests; order-service: 5 test classes; payment-service: StockReservedConsumerIntegrationTest; infra-tests: SagaHappyPathE2ETest |

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| `payment-service/.../PaymentTransactionalService.java` | `MOCK_PAYMENT_PREFIX = "mock-"` + `iyzicoPaymentId = MOCK_PAYMENT_PREFIX + paymentId` | Info | Intentional D-06 design stub — documented in SUMMARY.md Known Stubs. Phase 6 replaces with real Iyzico. No blocker. |

No unintentional stubs, TODO/FIXME markers, empty implementations, or hollow render paths found in Phase 5 production code.

### Human Verification Required

#### 1. CartView KDV Breakdown + Shipping Preview Scope

**Test:** Check ROADMAP Phase 5 SC-1 against current CartView REST response:
- `curl -s http://localhost:18080/api/v1/cart -H "Authorization: Bearer <JWT>"` → inspect response
- Confirm whether `subtotal`, `kdvTotal`, `shippingFee` fields are expected by Phase 5 or are deferred to frontend

**Expected (if deferred to frontend):** CartView returns `{userId, items:[{productId, nameSnapshot, imageUrlSnapshot, qty, unitPriceSnapshot, lineTotal}], updatedAt}` — KDV and shipping computed client-side in Phase 10 using product-service's `kdvRate` field via `Intl.NumberFormat`

**Expected (if gap):** CartView should include `subtotal` (sum of lineTotals), `kdvTotal`, and `shippingFee` fields — requires CartView + CartLineView + ProductSnapshot + CartPersistenceService.loadCartView changes

**Why human:** ROADMAP SC-1 says "view (with line totals + KDV breakdown + shipping preview) via REST" — explicit REST scope. PLAN must_have for CART-02 deliberately narrowed to items[]+lineTotal without these fields. Phase 10 SC-2/3 addresses KDV display as a frontend concern using `Intl.NumberFormat`. The design intent (backend cart stores priceGross; frontend computes KDV display) appears correct for a microservices split, but the ROADMAP wording creates ambiguity that automated verification cannot resolve.

### Gaps Summary

No automated BLOCKERS found. SC-1 has one UNCERTAIN item requiring human decision:

The CartView REST response (`GET /cart`) returns `{userId, items[{lineTotal}], updatedAt}` but does not include `subtotal`, `kdvBreakdown`, or `shippingPreview` fields. ROADMAP SC-1 says these should be "via REST." The Phase 5 PLAN narrowed CART-02's must_have to items+lineTotal, and Phase 10 handles KDV display client-side — but whether the ROADMAP's "via REST" phrasing was intentionally scoped down in the PLAN or is a genuine omission needs a human call.

All other SC truths (SC-2 saga E2E, SC-3 order listing/detail, SC-4 idempotency, SC-5 Springdoc) are fully verified against the codebase. All Phase 5 commits verified in git log (0284247, 9ce1591, 46b93b3, 60cde57, 66dd389, b4ddb22, 21313ec, f847684, 981bc4f, b0d1cf8, 22114b0, 3d65e1c, 096adc0, 4c45e99, b975b5d, db25041, 52ce2b4).

---

_Verified: 2026-04-30T14:00:00Z_
_Verifier: Claude (gsd-verifier)_
