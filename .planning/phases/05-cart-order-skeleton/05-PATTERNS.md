# Phase 5: Cart & Order Skeleton — Pattern Map

**Mapped:** 2026-04-30
**Files analyzed:** ~58 new + 7 modified across 4 new modules + 4 modified modules
**Analogs found:** 56 / 58 (2 net-new shapes — Idempotency-Key dedup + ArchUnit gate — have no in-repo precedent; lifted from RESEARCH.md)

---

## File Classification

### NEW MODULE: `common-outbox/` (Gradle library — D-09)

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `common-outbox/build.gradle.kts` | gradle-config | n/a | `common-events/build.gradle.kts` | role-match (sister library module) |
| `common-outbox/src/main/java/com/n11/outbox/OutboxEvent.java` | entity | repository-read | `inventory-service/.../outbox/OutboxEvent.java` | exact (verbatim move per CD-05) |
| `common-outbox/src/main/java/com/n11/outbox/OutboxRepository.java` | repository (base) | DB | `inventory-service/.../outbox/OutboxRepository.java` | role-match (extracted base; native query stays per-service) |
| `common-outbox/src/main/java/com/n11/outbox/AbstractOutboxPoller.java` | publisher (scheduled) | DB → AMQP | `inventory-service/.../outbox/OutboxPoller.java` + `identity-service/.../outbox/OutboxPoller.java` | exact (lift + abstract) |
| `common-outbox/src/main/java/com/n11/outbox/OutboxMessagePostProcessor.java` | message post-processor | AMQP outbound | `common-logging/.../CorrelationIdMessagePostProcessor.java` | role-match (different field set: messageId from envelope) |
| `common-outbox/src/test/java/com/n11/outbox/OutboxMessagePostProcessorTest.java` | test (unit) | n/a | none in repo (new shape) | no analog — fresh unit test |

### NEW MODULE: `cart-service/` (Spring Boot module)

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `cart-service/build.gradle.kts` | gradle-config | n/a | `inventory-service/build.gradle.kts` | exact (clone — strip pgvector, add `:common-outbox`) |
| `cart-service/src/main/java/com/n11/cart/CartServiceApplication.java` | bootstrap | n/a | `inventory-service/.../InventoryServiceApplication.java` | exact (port 8084 swap) |
| `cart-service/src/main/java/com/n11/cart/cart/Cart.java` | entity | repository | `identity-service/.../address/Address.java` | role-match (simple PK entity) |
| `cart-service/src/main/java/com/n11/cart/cart/CartItem.java` | entity (composite PK) | repository | `inventory-service/.../reservation/StockReservation.java` + research Pattern 2 (`@IdClass`) | role-match (StockReservation has UNIQUE (order_id, product_id); CartItem has PK (user_id, product_id) — composite-PK shape via `@IdClass`) |
| `cart-service/src/main/java/com/n11/cart/cart/CartItemId.java` | id-class record | n/a | none in repo | no analog (RESEARCH.md Pattern 2 only) |
| `cart-service/src/main/java/com/n11/cart/cart/CartRepository.java` | repository | DB | `identity-service/.../address/AddressRepository.java` | role-match (simple JpaRepository) |
| `cart-service/src/main/java/com/n11/cart/cart/CartItemRepository.java` | repository (UPSERT) | DB | `inventory-service/.../outbox/OutboxRepository.java` (native @Query precedent) | role-match (native @Modifying @Query for `INSERT ON CONFLICT`) |
| `cart-service/src/main/java/com/n11/cart/cart/CartService.java` | service | orchestration | `product-service/.../product/ProductService.java` + `identity-service/.../address/AddressService.java` | exact (per-method `@Transactional`, mapper helpers, `ResponseStatusException` for 4xx) |
| `cart-service/src/main/java/com/n11/cart/cart/CartController.java` | controller | request-response | `identity-service/.../address/AddressController.java` | exact (X-User-Id resolver, `@RestController @RequestMapping("/cart")`) — note research excerpt shows the canonical shape verbatim |
| `cart-service/src/main/java/com/n11/cart/cart/dto/AddCartItemRequest.java` | dto (record) | n/a | `identity-service/.../address/dto/CreateAddressRequest.java` | role-match (record + bean validation) |
| `cart-service/src/main/java/com/n11/cart/cart/dto/UpdateCartItemRequest.java` | dto | n/a | same | role-match |
| `cart-service/src/main/java/com/n11/cart/cart/dto/CartView.java` + `CartLineView.java` | dto | response | `identity-service/.../address/dto/AddressResponse.java` | role-match |
| `cart-service/src/main/java/com/n11/cart/product/ProductClient.java` | http client | sync REST out | none in repo (first sync REST-out client) | partial — cross-cutting `RestClient` from `common-logging.RestClientConfig` (correlation-id interceptor already wired) |
| `cart-service/src/main/java/com/n11/cart/messaging/OrderConfirmedConsumer.java` | consumer | AMQP → DB | `inventory-service/.../messaging/OrderCreatedConsumer.java` | exact (envelope deserialize → delegate to @Transactional service) |
| `cart-service/src/main/java/com/n11/cart/messaging/CartSagaService.java` | service (transactional) | DB writes | `inventory-service/.../messaging/InventoryOrderService.java` | exact (idempotency check inside @Transactional; clear cart_items WHERE user_id = ?) |
| `cart-service/src/main/java/com/n11/cart/messaging/CartRabbitConfig.java` | config (Declarables) | broker topology | `inventory-service/.../messaging/InventoryRabbitConfig.java` | exact (one queue + DLQ + binding to `orders.tx`/`order.confirmed`) |
| `cart-service/src/main/java/com/n11/cart/messaging/ProcessedEvent.java` + `ProcessedEventRepository.java` | entity + repo | DB | `inventory-service/.../messaging/ProcessedEvent.java` (+repo) | exact (verbatim copy) |
| `cart-service/src/main/java/com/n11/cart/health/SampleHealthController.java` | health | n/a | `inventory-service/.../health/SampleHealthController.java` | exact |
| `cart-service/src/main/resources/application.yml` | config (bootstrap) | n/a | `inventory-service/src/main/resources/application.yml` | exact |
| `cart-service/src/main/resources/db/migration/V1__init_processed_events.sql` | migration | DDL | `service-template/src/main/resources/db/migration/V1__init_processed_events.sql` | exact (verbatim) |
| `cart-service/src/main/resources/db/migration/V2__init_cart.sql` | migration | DDL | `inventory-service/.../V2__init_inventory.sql` (combines domain + outbox in one migration) | role-match (cart has carts + cart_items only — no outbox table since cart-service is consumer-only per D-07) |
| `cart-service/src/test/resources/application.yml` + `application-test.yml` | test-config | n/a | `inventory-service/src/test/resources/application.yml` (+ `-test.yml`) | exact (`SET search_path=cart`, Flyway placeholders, `optional:configserver:`) |
| `cart-service/src/test/java/com/n11/cart/cart/CartItemRepositoryUpsertTest.java` | test (Testcontainers Postgres slice) | DB | `product-service/.../product/ProductRepositoryTest.java` | exact (`@DataJpaTest` + `@ServiceConnection` + pgvector image) |
| `cart-service/src/test/java/com/n11/cart/messaging/OrderConfirmedConsumerIdempotencyTest.java` | test (integration) | direct consumer call | `inventory-service/.../messaging/OrderCreatedConsumerIntegrationTest.java` | exact (direct invocation pattern; Awaitility + Testcontainers Postgres only — RabbitMQ container NOT required for slice) |

### NEW MODULE: `order-service/` (Spring Boot module — saga publisher + 4 consumers)

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `order-service/build.gradle.kts` | gradle-config | n/a | `inventory-service/build.gradle.kts` | exact |
| `order-service/src/main/java/com/n11/order/OrderServiceApplication.java` | bootstrap | n/a | `inventory-service/.../InventoryServiceApplication.java` | exact (`@EnableScheduling` for poller; port 8085) |
| `order-service/src/main/java/com/n11/order/order/Order.java` | entity (`@Version`) | repository | `inventory-service/.../stock/Stock.java` | exact (`@Version` for optimistic lock; saga state-machine driven) |
| `order-service/src/main/java/com/n11/order/order/OrderItem.java` | entity (composite PK) | repository | `inventory-service/.../reservation/StockReservation.java` | role-match (composite PK on (order_id, product_id)) |
| `order-service/src/main/java/com/n11/order/order/OrderShippingAddress.java` | entity | repository | `identity-service/.../address/Address.java` | exact (mirror Address fields per D-04; FK to orders.id) |
| `order-service/src/main/java/com/n11/order/order/OrderStatus.java` | enum | n/a | `identity-service/.../user/Role.java` (constants pattern) | partial (enum semantics from RESEARCH.md Pattern 7 — 6 states + cancellation tail) |
| `order-service/src/main/java/com/n11/order/order/OrderRepository.java` | repository | DB | `inventory-service/.../stock/StockRepository.java` | exact (JpaRepository + native @Query for listing by user) |
| `order-service/src/main/java/com/n11/order/order/OrderItemRepository.java` + `OrderShippingAddressRepository.java` | repository | DB | `identity-service/.../address/AddressRepository.java` | exact |
| `order-service/src/main/java/com/n11/order/idempotency/OrderIdempotencyKey.java` + repo | entity + repo | DB | `inventory-service/.../messaging/ProcessedEvent.java` (+ repo) | role-match (similar (UUID, UUID) PK shape; existsBy* lookup) |
| `order-service/src/main/java/com/n11/order/order/OrderService.java` | service (orchestration) | sync REST + DB writes | `product-service/.../product/ProductService.java` + `identity-service/.../user/UserService.java` | exact (sync calls outside `@Transactional`, then delegate to `OrderTransactionalService`) |
| `order-service/src/main/java/com/n11/order/order/OrderTransactionalService.java` | service (transactional persist) | DB writes (orders + items + address + outbox) | `inventory-service/.../messaging/InventoryOrderService.java` (envelope construction + outbox.save in @Transactional) | exact (RESEARCH.md "Code Examples" snippet matches this verbatim) |
| `order-service/src/main/java/com/n11/order/order/OrderController.java` | controller | request-response | `product-service/.../product/ProductController.java` + `identity-service/.../address/AddressController.java` | exact (X-User-Id + Idempotency-Key headers; @ResponseStatus(202)) |
| `order-service/src/main/java/com/n11/order/order/dto/CreateOrderRequest.java` + `OrderResponse.java` + `OrderDetailDto.java` | dto | request-response | `product-service/.../product/dto/CreateProductRequest.java` + `ProductDetailDto.java` | exact (records + bean validation) |
| `order-service/src/main/java/com/n11/order/cart/CartClient.java` + `product/ProductClient.java` + `identity/IdentityClient.java` | http client | sync REST out | none in repo | partial — uses `common-logging.RestClientConfig` builder; first multi-target client trio |
| `order-service/src/main/java/com/n11/order/messaging/StockReservedConsumer.java` | consumer | AMQP → DB transition | `inventory-service/.../messaging/OrderCreatedConsumer.java` | exact (envelope deserialize → @Transactional delegate) |
| `order-service/src/main/java/com/n11/order/messaging/StockReserveFailedConsumer.java` | consumer | AMQP → DB transition + outbox | same | exact (state → CANCELLED, cancel_reason=OUT_OF_STOCK, write outbox order.cancelled) |
| `order-service/src/main/java/com/n11/order/messaging/PaymentCompletedConsumer.java` | consumer | AMQP → DB transition + outbox | same | exact (state → PAID → CONFIRMED, write outbox order.confirmed) |
| `order-service/src/main/java/com/n11/order/messaging/PaymentFailedConsumer.java` | consumer | AMQP → DB transition + outbox | same | exact (state → CANCELLED, cancel_reason=PAYMENT_DECLINED, write outbox order.cancelled — CD-08) |
| `order-service/src/main/java/com/n11/order/messaging/OrderSagaService.java` | service (transactional) | DB writes + outbox | `inventory-service/.../messaging/InventoryOrderService.java` | exact (one `processX(eventId, envelope, payload)` method per consumer; processed_events INSIDE @Transactional) |
| `order-service/src/main/java/com/n11/order/order/OrderCancellationService.java` | service (transactional) | DB writes + outbox | `inventory-service/.../messaging/InventoryOrderService.java` (publishReserveFailed pattern) | role-match (POST /orders/{id}/cancel — CD-09; same shape as a saga consumer but trigger is HTTP) |
| `order-service/src/main/java/com/n11/order/messaging/OrderRabbitConfig.java` | config (Declarables) | broker topology | `inventory-service/.../messaging/InventoryRabbitConfig.java` | exact (4 queues + 4 DLQs + 4 bindings + idempotent re-declare of `orders.tx`/`inventory.tx`/`payments.tx`) |
| `order-service/src/main/java/com/n11/order/messaging/ProcessedEvent.java` + repo | entity + repo | DB | `inventory-service/.../messaging/ProcessedEvent.java` (+ repo) | exact (verbatim copy) |
| `order-service/src/main/java/com/n11/order/outbox/OrderOutboxRepository.java` | repository (native query) | DB | `inventory-service/.../outbox/OutboxRepository.java` | exact (override `findUnsentBatch` with `FROM orders.outbox` schema-qualified) |
| `order-service/src/main/java/com/n11/order/outbox/OrderOutboxPoller.java` | publisher (scheduled) | DB → AMQP | new `common-outbox.AbstractOutboxPoller` | exact (concrete @Component subclass, ctor-only) |
| `order-service/src/main/resources/db/migration/V1__init_processed_events.sql` | migration | DDL | `service-template/src/main/resources/db/migration/V1__init_processed_events.sql` | exact |
| `order-service/src/main/resources/db/migration/V2__init_orders.sql` | migration | DDL | `inventory-service/.../V2__init_inventory.sql` (combined domain + outbox shape) | exact (orders + order_items + order_shipping_addresses + order_idempotency_keys + outbox in V2) |
| `order-service/src/test/java/com/n11/order/order/OrderCreationServiceTest.java` | test (Testcontainers Postgres) | DB | `product-service/.../product/ProductRepositoryTest.java` + `inventory-service/.../OrderCreatedConsumerIntegrationTest.java` | exact |
| `order-service/src/test/java/com/n11/order/messaging/SagaConsumerIntegrationTest.java` | test (integration; direct invoke) | DB | `inventory-service/.../OrderCreatedConsumerIntegrationTest.java` | exact (covers all 4 consumers via direct `consumer.handleX(amqpMsg)`) |

### NEW MODULE: `payment-service/` (minimal skeleton — D-06)

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `payment-service/build.gradle.kts` | gradle-config | n/a | `inventory-service/build.gradle.kts` | exact (no Springdoc since no REST in v1; KEEP it for forward-compat with Phase 6) |
| `payment-service/src/main/java/com/n11/payment/PaymentServiceApplication.java` | bootstrap | n/a | `inventory-service/.../InventoryServiceApplication.java` | exact (port 8086, `@EnableScheduling`) |
| `payment-service/src/main/java/com/n11/payment/payment/Payment.java` | entity | repository | `inventory-service/.../reservation/StockReservation.java` | role-match (id, order_id, amount, currency, status, iyzico_payment_id NULL, created_at) |
| `payment-service/src/main/java/com/n11/payment/payment/PaymentRepository.java` | repository | DB | `inventory-service/.../reservation/StockReservationRepository.java` | exact |
| `payment-service/src/main/java/com/n11/payment/messaging/StockReservedConsumer.java` | consumer | AMQP → DB + outbox | `inventory-service/.../messaging/OrderCreatedConsumer.java` | exact (envelope → @Transactional → write payments + outbox payment.completed) |
| `payment-service/src/main/java/com/n11/payment/messaging/PaymentSagaService.java` | service (transactional) | DB writes + outbox + mock delay | `inventory-service/.../messaging/InventoryOrderService.java` | exact (added: `Thread.sleep(mockPaymentDelayMs)` BEFORE persistence) |
| `payment-service/src/main/java/com/n11/payment/messaging/PaymentRabbitConfig.java` | config (Declarables) | broker topology | `inventory-service/.../messaging/InventoryRabbitConfig.java` | exact (1 queue + DLQ + binding to `inventory.tx`/`stock.reserved`; idempotent re-declare of `inventory.tx` + `payments.tx`) |
| `payment-service/src/main/java/com/n11/payment/messaging/ProcessedEvent.java` + repo | entity + repo | DB | `inventory-service/.../messaging/ProcessedEvent.java` (+ repo) | exact |
| `payment-service/src/main/java/com/n11/payment/outbox/PaymentOutboxRepository.java` | repository | DB | `inventory-service/.../outbox/OutboxRepository.java` | exact (`FROM payment.outbox`) |
| `payment-service/src/main/java/com/n11/payment/outbox/PaymentOutboxPoller.java` | publisher | DB → AMQP | new `common-outbox.AbstractOutboxPoller` | exact (ctor-only subclass) |
| `payment-service/src/main/resources/db/migration/V1__init_processed_events.sql` | migration | DDL | `service-template/.../V1__init_processed_events.sql` | exact |
| `payment-service/src/main/resources/db/migration/V2__init_payment.sql` | migration | DDL | `inventory-service/.../V2__init_inventory.sql` | exact (payments + outbox in one V2) |
| `payment-service/src/test/java/com/n11/payment/messaging/StockReservedConsumerTest.java` | test | direct consumer call | `inventory-service/.../OrderCreatedConsumerIntegrationTest.java` | exact (mock.payment.delay-ms=0 in test profile) |

### MODIFIED FILES (existing modules)

| File | Role | Modification Type | Closest Analog (for the change) | Match Quality |
|------|------|-------------------|---------------------------------|---------------|
| `inventory-service/.../outbox/OutboxPoller.java` | publisher | refactor — extends `AbstractOutboxPoller` | new `AbstractOutboxPoller` | exact (D-09 migration; class shrinks to ctor-only) |
| `inventory-service/.../outbox/OutboxEvent.java` | entity | DELETE — moves to `common-outbox` | n/a | exact (CD-05 verbatim move) |
| `inventory-service/.../outbox/OutboxRepository.java` | repository | refactor — extends `com.n11.outbox.OutboxRepository`; keeps native query | new `OutboxRepository` (base) | exact |
| `inventory-service/build.gradle.kts` | gradle-config | add `implementation(project(":common-outbox"))` | `inventory-service/build.gradle.kts` (existing common-* lines) | exact |
| `inventory-service/.../messaging/PaymentFailedConsumer.java` | consumer (NEW in modified service) | AMQP → DB stock release | `inventory-service/.../messaging/OrderCreatedConsumer.java` | exact (CD-08 — bound to `inventory.q.payment-failed`; calls StockService.releaseStock) |
| `inventory-service/.../messaging/OrderCancelledConsumer.java` | consumer (NEW in modified service) | AMQP → DB stock release | same | exact (CD-09 — bound to `inventory.q.order-cancelled`) |
| `inventory-service/.../messaging/InventoryRabbitConfig.java` | config (Declarables) | ADD 2 queues + DLQs + bindings | self (existing pattern) | exact (mirror existing 1-queue pattern × 2) |
| `inventory-service/.../messaging/InventoryOrderService.java` | service (transactional) | ADD `releaseStockForOrder(orderId, envelope)` method | self | exact (uses existing reservation table; sets reservation.status=RELEASED) |
| `inventory-service/.../stock/Stock.java` | entity | (none — verify `release(qty)` method exists; or add to mirror `reserve(qty)`) | self | exact |
| `identity-service/.../outbox/OutboxPoller.java` | publisher | refactor — extends `AbstractOutboxPoller` | new `AbstractOutboxPoller` | exact (D-09 migration) |
| `identity-service/.../outbox/OutboxEvent.java` | entity | DELETE — moves to `common-outbox` | n/a | exact |
| `identity-service/.../outbox/OutboxRepository.java` | repository | refactor — extends `com.n11.outbox.OutboxRepository` | new `OutboxRepository` (base) | exact |
| `identity-service/build.gradle.kts` | gradle-config | add `implementation(project(":common-outbox"))` | self | exact |
| `config-server/src/main/resources/config/cart-service.yml` | config (per-service) | NEW | `config-server/.../config/inventory-service.yml` | exact (clone — port 8084, schema cart, user cart_user, ${CART_DB_PASSWORD}) |
| `config-server/src/main/resources/config/order-service.yml` | config | NEW | same | exact (port 8085, schema orders, user orders_user, ${ORDERS_DB_PASSWORD}) — RESEARCH.md "Per-service `<svc>-service.yml`" excerpt is verbatim |
| `config-server/src/main/resources/config/payment-service.yml` | config | NEW | same | exact (port 8086, schema payment, plus `mock.payment.delay-ms: 100` key) |
| `config-server/src/main/resources/config/api-gateway.yml` | config (gateway routes + Springdoc) | additive merge | self (existing routes block) | exact (add 2 routes + 2 Springdoc URLs — RESEARCH.md "Gateway routing additions" excerpt is verbatim) |
| `docker-compose.yml` | compose | additive merge — 3 new services + api-gateway depends_on | `docker-compose.yml` (existing product-service + inventory-service blocks) | exact (RESEARCH.md "docker-compose additions" excerpt) |
| `settings.gradle.kts` | gradle-settings | add `"common-outbox", "cart-service", "order-service", "payment-service"` | self (existing module list) | exact |
| `infra-tests/build.gradle.kts` | gradle-config | add archunit + awaitility test deps | self | partial (additive — RESEARCH.md installation snippet is verbatim) |
| `infra-tests/src/test/java/com/n11/infra/arch/AmqpAckModeArchTest.java` | test (architecture gate) | NEW | none in repo | no analog (RESEARCH.md Pattern 4 verbatim — D-10) |
| `infra-tests/src/test/java/com/n11/infra/saga/SagaHappyPathE2ETest.java` | test (Testcontainers Postgres + Rabbit E2E) | NEW | `infra-tests/.../CrossSchemaDenyTest.java` (Testcontainers boundary pattern) + `inventory-service/.../OrderCreatedConsumerIntegrationTest.java` (Awaitility async assertion) | role-match (mixes both — single-app boot + dual containers per CD-06) |

---

## Pattern Assignments

### `cart-service/.../cart/CartController.java` (controller, request-response)

**Analog:** `identity-service/src/main/java/com/n11/identity/address/AddressController.java`

**Imports + class shape** (lines 1-33):
```java
package com.n11.identity.address;

import com.n11.identity.address.dto.AddressResponse;
import com.n11.identity.address.dto.CreateAddressRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Address book per AUTH-08. Both endpoints rely on the gateway-injected
 * {@code X-User-Id} header (D-15: zero JWT decoding in identity-service).
 */
@RestController
@RequestMapping("/addresses")
public class AddressController {

    private static final String HEADER_USER_ID = "X-User-Id";

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }
```

**X-User-Id resolver pattern** (lines 49-59) — copy verbatim into `CartController` and `OrderController`:
```java
private static UUID resolveUserId(HttpServletRequest request) {
    String header = request.getHeader(HEADER_USER_ID);
    if (header == null || header.isBlank()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Kimlik doğrulaması gerekli");
    }
    try {
        return UUID.fromString(header);
    } catch (IllegalArgumentException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz kullanıcı kimliği");
    }
}
```

**Method shape — `@PostMapping` + `@Valid @RequestBody` + `@ResponseStatus`** (lines 41-47):
```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public AddressResponse create(HttpServletRequest request,
                               @Valid @RequestBody CreateAddressRequest body) {
    UUID userId = resolveUserId(request);
    return addressService.create(userId, body);
}
```

**For `CartController` specifically — see RESEARCH.md "Cart-service controller" code snippet (line 1042-1093) for the exact 4-method shape (`GET /cart`, `POST /cart/items`, `PATCH /cart/items/{productId}`, `DELETE /cart/items/{productId}`).**

---

### `order-service/.../order/OrderController.java` (controller, request-response + Idempotency-Key)

**Analogs:**
- `product-service/src/main/java/com/n11/product/product/ProductController.java` (Springdoc `@Operation` annotations + admin-gated handlers)
- `identity-service/.../address/AddressController.java` (X-User-Id resolver)

**Idempotency-Key handler shape** (no in-repo analog — from RESEARCH.md Pattern 3):
```java
@PostMapping("/orders")
public ResponseEntity<OrderResponse> create(
        @RequestHeader("X-User-Id") UUID userId,
        @RequestHeader("Idempotency-Key") UUID idempotencyKey,
        @Valid @RequestBody CreateOrderRequest body) {

    OrderCreationOutcome outcome = orderService.createOrder(userId, idempotencyKey, body);
    return ResponseEntity
        .status(outcome.isReplay() ? HttpStatus.OK : HttpStatus.ACCEPTED)
        .body(outcome.toResponse());
}
```

**Springdoc `@Operation` annotation pattern** from `ProductController.java` (lines 38-49):
```java
@Operation(summary = "Paginated product listing",
           description = "Page is 0-indexed (page 0 = first page). Default size = 20. " +
                         "Query params: q (text search, ILIKE on lower(name_tr)), categoryId. " +
                         "Sort examples: sort=priceGross,asc | sort=createdAt,desc | sort=nameTr,asc")
@GetMapping
public Page<ProductSummaryDto> list(
        @ParameterObject @PageableDefault(size = 20, sort = "created_at",
                direction = Sort.Direction.DESC) Pageable pageable,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) UUID categoryId) {
    return productService.search(q, categoryId, pageable);
}
```

**`GET /orders` listing — apply same `@PageableDefault(sort = "created_at", direction = DESC)` pattern.**

---

### `cart-service/.../cart/CartItemRepository.java` (repository, native UPSERT)

**Analog:** `inventory-service/src/main/java/com/n11/inventory/outbox/OutboxRepository.java` (native @Query precedent)

**Native @Query pattern** (lines 14-21 of OutboxRepository.java):
```java
@Query(
        value = "SELECT * FROM inventory.outbox WHERE sent_at IS NULL ORDER BY occurred_at LIMIT :batchSize FOR UPDATE SKIP LOCKED",
        nativeQuery = true
)
List<OutboxEvent> findUnsentBatch(@Param("batchSize") int batchSize);
```

**For UPSERT — combine the schema-qualified native query precedent with RESEARCH.md Pattern 2's `INSERT ... ON CONFLICT DO UPDATE` (research lines 567-589):**
```java
@Modifying
@Query(value = """
    INSERT INTO cart.cart_items
        (user_id, product_id, qty, unit_price_snapshot, name_snapshot, image_url_snapshot, added_at, updated_at)
    VALUES
        (:userId, :productId, :qty, :unitPriceSnapshot, :nameSnapshot, :imageUrlSnapshot, now(), now())
    ON CONFLICT (user_id, product_id) DO UPDATE SET
        qty                  = cart_items.qty + EXCLUDED.qty,
        unit_price_snapshot  = EXCLUDED.unit_price_snapshot,
        name_snapshot        = EXCLUDED.name_snapshot,
        image_url_snapshot   = EXCLUDED.image_url_snapshot,
        updated_at           = now()
    """,
    nativeQuery = true)
int upsert(@Param("userId") UUID userId, ...);
```

---

### `cart-service/.../messaging/OrderConfirmedConsumer.java` AND `order-service/.../messaging/{Stock,Payment}*Consumer.java` AND `payment-service/.../messaging/StockReservedConsumer.java`

**Canonical analog (the SINGLE most-important pattern in Phase 5):** `inventory-service/src/main/java/com/n11/inventory/messaging/OrderCreatedConsumer.java`

**Consumer shape** (lines 45-127) — split between AMQP-listener class (NOT @Transactional) and @Service delegate (IS @Transactional):

```java
@Component
public class OrderCreatedConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(OrderCreatedConsumer.class);

    private final InventoryOrderService inventoryOrderService;
    private final ObjectMapper objectMapper;

    public OrderCreatedConsumer(InventoryOrderService inventoryOrderService,
                                ObjectMapper objectMapper) {
        this.inventoryOrderService = inventoryOrderService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = InventoryRabbitConfig.QUEUE_INVENTORY_ORDERS)
    public void handleOrderCreated(Message amqpMessage) {
        String body = new String(amqpMessage.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        Envelope envelope;

        try {
            envelope = objectMapper.readValue(body, Envelope.class);
        } catch (Exception e) {
            LOG.error("inventory.consumer: malformed envelope, routing to DLQ. body={}", body, e);
            throw new AmqpRejectAndDontRequeueException("Malformed saga envelope", e);
        }

        UUID eventId;
        try {
            eventId = UUID.fromString(envelope.eventId());
        } catch (Exception e) {
            throw new AmqpRejectAndDontRequeueException(
                    "Invalid eventId in saga envelope: " + envelope.eventId(), e);
        }

        if (envelope.payload() == null || envelope.payload().isNull()) {
            throw new AmqpRejectAndDontRequeueException(
                    "Null payload in saga envelope for event " + eventId);
        }

        InventoryOrderService.OrderCreatedPayload payload;
        try {
            payload = objectMapper.treeToValue(envelope.payload(),
                    InventoryOrderService.OrderCreatedPayload.class);
        } catch (Exception e) {
            throw new AmqpRejectAndDontRequeueException(
                    "Cannot deserialize order.created payload for event " + eventId, e);
        }

        if (payload == null) {
            throw new AmqpRejectAndDontRequeueException(
                    "Deserialized payload is null for event " + eventId);
        }

        try {
            inventoryOrderService.processOrderCreated(eventId, envelope, payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Outbox serialization failure for event " + eventId, e);
        }
    }
}
```

**KEY INVARIANTS** (D-10 ArchUnit gate enforces these):
1. **`Message` parameter (NOT a deserialized POJO).** AUTO ack mode shape.
2. **NO `Channel` parameter.** Channel signals MANUAL ack — banned in Phase 5.
3. **NO `@Transactional` on the listener method.** AOP-proxy bypass risk on AMQP container thread.
4. **`AmqpRejectAndDontRequeueException` for unrecoverable** (bad JSON, invalid UUID, null payload).
5. **`RuntimeException` for transient** (DB hiccup) — propagates to `StatefulRetryInterceptor` → 3 attempts → DLQ.

**`@RabbitListener(queues = ...)` with NO `containerFactory` attribute** — auto-resolves to `RabbitRetryConfig.rabbitListenerContainerFactory` from `common-events` (CD-04). DO NOT redeclare a factory bean in any new service.

---

### `cart-service/.../messaging/CartSagaService.java` AND `order-service/.../messaging/OrderSagaService.java` AND `payment-service/.../messaging/PaymentSagaService.java`

**Canonical analog:** `inventory-service/src/main/java/com/n11/inventory/messaging/InventoryOrderService.java`

**`@Transactional` service delegate shape** (lines 36-149) — idempotency check INSIDE transaction, processed_events row written ALWAYS:

```java
@Service
public class InventoryOrderService {

    private final ProcessedEventRepository processedEventsRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    // … other repos …

    @Transactional
    public void processOrderCreated(UUID eventId, Envelope envelope, OrderCreatedPayload payload)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        // ---- IDEMPOTENCY CHECK (CLAUDE.md Rule #3) -------------------------
        if (processedEventsRepository.existsById(eventId)) {
            LOG.debug("inventory.service: duplicate event {}, skipping", eventId);
            return;
        }

        // ---- Domain side effects (state transition / cart clear / etc.) ----
        // … repository.save(...) …

        // ---- Optional outbox row for downstream events --------------------
        Envelope outboundEnvelope = new Envelope(
                UUID.randomUUID().toString(),
                "stock.reserved",
                1,
                Instant.now(),
                envelope.correlationId(),    // PROPAGATE correlationId — saga-wide
                envelope.eventId(),          // causationId = caller's eventId
                "inventory-service",
                objectMapper.valueToTree(successPayload)
        );

        String envelopeJson = objectMapper.writeValueAsString(outboundEnvelope);
        outboxRepository.save(new OutboxEvent(
                UUID.randomUUID(), "inventory", "stock.reserved", envelopeJson, Instant.now()
        ));

        // ALWAYS save processed_events (success OR failure path) — prevents re-processing
        processedEventsRepository.save(
                new ProcessedEvent(eventId, "OrderCreatedConsumer", envelope.eventType())
        );
    }
}
```

**Saga-event chaining rule** (saga-contracts.md §1, observable in InventoryOrderService.java line 119-120):
- Outgoing envelope's **`correlationId` = incoming envelope's `correlationId`** (saga-wide identifier flows through)
- Outgoing envelope's **`causationId` = incoming envelope's `eventId`** (causation chain)

---

### `order-service/.../order/OrderTransactionalService.java` (saga-INITIATING publisher pattern)

**Analog:** `identity-service/src/main/java/com/n11/identity/outbox/OutboxBackedUserRegistrationOutboxPublisher.java`

**Saga-root envelope construction** (lines 42-83) — `correlationId == eventId`, `causationId == null`:

```java
@Override
public void publishRegistered(User user) {
    UUID eventId = UUID.randomUUID();
    Instant occurredAt = Instant.now();

    UserRegisteredPayload payload = new UserRegisteredPayload(
            user.getId(), user.getEmail(), user.getFullName(), occurredAt);

    Envelope envelope = new Envelope(
            eventId.toString(),
            EVENT_TYPE,                 // "user.registered"
            EVENT_VERSION,              // 1
            occurredAt,                 // Instant — matches Envelope record field type
            eventId.toString(),         // correlationId == eventId for saga-initiating events
            null,                       // causationId — null for first event in chain
            PRODUCER,                   // "identity-service"
            objectMapper.valueToTree(payload)
    );

    String envelopeJson;
    try {
        envelopeJson = objectMapper.writeValueAsString(envelope);
    } catch (JsonProcessingException e) {
        throw new IllegalStateException("Failed to serialize user.registered envelope", e);
    }

    OutboxEvent row = new OutboxEvent(
            eventId, AGGREGATE, EVENT_TYPE, envelopeJson, occurredAt);
    outboxRepository.save(row);
}
```

**For `order.created` (Phase 5 saga-root):** `correlationId = orderId.toString()` (per saga-contracts.md §1 — for orders the orderId IS the correlation key). `causationId = null`. RESEARCH.md "Code Examples" lines 975-1039 show the full `persistOrder` method including pre-tx idempotency check + post-tx outbox write — copy that shape.

---

### `cart-service/.../messaging/CartRabbitConfig.java` AND `order-service/.../messaging/OrderRabbitConfig.java` AND `payment-service/.../messaging/PaymentRabbitConfig.java`

**Canonical analog:** `inventory-service/src/main/java/com/n11/inventory/messaging/InventoryRabbitConfig.java`

**Declarables pattern — exchange + queue (with DLQ args) + DLQ + binding** (lines 18-57):

```java
@Configuration
public class InventoryRabbitConfig {

    public static final String EXCHANGE_INVENTORY_TX  = "inventory.tx";
    public static final String EXCHANGE_ORDERS_TX     = "orders.tx";
    public static final String QUEUE_INVENTORY_ORDERS = "inventory.q.order-created";
    public static final String DLQ_INVENTORY_ORDERS   = "inventory.q.order-created.dlq";

    @Bean
    public TopicExchange inventoryExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_INVENTORY_TX).durable(true).build();
    }

    /**
     * Idempotent re-declaration of orders.tx. This exchange is owned by order-service
     * (Phase 5); declaring it here ensures the queue can bind before order-service starts.
     */
    @Bean
    public TopicExchange ordersExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_ORDERS_TX).durable(true).build();
    }

    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(QUEUE_INVENTORY_ORDERS)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DLQ_INVENTORY_ORDERS)
                .build();
    }

    @Bean
    public Queue orderCreatedDlq() {
        return QueueBuilder.durable(DLQ_INVENTORY_ORDERS).build();
    }

    @Bean
    public Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(ordersExchange).with("order.created");
    }
}
```

**Per-service mapping (saga-contracts.md §2):**

| New Config Class | Owns | Re-declares (idempotent) | Queues (each with DLQ) |
|------------------|------|--------------------------|------------------------|
| `CartRabbitConfig` | (none — consumer-only) | `orders.tx` | `cart.q.order-confirmed` ← `orders.tx`/`order.confirmed` |
| `OrderRabbitConfig` | `orders.tx` | `inventory.tx`, `payments.tx` | `order.q.stock-reserved`, `order.q.stock-failed`, `order.q.payment-completed`, `order.q.payment-failed` |
| `PaymentRabbitConfig` | `payments.tx` | `inventory.tx` | `payment.q.stock-reserved` ← `inventory.tx`/`stock.reserved` |
| `InventoryRabbitConfig` (modified) | `inventory.tx` | `orders.tx`, `payments.tx` | (existing `inventory.q.order-created`) + NEW `inventory.q.payment-failed`, `inventory.q.order-cancelled` |

---

### `common-outbox/.../OutboxEvent.java`

**Analog:** `inventory-service/src/main/java/com/n11/inventory/outbox/OutboxEvent.java`

**Verbatim move (CD-05 — fields identical so per-service `outbox` table data is readable as-is):**

```java
package com.n11.outbox;   // PACKAGE CHANGE — only diff vs inventory's version

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox")
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate", nullable = false)
    private String aggregate;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected OutboxEvent() { /* JPA */ }

    public OutboxEvent(UUID id, String aggregate, String eventType, String payload, Instant occurredAt) {
        this.id = id;
        this.aggregate = aggregate;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }

    public UUID getId()            { return id; }
    public String getAggregate()   { return aggregate; }
    public String getEventType()   { return eventType; }
    public String getPayload()     { return payload; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getSentAt()     { return sentAt; }

    public void markSent(Instant when) { this.sentAt = when; }
}
```

---

### `common-outbox/.../AbstractOutboxPoller.java`

**Analogs (LIFTED FROM BOTH):**
- `inventory-service/src/main/java/com/n11/inventory/outbox/OutboxPoller.java` (lines 26-60)
- `identity-service/src/main/java/com/n11/identity/outbox/OutboxPoller.java` (lines 28-62)

**Both have identical body — the lift target is the union:**

```java
@Component
public class OutboxPoller {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 100;  // CD-04

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPoller(OutboxRepository outboxRepository, RabbitTemplate rabbitTemplate) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelay = 5000)   // CD-04: every 5 seconds
    @Transactional
    public void poll() {
        List<OutboxEvent> unsent = outboxRepository.findUnsentBatch(BATCH_SIZE);
        if (unsent.isEmpty()) {
            return;
        }
        LOG.debug("outbox.poller draining {} unsent events", unsent.size());

        for (OutboxEvent event : unsent) {
            String exchange = event.getAggregate() + ".tx"; // "inventory.tx"
            rabbitTemplate.convertAndSend(
                    exchange,
                    event.getEventType(),     // routing key e.g. "stock.reserved"
                    event.getPayload()        // envelope JSON as String
            );
            event.markSent(Instant.now());
            outboxRepository.save(event);
        }
    }
}
```

**D-09 modifications when lifting:**
1. Remove `@Component` (concrete subclass adds it — Phase 5 is `OrderOutboxPoller`, `PaymentOutboxPoller`; Phase 5 modifications make `inventory.OutboxPoller` and `identity.OutboxPoller` both concrete subclasses).
2. Class becomes `public abstract class AbstractOutboxPoller` with protected ctor.
3. `convertAndSend(...)` adds a 4th argument: `outboxMessagePostProcessor()` — see RESEARCH.md lines 482-518 for the full method body. **This is the structural fix for the 999.2 message_id bug.**

**Concrete subclass shape** (e.g., `OrderOutboxPoller`):
```java
@Component
public class OrderOutboxPoller extends AbstractOutboxPoller {
    public OrderOutboxPoller(OrderOutboxRepository repo,
                             RabbitTemplate rabbitTemplate,
                             ObjectMapper objectMapper) {
        super(repo, rabbitTemplate, objectMapper);
    }
}
```

---

### `cart-service/src/test/java/com/n11/cart/cart/CartItemRepositoryUpsertTest.java`

**Analog:** `product-service/src/test/java/com/n11/product/product/ProductRepositoryTest.java`

**Testcontainers Postgres slice-test pattern** (lines 26-44):

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class ProductRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("n11")
                    .withUsername("product_user")
                    .withPassword("test-password");

    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
```

**Test config pattern** (`product-service/src/test/resources/application-test.yml`):
```yaml
spring:
  datasource:
    hikari:
      connection-init-sql: "SET search_path = product"   # ← change `product` → `cart` / `orders` / `payment` per service
  jpa:
    hibernate:
      ddl-auto: none
    properties:
      hibernate:
        default_schema: product
  flyway:
    enabled: true
    locations: classpath:db/migration
    schemas: product
    default-schema: product
    create-schemas: true
    placeholders:
      schema: product
      flyway.schema: product
  rabbitmq:
    listener:
      simple:
        auto-startup: false   # don't start consumers in unit tests
```

**Bypass-config-server pattern** (`product-service/src/test/resources/application.yml`):
```yaml
spring:
  config:
    import: "optional:configserver:"
  flyway:
    enabled: false
```

---

### `cart-service/src/test/java/com/n11/cart/messaging/OrderConfirmedConsumerIdempotencyTest.java` AND `order-service/.../SagaConsumerIntegrationTest.java`

**Analog:** `inventory-service/src/test/java/com/n11/inventory/messaging/OrderCreatedConsumerIntegrationTest.java`

**Direct consumer-invocation pattern** (lines 51-120) — bypasses AMQP delivery, focuses on the @Transactional idempotency contract:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
class OrderCreatedConsumerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("n11")
                    .withUsername("inventory_user")
                    .withPassword("test-password");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));
    // CRITICAL: 3.13-management NOT 4.0 — Plan 04-02 handshake-instability lesson

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.schemas", () -> "inventory");
        registry.add("spring.flyway.default-schema", () -> "inventory");
        registry.add("spring.flyway.create-schemas", () -> "true");
        registry.add("spring.flyway.placeholders.schema", () -> "inventory");
        registry.add("spring.flyway.placeholders.flyway.schema", () -> "inventory");
    }

    @Autowired OrderCreatedConsumer orderCreatedConsumer;

    @Test
    void publishOrderCreatedTwice_assertSingleSideEffect() throws Exception {
        // … seed stock …
        UUID eventId = UUID.randomUUID();
        String envelopeJson = buildOrderCreatedEnvelope(eventId, orderId, userId, productId, 5);
        org.springframework.amqp.core.Message amqpMsg =
                new org.springframework.amqp.core.Message(envelopeJson.getBytes());

        // First delivery — expect full processing
        orderCreatedConsumer.handleOrderCreated(amqpMsg);

        // Wait (via Awaitility) until the processed_events row is visible to the test thread
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> countProcessedEvents(eventId) == 1);

        assertThat(countProcessedEvents(eventId)).isEqualTo(1);

        // Second delivery — must be a no-op (idempotent)
        orderCreatedConsumer.handleOrderCreated(amqpMsg);
        // Stable-window assertion — wait then assert NO change
        Thread.sleep(1000);
        assertThat(countProcessedEvents(eventId)).isEqualTo(1);
    }
}
```

---

### `cart-service/src/main/resources/db/migration/V2__init_cart.sql`

**Analog:** `inventory-service/src/main/resources/db/migration/V2__init_inventory.sql`

**Combined-V2 pattern** (lines 1-34) — domain table + outbox table in one migration:

```sql
-- V2__init_inventory.sql -- inventory schema

CREATE TABLE stock (
    product_id          UUID        PRIMARY KEY,
    available_qty       INT         NOT NULL DEFAULT 0 CHECK (available_qty >= 0),
    reserved_qty        INT         NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
    low_stock_threshold INT         NOT NULL DEFAULT 5,
    version             BIGINT      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stock_reservations (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID        NOT NULL,
    product_id   UUID        NOT NULL REFERENCES stock(product_id),
    reserved_qty INT         NOT NULL CHECK (reserved_qty > 0),
    status       VARCHAR(20) NOT NULL DEFAULT 'RESERVED'
                             CHECK (status IN ('RESERVED', 'RELEASED', 'COMMITTED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (order_id, product_id)
);

CREATE TABLE outbox (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate   VARCHAR(64)  NOT NULL,
    event_type  VARCHAR(128) NOT NULL,
    payload     JSONB        NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ
);

CREATE INDEX outbox_unsent_idx ON outbox (occurred_at) WHERE sent_at IS NULL;
```

**Key DDL conventions (apply to all 3 new V2 migrations):**
- NO `CREATE SCHEMA` (init.sh owns schema creation per Plan 01-03; Flyway `create-schemas: false` in production).
- `UUID PRIMARY KEY DEFAULT gen_random_uuid()` for all simple PKs.
- Composite PK declared via `PRIMARY KEY (col_a, col_b)` at end of CREATE TABLE.
- `TIMESTAMPTZ NOT NULL DEFAULT now()` for created_at / updated_at.
- `JSONB` for envelope payloads; partial index on `(occurred_at) WHERE sent_at IS NULL` for the poller's drain query.
- Indexes for query patterns: `CREATE INDEX idx_<table>_<columns>` after the CREATE TABLE.

**Phase 5 specifics (per service):**
- `cart`: `carts (user_id PK, updated_at)` + `cart_items` with composite PK `(user_id, product_id)` (D-11). NO `outbox` (cart-service is consumer-only — D-07).
- `orders`: `orders` (with `version BIGINT NOT NULL DEFAULT 0` per Pattern 7), `order_items` (composite PK), `order_shipping_addresses` (1:1 with orders), `order_idempotency_keys` (PK on `(idempotency_key, user_id)`), `outbox`. Index on `(user_id, created_at DESC)` for ORD-03 listing.
- `payment`: `payments`, `outbox`.

---

### `config-server/src/main/resources/config/{cart,order,payment}-service.yml`

**Analog:** `config-server/src/main/resources/config/inventory-service.yml`

**Per-service config-server YAML pattern** (lines 1-66 of inventory-service.yml):

```yaml
server:
  port: 8083    # ← swap per service: 8084 / 8085 / 8086

eureka:
  client:
    registry-fetch-interval-seconds: 5
    eureka-server-connect-timeout-seconds: 5
    eureka-server-read-timeout-seconds: 8
    initial-instance-info-replication-interval-seconds: 5
  instance:
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
    prefer-ip-address: true

spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/n11
    username: inventory_user      # ← cart_user / orders_user / payment_user
    password: ${INVENTORY_DB_PASSWORD}   # ← ${CART_DB_PASSWORD} / ${ORDERS_DB_PASSWORD} / ${PAYMENT_DB_PASSWORD}
    hikari:
      maximum-pool-size: 10

  flyway:
    enabled: true
    locations: classpath:db/migration
    schemas: inventory             # ← cart / orders / payment
    default-schema: inventory      # ← cart / orders / payment
    create-schemas: false
    user: ${spring.datasource.username}
    password: ${spring.datasource.password}
    placeholders:
      schema: inventory            # ← cart / orders / payment
      flyway.schema: inventory     # ← cart / orders / payment

  jpa:
    open-in-view: false
    properties:
      hibernate:
        default_schema: inventory  # ← cart / orders / payment

  rabbitmq:
    host: rabbitmq
    port: 5672
    username: ${RABBITMQ_DEFAULT_USER}
    password: ${RABBITMQ_DEFAULT_PASS}

management:
  endpoints:
    web:
      exposure:
        include: health,info

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

**`payment-service.yml` adds at root:**
```yaml
mock:
  payment:
    delay-ms: 100   # D-06; test profile sets to 0
```

**Note:** `payment-service.yml` MAY omit `springdoc` entirely (no public REST in Phase 5 per D-06) — but keeping it forward-compat with Phase 6 costs nothing.

---

### `config-server/src/main/resources/config/api-gateway.yml` (modified — additive)

**Analog:** self (existing routes block at lines 50-77 — same StripPrefix=2 pattern as Phase 4 Plan 04-03 fix).

**Add under `spring.cloud.gateway.server.webflux.routes:`** (mirror existing inventory-service block at lines 72-77):

```yaml
            - id: cart-service
              uri: lb://CART-SERVICE
              predicates:
                - Path=/api/v1/cart/**
              filters:
                - StripPrefix=2
            - id: order-service
              uri: lb://ORDER-SERVICE
              predicates:
                - Path=/api/v1/orders/**
              filters:
                - StripPrefix=2
```

**Add under `springdoc.swagger-ui.urls:`** (mirror existing inventory-service entry at lines 140-141):

```yaml
      - name: cart-service
        url: /api/v1/cart/v3/api-docs
      - name: order-service
        url: /api/v1/orders/v3/api-docs
```

**Lessons inherited from Plan 04-03:**
- StripPrefix=2 (NOT 3) — `/api/v1/products/...` → `/products/...` (controller `@RequestMapping("/products")`); identity-service is a special case at StripPrefix=3 because its predicate has 4 segments.
- Payment-service does NOT get a Phase 5 route (no public REST per D-06). Phase 6 adds it.

---

### `docker-compose.yml` (modified — additive merge)

**Analog:** existing `product-service` (lines 247-273) and `inventory-service` (lines 280-306) blocks.

**Per-service compose entry pattern** (verbatim from product-service, lines 247-273):

```yaml
  product-service:
    image: n11/product-service:dev
    container_name: n11-product-service
    env_file:
      - .env
    environment:
      SPRING_CONFIG_IMPORT: configserver:http://config-server:8888?fail-fast=true&max-attempts=10&max-interval=1500&multiplier=1.2&initial-interval=1100
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_PROFILES_ACTIVE: docker
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
      config-server:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O- http://localhost:8082/actuator/health | grep -q '\"status\":\"UP\"'"]
      interval: 5s
      timeout: 3s
      retries: 6
      start_period: 25s
    restart: unless-stopped
    networks:
      - n11-net
```

**Phase 5 additions (per RESEARCH.md "docker-compose additions"):**
- `cart-service` adds `ports: ["8084:8084"]` (per CONTEXT.md "Existing Code Insights" — exposed for direct testing) AND `product-service: { condition: service_healthy }` (sync REST dep per D-01).
- `order-service` adds `ports: ["8085:8085"]` AND `cart-service` + `identity-service` to `depends_on`.
- `payment-service` adds `ports: ["8086:8086"]` (NO additional sync deps — broker-only).
- Existing `api-gateway` block extends `depends_on` with `cart-service` + `order-service` (NOT payment-service — D-06 no public REST).
- ALL THREE: `env_file: [.env]` already passes the per-service DB passwords (already in init.sh per Plan 01-03).

---

### `infra-tests/src/test/java/com/n11/infra/arch/AmqpAckModeArchTest.java` (NEW)

**Analog:** none in repo (first ArchUnit test).

**Pattern source:** RESEARCH.md "Pattern 4: ArchUnit Ack-Mode Gate" lines 666-737.

**Build dep (add to `infra-tests/build.gradle.kts`):**
```kotlin
testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")
```

**The rule asserts (per D-10):**
1. Every method annotated with `@RabbitListener` has a `org.springframework.amqp.core.Message` parameter.
2. NO method annotated with `@RabbitListener` has a `com.rabbitmq.client.Channel` parameter.

ArchUnit cannot inspect the bean wiring (`containerFactory` reference is a string), so the structural proxy is the listener method signature — sufficient to catch the Plan 04-03 regression class.

**Existing infra-tests pattern** (`CrossSchemaDenyTest.java` lines 41-66) shows the `@Testcontainers` + `@Container static final` shape — but ArchUnit test does NOT need a container (it's pure classpath analysis). Drop the `@Testcontainers` annotation; just `@Test` is sufficient.

---

### `infra-tests/src/test/java/com/n11/infra/saga/SagaHappyPathE2ETest.java` (NEW)

**Analogs (combined):**
- `infra-tests/src/test/java/com/n11/infra/CrossSchemaDenyTest.java` (Testcontainers boundary pattern, classpath init.sh mount)
- `inventory-service/src/test/java/com/n11/inventory/messaging/OrderCreatedConsumerIntegrationTest.java` (Awaitility async assertion + dual-container `@ServiceConnection` pattern)

**Recommended approach (per CD-06 + RESEARCH.md "Important caveat" lines 1316-1320):**
- ONE Spring Boot test boots ONLY `order-service` (not all 4 services).
- Postgres + RabbitMQ Testcontainers via `@ServiceConnection`.
- Mock the OTHER services' consumers by manually publishing the response events via `RabbitTemplate.convertAndSend(...)` from the test thread.
- Awaitility polls `orders.orders.status` until `CONFIRMED`.

**Test config skeleton (RESEARCH.md lines 1265-1313 has the full code):**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
class SagaHappyPathE2ETest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("n11");

    @Container @ServiceConnection
    static RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));
    // (3.13 NOT 4.0 — Phase 4 Plan 04-02 lesson)

    // … test seeds product + cart row, posts to controller, waits via Awaitility …

    await().atMost(Duration.ofSeconds(15))
           .pollInterval(Duration.ofMillis(200))
           .untilAsserted(() -> {
               OrderStatus status = jdbcTemplate.queryForObject(
                   "SELECT status FROM orders.orders WHERE id = ?",
                   (rs, i) -> OrderStatus.valueOf(rs.getString(1)),
                   orderId);
               assertThat(status).isEqualTo(OrderStatus.CONFIRMED);
           });
}
```

**Pitfall #6 mitigation (Awaitility + Hibernate L1 cache):** Use `JdbcTemplate` for assertions (NOT `repository.findById` — first-level cache returns stale entity). Pattern verified in `OrderCreatedConsumerIntegrationTest` lines 230-247.

---

### `cart-service/build.gradle.kts` AND `order-service/build.gradle.kts` AND `payment-service/build.gradle.kts`

**Analog:** `inventory-service/build.gradle.kts`

**Verbatim shape** (lines 1-68 of inventory-service/build.gradle.kts):

```kotlin
plugins {
    id("org.springframework.boot")
    id("com.google.cloud.tools.jib")
}

dependencies {
    // Boot core
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    // Spring Cloud — discovery + centralized config
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")

    // Springdoc per-service
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")

    // Flyway 12.5
    implementation("org.flywaydb:flyway-core:12.5.0")
    implementation("org.flywaydb:flyway-database-postgresql:12.5.0")

    // JDBC driver (version managed by Boot BOM)
    runtimeOnly("org.postgresql:postgresql")

    // Logback JSON
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:8.0")

    // Cross-cutting modules
    implementation(project(":common-error"))
    implementation(project(":common-logging"))
    implementation(project(":common-events"))
    implementation(project(":common-outbox"))   // ← NEW for Phase 5

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:rabbitmq")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.awaitility:awaitility:4.3.1")   // ← upgrade from 4.2.0
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("inventory-service")     // ← swap per service
}

jib {
    from { image = "eclipse-temurin:21-jre" }
    to { image = "n11/inventory-service:dev" }   // ← swap per service
    container {
        ports = listOf("8083")                   // ← 8084 / 8085 / 8086
        jvmFlags = listOf("-XX:+UseG1GC", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0")
    }
}
```

**Cart-service / Order-service additions:**
- `testImplementation(libs.networknt.json.schema)` — for envelope-schema drift gate (matches inventory's pattern).

**Payment-service simplifications (D-06):**
- Keep `springdoc-openapi-starter-webmvc-ui` (cheap forward-compat for Phase 6).
- All other deps identical.

---

### `cart-service/src/main/resources/application.yml` AND order-service / payment-service equivalents

**Analog:** `inventory-service/src/main/resources/application.yml` (lines 1-6):

```yaml
spring:
  application:
    name: inventory-service     # ← swap: cart-service / order-service / payment-service
  config:
    import: configserver:http://config-server:8888?fail-fast=true&max-attempts=10&max-interval=1500&multiplier=1.2&initial-interval=1100
```

**No `bootstrap.yml`** (Boot 3.x convention — lifted from service-template Phase 1).

---

## Shared Patterns

### Saga Consumer Idempotency (CLAUDE.md Rule #3 + D-10)

**Source:** `inventory-service/.../messaging/OrderCreatedConsumer.java` (consumer class) + `InventoryOrderService.java` (service delegate)
**Apply to:** EVERY new `@RabbitListener` in Phase 5 (8 consumers total: cart×1, order×4, payment×1, inventory×2)

**Key invariants (each consumer must satisfy ALL of these):**
1. Listener class has `Message amqpMessage` parameter (NOT a deserialized POJO; NOT a `Channel`).
2. Listener class is NOT `@Transactional`.
3. Service delegate IS `@Transactional`.
4. Idempotency check `processedEventsRepository.existsById(eventId)` is INSIDE the transaction.
5. `processed_events` row is written in the SAME transaction as side effects (success OR failure path).
6. `@RabbitListener(queues = …)` does NOT specify `containerFactory` — auto-resolves to `RabbitRetryConfig.rabbitListenerContainerFactory` from `common-events`.
7. `AmqpRejectAndDontRequeueException` for unrecoverable; other exceptions propagate to retry interceptor.

D-10 ArchUnit test enforces #1, #2 structurally.

---

### Outbox Publisher Envelope (saga-contracts.md §1)

**Source:** `identity-service/.../outbox/OutboxBackedUserRegistrationOutboxPublisher.java`
**Apply to:** order-service (saga-root for `order.created`), order-service consumers (chained `order.confirmed` / `order.cancelled`), inventory-service consumers (chained `stock.reserved` / `stock.released`), payment-service-skeleton (chained `payment.completed`).

**Envelope rules:**
- Saga ROOT (e.g., `order.created` from order-service): `correlationId = orderId.toString()`, `causationId = null`.
- Saga CHAIN (every other event): `correlationId = incomingEnvelope.correlationId()`, `causationId = incomingEnvelope.eventId()`.
- `occurredAt` is `Instant.now()` (NOT String).
- `eventVersion` is `1` for every Phase 5 event.
- `producer` is the service name as a String (e.g., `"order-service"`).
- `payload` is built via `objectMapper.valueToTree(payloadRecord)`.

---

### `MessageProperties.setMessageId(eventId)` Invariant (D-09 + 999.2 fix)

**Source:** new `common-outbox.OutboxMessagePostProcessor` (RESEARCH.md lines 496-518)
**Apply to:** EVERY outbox publish call across ALL 4 outbox publishers.

Currently the inventory-service and identity-service `OutboxPoller` classes do NOT set `messageId` (verified by `grep -n "setMessageId" inventory-service identity-service` — no matches). The 999.2 fix in commit `06338b1` was to the smoke runbook docs only (manual publish). D-09 makes this a structural fix via the new `OutboxMessagePostProcessor` invoked by `AbstractOutboxPoller.poll()`.

**Why this matters:** `RabbitRetryConfig.sagaRetryInterceptor.messageKeyGenerator` (lines 41-49 of `common-events/.../RabbitRetryConfig.java`) throws `AmqpException("Stateful retry requires a non-null messageId")` if the property is missing. Saga stalls silently before the listener method even runs.

---

### REST Controller Auth Pattern (X-User-Id from gateway)

**Source:** `identity-service/.../address/AddressController.java` lines 49-59
**Apply to:** `CartController`, `OrderController`. (payment-service has no REST in Phase 5.)

Already a Phase 3 D-15 lesson: services NEVER decode JWT. Gateway injects `X-User-Id` header. Missing → 401; malformed → 400.

```java
private static UUID resolveUserId(HttpServletRequest request) {
    String header = request.getHeader("X-User-Id");
    if (header == null || header.isBlank()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Kimlik doğrulaması gerekli");
    }
    try {
        return UUID.fromString(header);
    } catch (IllegalArgumentException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz kullanıcı kimliği");
    }
}
```

**Admin gate (only relevant if a Phase 5 endpoint needs ROLE_ADMIN — none do in v1; product-service shows the pattern at `ProductController.java` lines 86-91):**
```java
String roles = request.getHeader("X-User-Roles");
if (roles == null || !roles.contains("ROLE_ADMIN")) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Yetkisiz erişim");
}
```

---

### Error Handling — RFC-7807 via `common-error`

**Source:** `common-error/.../ProblemDetailControllerAdvice.java` (already shipped Phase 1)
**Apply to:** All controllers (cart, order). No code change — `ResponseStatusException` flows through automatically.

For order-service price-drift 409 (D-01) and Idempotency-Key conflict 400 (D-05), throw `ResponseStatusException` with the Turkish message; `common-error` converts to RFC-7807 JSON.

---

### Correlation-ID Propagation (`common-logging` 5-wire — api-contracts.md §5 "Phase 5 activates")

**Source:** `common-logging/.../{CorrelationIdFilter, CorrelationIdRestClientInterceptor, CorrelationIdMessagePostProcessor, RabbitListenerCorrelationAspect, RestClientConfig, RabbitTemplateConfig}.java`
**Apply to:** ALL new services. NO code change — `implementation(project(":common-logging"))` already in the canonical build.gradle.kts. The `@Around` `RabbitListenerCorrelationAspect` activates automatically in Phase 5 by virtue of new `@RabbitListener` methods existing.

**Verify wires:**
- HTTP inbound → `CorrelationIdFilter` populates MDC.
- HTTP outbound (cart-service → product-service, order-service → cart/product/identity) → `RestClientConfig` builds a `RestClient` whose interceptor chain includes `CorrelationIdRestClientInterceptor`.
- AMQP outbound → `RabbitTemplateConfig` adds `CorrelationIdMessagePostProcessor` to every `convertAndSend` (additive to D-09's new `OutboxMessagePostProcessor` — both run).
- AMQP inbound → `RabbitListenerCorrelationAspect` `@Around` advice extracts `X-Correlation-Id` header → MDC.

---

### Per-service Test Configuration

**Source:** `product-service/src/test/resources/application.yml` + `application-test.yml` (the canonical pair)
**Apply to:** Each new service's `src/test/resources/`.

**Two-file pattern:**
- `application.yml` (loaded by ALL tests) — neutralizes config-server: `spring.config.import: "optional:configserver:"` + `spring.flyway.enabled: false`.
- `application-test.yml` (activated by `@ActiveProfiles("test")`) — Testcontainers Postgres + Hikari `connection-init-sql: "SET search_path = <schema>"` + Flyway placeholders + `rabbitmq.listener.simple.auto-startup: false`.

This is the lesson from Plan 04-01 — Testcontainers' test user is `postgres` superuser, NOT `<service>_user`, so `init.sh`'s `ALTER USER <user> SET search_path` doesn't apply; the `connection-init-sql` setting gets it set per-connection.

---

## No Analog Found

| File | Role | Data Flow | Reason | Pattern Source |
|------|------|-----------|--------|----------------|
| `order-service/.../idempotency/OrderIdempotencyKey.java` + repo + dedup logic | entity + idempotent dedup | DB | First Stripe-pattern Idempotency-Key in repo | RESEARCH.md Pattern 3 (lines 602-657); brandur.org canonical schema |
| `cart-service/.../cart/CartItemId.java` (`@IdClass` record) | id-class record | n/a | First composite-PK `@IdClass` in repo (existing composite-PK tables — `user_roles`, `stock_reservations.UNIQUE` — use surrogate id or just unique constraints) | RESEARCH.md Pattern 2 lines 542-562 + Assumption A3 (planner should add a tiny spike test to confirm `@IdClass` + record + Spring Data JPA composes cleanly BEFORE building the full controller) |
| `cart-service/.../product/ProductClient.java` + similar in order-service | http client | sync REST out | First sync REST-out client in repo (identity-service has none — all ↑ flows are inbound) | `common-logging.RestClientConfig` provides the builder; planner picks shape (`RestClient` vs `WebClient` — recommend `RestClient` for sync) |
| `infra-tests/.../arch/AmqpAckModeArchTest.java` | architecture test | classpath analysis | First ArchUnit test in repo | RESEARCH.md Pattern 4 lines 666-737 + ArchUnit 1.4.2 docs |
| `payment-service/.../messaging/PaymentSagaService.java` mock-delay shape | service (transactional) | DB writes + Thread.sleep | First mock-payment service | RESEARCH.md D-06 — `Thread.sleep(mockPaymentDelayMs)` BEFORE persistence (configurable via `${mock.payment.delay-ms:100}`); test profile sets to 0 |
| `infra-tests/.../saga/SagaHappyPathE2ETest.java` | E2E test (multi-container) | DB + AMQP | First multi-container Testcontainers test | RESEARCH.md "Important caveat" lines 1316-1320 — boot ONE service (order-service) + manually publish response events from test thread; full multi-app boot is out of scope |

---

## Metadata

**Analog search scope:**
- `inventory-service/src/{main,test}` (saga consumer canonical, Testcontainers integration test)
- `identity-service/src/main` (saga-root outbox publisher canonical, X-User-Id resolver, address entity for D-04 mirror)
- `product-service/src/{main,test}` (controller/service/repo separation, Testcontainers slice test, V2 Flyway shape)
- `service-template/src,skeleton/` (clone-and-rename source, V1 processed_events migration)
- `common-events/src/main` (Envelope record, RabbitRetryConfig listener factory)
- `common-error/src/main`, `common-logging/src/main` (cross-cutting wires — no code change)
- `infra-tests/src/test`, `api-gateway/src/main`, `config-server/src/main/resources/config/`, `docker-compose.yml`, `settings.gradle.kts`, root `build.gradle.kts`

**Files scanned:** ~45 source files + 6 SQL migrations + 5 YAMLs + 3 Gradle build scripts.

**Pattern extraction date:** 2026-04-30

**Confidence:** HIGH for 56 files (direct analog with concrete excerpts); MEDIUM for the 2 net-new shapes (Idempotency-Key dedup table is from a canonical external source — Brandur's Stripe-pattern article; ArchUnit ack-mode rule shape verified against ArchUnit 1.4.2 docs).

---

## PATTERN MAPPING COMPLETE
