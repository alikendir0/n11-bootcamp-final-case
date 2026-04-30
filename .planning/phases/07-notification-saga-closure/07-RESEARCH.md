# Phase 7: Notification (Saga Closure) — Research

**Researched:** 2026-04-30
**Domain:** Spring Boot 3.5.14 / Java 21 — leaf saga consumer, Spring AMQP listener, Postgres 16 schema-per-service, Testcontainers + Awaitility QUAL-04 saga integration test
**Confidence:** HIGH (all patterns directly derived from verified in-repo code; no new external libraries introduced)

---

## Summary

Phase 7 stands up `notification-service` as a fully independent Spring Boot microservice that acts as a **pure leaf saga consumer** — it consumes events, writes an audit row plus a structured log line, and emits no further saga events. This is architecturally the simplest service in the system: no REST surface beyond an optional `/notifications` endpoint, no outbox (it never publishes), no synchronous downstream calls.

The primary complexity is operational: wiring four separate queues (`notify.q.order-confirmed`, `notify.q.payment-failed`, `notify.q.order-cancelled`, `notify.q.user-registered`) with correct DLQ topology and idempotency on each, and then extending the `infra-tests` `SagaHappyPathE2ETest` to boot notification-service and assert `order.confirmed → notification logged` within the Awaitility window.

One open question that the planner must resolve before writing the QUAL-04 test: **`order.cancelled` vs `notify.q.order-cancelled`**. The saga-contracts.md queue table (§2) lists `notify.q.order-confirmed`, `notify.q.payment-failed`, and `notify.q.user-registered` but does **not** list a `notify.q.order-cancelled` queue — the event catalog (§7) does say notification-service is a consumer of `order.cancelled`. The planner must add `notify.q.order-cancelled` (bound to `orders.tx` / `order.cancelled`) as a fourth queue, mirroring the existing pattern. [VERIFIED: saga-contracts.md §2 and §7, read in this session]

The QUAL-04 saga integration test extends the existing `SagaHappyPathE2ETest` pattern in `infra-tests/`. That test already boots payment-service with Testcontainers Postgres + RabbitMQ and asserts `payment.completed` publication within 15 s. Phase 7 must add notification-service to the boot context and assert a row in `notification.notifications` within the same Awaitility window. The known caveats from Phase 5 (Plan 04-02 lesson: `rabbitmq:3.13-management` for Testcontainers, bean disambiguation for shared-name classes, `classpath:db/migration/<schema>` subdirectory for Flyway in infra-tests, no `.autoDelete()` on sniffer queues) all apply here unchanged.

**Primary recommendation:** Plan three plans across three waves — (W0) notification-service module scaffold + Flyway migrations + config-server YAML + docker-compose entry; (W1) four saga consumers + `notifications` audit table + structured JSON log + idempotency; (W2) QUAL-04 infra-tests extension covering the full saga happy path + gateway route for the optional `/notifications` inbox endpoint + Phase 7 smoke runbook.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| NOTIF-01 | notification-service consumes `order.confirmed` / `order.failed` / `order.cancelled` events from RabbitMQ | Queue topology documented in §Standard Stack; consumer pattern cloned from inventory-service OrderCreatedConsumer |
| NOTIF-02 | notification-service logs structured "email payload" (recipient, subject, body) instead of sending real email — closes saga loop | Notifications audit table schema + structured JSON log pattern documented in §Architecture Patterns |
| NOTIF-03 | notification-service is a fully independent microservice with its own Postgres schema and Spring AMQP listener | DB user `notification_user` + schema `notification` already provisioned in `infra/postgres/init.sh`; NOTIFICATION_DB_PASSWORD in `.env.example`; module scaffold mirrors cart-service/order-service pattern |
| QUAL-04 | Saga happy-path integration test covering `OrderCreated → StockReserved → PaymentCompleted → OrderConfirmed → notification logged`, using Testcontainers + Awaitility, passes on green CI | Research §Validation Architecture documents extension pattern; `SagaHappyPathE2ETest` is the starting point |

</phase_requirements>

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Event consumption (`order.confirmed`, `order.cancelled`, `payment.failed`, `user.registered`) | notification-service (AMQP) | — | Pure leaf consumer — no upstream coordination needed |
| Idempotency enforcement | notification-service DB (`processed_events`) | — | Per CLAUDE.md Rule #3; every consumer in this project uses the inbox table |
| Audit log persistence | notification-service DB (`notifications` table) | — | Owned by notification-service schema; no other service reads it |
| Structured log emission | notification-service application layer | — | SLF4J + `LogstashEncoder` already configured via shared `common-logging`; no separate sink |
| Saga happy-path integration test | `infra-tests` module | notification-service classpath dep | Same pattern as existing `SagaHappyPathE2ETest`; notification-service added as an additional boot context |
| DLQ routing | RabbitMQ topology (declared by `NotificationRabbitConfig`) | — | `x-dead-letter-exchange` + 3-attempt Spring Retry interceptor |
| Turkish email body templates | notification-service domain layer | — | Inline string templates; no external template engine needed |

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.5.14 | Application framework | Locked; matches every other service |
| Spring AMQP (`spring-boot-starter-amqp`) | managed by Boot BOM | RabbitMQ listener container + `RabbitTemplate` | Same as inventory-service, cart-service, order-service |
| Spring Data JPA (`spring-boot-starter-data-jpa`) | managed by Boot BOM | `processed_events` + `notifications` JPA entities | Same as all other business services |
| PostgreSQL 16 + Flyway 12.5.0 | Boot BOM + `flyway-core:12.5.0` + `flyway-database-postgresql:12.5.0` | Schema migration for `notification` schema | Same two-artifact Flyway pattern used by all services since Phase 1 |
| `common-events` (project module) | local | `Envelope` record, `RabbitRetryConfig` (`StatefulRetryInterceptor` + `SimpleRabbitListenerContainerFactory`) | Every saga consumer in the project depends on this |
| `common-outbox` (project module) | local | `OutboxEvent`, `AbstractOutboxPoller` | notification-service does NOT need this — it is a leaf consumer with no outbox. Do NOT import |
| `common-error` (project module) | local | `ProblemDetailControllerAdvice` | Optional; only needed if `/notifications` inbox REST endpoint is added |
| `common-logging` (project module) | local | `CorrelationIdFilter`, AMQP MDC aspect, logstash JSON config | All services import this for `correlationId` propagation |
| Spring Cloud Eureka Client | managed by Spring Cloud 2025.0.x BOM | Service registration + discovery | Eureka-registered; gateway can route `/api/v1/notifications/**` |
| Spring Cloud Config Client | managed by Spring Cloud 2025.0.x BOM | Per-service config from config-server | `spring.config.import=optional:configserver:` pattern |
| Springdoc (`springdoc-openapi-starter-webmvc-ui:2.8.17`) | 2.8.17 | Swagger UI (optional if no REST surface) | Only needed if `/notifications` inbox endpoint ships in this phase |

[VERIFIED: version numbers from `.planning/research/STACK.md`, read in this session]

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Inline string template bodies | Thymeleaf / Freemarker | Zero dep overhead; Turkish copy is static for v1; template engine adds dependency weight for no demo value |
| `processed_events` inbox (DB table) | Redis SETNX | Postgres is already present; Redis is not in scope; DB table is simpler and consistent with all existing consumers |
| `AcknowledgeMode.AUTO` + Spring Retry | Manual ack + `basicAck()` | `AUTO` is the locked pattern per D-10 ArchUnit gate; MANUAL ack without `Channel` param will fail the gate |

**Installation:** No new dependencies vs Phase 6. notification-service's `build.gradle.kts` is a clone of cart-service or payment-service (whichever is cleaner), minus `common-outbox` and Iyzico SDK.

---

## Architecture Patterns

### System Architecture Diagram

```
[identity-service]            [order-service]              [payment-service]
  identity.tx                   orders.tx                    payments.tx
  user.registered               order.confirmed              payment.failed
       │                        order.cancelled                    │
       │                              │                            │
       └──────────────────────────────┼────────────────────────────┘
                                      │
                             RabbitMQ choreography bus
                                      │
                      ┌───────────────┼───────────────┐
                      │               │               │
          notify.q.user-registered  notify.q.     notify.q.
                      │            order-confirmed  payment-failed
                      │            notify.q.
                      │            order-cancelled
                      │
                      ▼
              [notification-service] ← pure leaf consumer
                      │
                      ├── processed_events table (idempotency)
                      ├── notifications table (audit)
                      └── SLF4J JSON log line (structured)
```

### Recommended Project Structure

```
notification-service/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── java/com/n11/notification/
    │   │   ├── NotificationServiceApplication.java
    │   │   ├── config/
    │   │   │   └── NotificationRabbitConfig.java   # 4 queues + DLQs + bindings
    │   │   ├── domain/
    │   │   │   └── Notification.java               # JPA entity for audit table
    │   │   ├── repository/
    │   │   │   ├── NotificationRepository.java
    │   │   │   └── ProcessedEventRepository.java
    │   │   ├── messaging/
    │   │   │   ├── OrderConfirmedConsumer.java      # @RabbitListener
    │   │   │   ├── OrderCancelledConsumer.java      # @RabbitListener
    │   │   │   ├── PaymentFailedConsumer.java       # @RabbitListener
    │   │   │   ├── UserRegisteredConsumer.java      # @RabbitListener
    │   │   │   ├── ProcessedEvent.java              # local @Entity (no common-outbox)
    │   │   │   └── NotificationService.java         # @Transactional — idempotency + persist + log
    │   │   └── api/ (optional)
    │   │       └── NotificationsController.java     # GET /notifications (optional inbox)
    │   └── resources/db/migration/
    │       ├── V1__init_processed_events.sql        # from service-template
    │       └── V2__init_notifications.sql           # notifications audit table
    └── test/
        ├── java/com/n11/notification/
        │   ├── messaging/
        │   │   └── OrderConfirmedConsumerIdempotencyTest.java
        │   └── domain/
        │       └── NotificationServiceTest.java
        └── resources/
            └── application.yml                     # optional:configserver: + hikari search_path
```

### Pattern 1: Saga Consumer (Listener → Transactional Delegate)

Exact pattern from `inventory-service/OrderCreatedConsumer.java` (VERIFIED in this session). All four notification-service consumers follow this shape:

```java
// Source: inventory-service/src/main/java/com/n11/inventory/messaging/OrderCreatedConsumer.java
@Component
public class OrderConfirmedConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = NotificationRabbitConfig.QUEUE_NOTIFY_ORDER_CONFIRMED)
    public void handleOrderConfirmed(Message amqpMessage) {
        String body = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
        Envelope envelope;
        try {
            envelope = objectMapper.readValue(body, Envelope.class);
        } catch (Exception e) {
            LOG.error("notification.consumer: malformed envelope, routing to DLQ. body={}", body, e);
            throw new AmqpRejectAndDontRequeueException("Malformed saga envelope", e);
        }
        UUID eventId;
        try {
            eventId = UUID.fromString(envelope.eventId());
        } catch (Exception e) {
            throw new AmqpRejectAndDontRequeueException("Invalid eventId: " + envelope.eventId(), e);
        }
        if (envelope.payload() == null || envelope.payload().isNull()) {
            throw new AmqpRejectAndDontRequeueException("Null payload for event " + eventId);
        }
        // Transient service exceptions propagate → StatefulRetryInterceptor retries → DLQ
        notificationService.handleOrderConfirmed(eventId, envelope);
    }
}
```

```java
// NotificationService.java — owns @Transactional boundary
@Service
public class NotificationService {
    @Transactional
    public void handleOrderConfirmed(UUID eventId, Envelope envelope) {
        // 1. Idempotency check INSIDE transaction
        if (processedEventRepository.existsById(eventId)) {
            LOG.debug("notification: duplicate event {}, skipping", eventId);
            return;
        }
        // 2. Deserialize payload
        OrderConfirmedPayload payload = objectMapper.treeToValue(
            envelope.payload(), OrderConfirmedPayload.class);
        // 3. Persist audit row
        Notification n = new Notification(
            UUID.randomUUID(), payload.userId(), "EMAIL", "ORDER_CONFIRMED",
            buildOrderConfirmedBody(payload), Instant.now());
        notificationRepository.save(n);
        // 4. Write processed_events row (SAME transaction)
        processedEventRepository.save(new ProcessedEvent(eventId, "order-confirmed-consumer", "order.confirmed"));
        // 5. Emit structured log (NOT part of the transaction — side effect after commit is fine)
        LOG.info("notification.sent recipient={} subject={} correlationId={} eventType=order.confirmed",
            payload.userId(), "Siparişiniz onaylandı", envelope.correlationId());
    }
}
```

[VERIFIED pattern from inventory-service code + Plan 04-02 decisions, read in this session]

### Pattern 2: RabbitMQ Topology — Four Queues + DLQs

```java
// Source: modeled on inventory-service/src/main/java/com/n11/inventory/messaging/InventoryRabbitConfig.java
@Configuration
public class NotificationRabbitConfig {

    // Queue name constants (saga-contracts.md §2)
    public static final String QUEUE_NOTIFY_ORDER_CONFIRMED  = "notify.q.order-confirmed";
    public static final String QUEUE_NOTIFY_ORDER_CANCELLED  = "notify.q.order-cancelled";  // §7 adds this
    public static final String QUEUE_NOTIFY_PAYMENT_FAILED   = "notify.q.payment-failed";
    public static final String QUEUE_NOTIFY_USER_REGISTERED  = "notify.q.user-registered";

    // Idempotent re-declarations of exchanges owned by other services
    @Bean TopicExchange ordersExchange() { return ExchangeBuilder.topicExchange("orders.tx").durable(true).build(); }
    @Bean TopicExchange paymentsExchange() { return ExchangeBuilder.topicExchange("payments.tx").durable(true).build(); }
    @Bean TopicExchange identityExchange() { return ExchangeBuilder.topicExchange("identity.tx").durable(true).build(); }

    // --- notify.q.order-confirmed (orders.tx / order.confirmed → cart-service clears cart) ---
    @Bean
    public Queue notifyOrderConfirmedQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFY_ORDER_CONFIRMED)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", QUEUE_NOTIFY_ORDER_CONFIRMED + ".dlq")
                .build();
    }
    @Bean public Queue notifyOrderConfirmedDlq() { return QueueBuilder.durable(QUEUE_NOTIFY_ORDER_CONFIRMED + ".dlq").build(); }
    @Bean public Binding notifyOrderConfirmedBinding(Queue notifyOrderConfirmedQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(notifyOrderConfirmedQueue).to(ordersExchange).with("order.confirmed");
    }

    // --- Similarly for order-cancelled, payment-failed, user-registered ---
}
```

[VERIFIED: pattern from InventoryRabbitConfig.java, read in this session; queue names from saga-contracts.md §2]

### Pattern 3: ProcessedEvent — Local Entity (not common-outbox)

notification-service is a **consumer only** — no outbox. `ProcessedEvent` lives as a local `@Entity` in the `com.n11.notification.messaging` package, following the same pattern as cart-service (per Phase 5 Plan 05-02 note: "cart-service does NOT import `:common-outbox` — consumer-only service. `ProcessedEvent` entity is local to `com.n11.cart.messaging`"). [VERIFIED: STATE.md decision 2026-04-30 Plan 05-02]

### Pattern 4: Notifications Audit Table DDL

```sql
-- V2__init_notifications.sql
-- Audit log for "email payload" mock notifications (NOTIF-02).
-- Pure append-only; no FK to any other service schema (boundary enforcement).

CREATE TABLE notifications (
    id              UUID          PRIMARY KEY,
    user_id         UUID          NOT NULL,         -- recipient
    channel         VARCHAR(32)   NOT NULL DEFAULT 'EMAIL',
    event_type      VARCHAR(128)  NOT NULL,         -- e.g. 'order.confirmed'
    subject         TEXT          NOT NULL,
    body            TEXT          NOT NULL,
    correlation_id  UUID,                           -- from saga envelope
    sent_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_id_sent_at ON notifications (user_id, sent_at DESC);
```

[ASSUMED: field set matches ARCHITECTURE.md §2.10 `notifications(id, user_id, channel, type, payload_json, status, sent_at)` but uses `subject + body` instead of `payload_json` for better Springdoc/log readability. Planner should decide column naming.]

### Pattern 5: Turkish Email Body Templates

All bodies are inline String.format templates — no external template engine needed:

| Event | Subject (TR) | Body template (TR) |
|-------|-------------|-------------------|
| `order.confirmed` | `Siparişiniz onaylandı` | `"Merhaba, %s siparişiniz onaylandı. Toplam tutar: %.2f TL."` (orderId, totalAmount) |
| `order.cancelled` | `Siparişiniz iptal edildi` | `"Merhaba, %s siparişiniz iptal edildi. Sebep: %s."` (orderId, reason TR) |
| `payment.failed` | `Ödemeniz alınamadı` | `"Merhaba, %s siparişinize ait ödeme alınamadı. Lütfen tekrar deneyin."` (orderId) |
| `user.registered` | `Hoş geldiniz!` | `"Merhaba %s, n11'e hoş geldiniz. Üyeliğiniz başarıyla oluşturuldu."` (fullName) |

Reason code Turkish mapping for `order.cancelled`:
- `OUT_OF_STOCK` → `"Stok yetersizliği"`
- `PAYMENT_DECLINED` → `"Ödeme reddedildi"`
- `USER_CANCELLED` → `"Sipariş iptal edildi"`
- `PAYMENT_TIMEOUT` → `"Ödeme süresi doldu"`

[ASSUMED: exact wording is illustrative; planner can adjust. The requirement is Turkish copy per LOC constraints in CLAUDE.md.]

### Anti-Patterns to Avoid

- **Importing `common-outbox`:** notification-service emits no saga events; it has no outbox. Importing the module would add a `@Scheduled` poller that polls an empty `outbox` table forever. DO NOT import.
- **`@Transactional` on `@RabbitListener` method:** AOP proxy may be bypassed on the AMQP container thread. Split into listener (deserialization + routing) + `@Transactional @Service` method, per Plan 04-02 lesson. [VERIFIED: STATE.md decision 2026-04-29]
- **`AcknowledgeMode.MANUAL` without `Channel` parameter:** Fails the D-10 ArchUnit gate in `infra-tests/AmqpAckModeArchTest`. Use `AcknowledgeMode.AUTO` (default via `RabbitRetryConfig`). [VERIFIED: AmqpAckModeArchTest.java exists, read build.gradle.kts deps]
- **Auto-delete sniffer queues in QUAL-04 test:** Plan 05-04 lesson — `autoDelete()` causes queue deletion between Awaitility poll iterations. Use `QueueBuilder.nonDurable(name)` with NO `.autoDelete()`. [VERIFIED: STATE.md decision 2026-04-30 Plan 05-04]
- **Logging PII in the body field:** `userId` (UUID) is acceptable; raw email addresses should go in the `subject` line only. No names or email in INFO-level JSON logs.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| DLQ routing after 3 failures | Custom retry counter on a DB table | `StatefulRetryInterceptor` (already in `common-events.RabbitRetryConfig`) | Edge cases with concurrent consumers, state across pod restarts |
| Message deduplication | Custom `Set<UUID>` in memory | `processed_events` inbox table (already exists in service-template DDL) | Memory is lost on restart; table survives crashes |
| Turkish localization | External i18n framework | Inline string templates | v1 has 4 fixed event types; no dynamic locale resolution needed |
| Structured JSON logs | Custom log appender | SLF4J + `LogstashEncoder` (already configured via `common-logging`) | Already in every service; re-use the existing MDC pattern |
| AMQP exchange/queue declaration | RabbitMQ admin REST API calls | Spring AMQP `@Bean Declarables` / `QueueBuilder` | Idempotent, verified by Spring context lifecycle |

**Key insight:** notification-service is the simplest service in the repo. Every infrastructure need (retry, DLQ, idempotency, structured logging) is already provided by shared modules. The planner should resist adding complexity.

---

## Event Payload Shapes

All schemas verified against `.planning/saga-contracts/` files read in this session.

### `order.confirmed` (from `orders.tx`)

Publisher: order-service. Schema: `order-confirmed.schema.json`.

```json
{
  "orderId":     "uuid",
  "userId":      "uuid",
  "totalAmount": 1299.50,
  "items": [
    { "productId": "uuid", "qty": 2, "unitPrice": 549.50 }
  ]
}
```

Envelope routing: exchange `orders.tx`, routing key `order.confirmed`, queue `notify.q.order-confirmed`.

### `order.cancelled` (from `orders.tx`)

Publisher: order-service. Schema: `order-cancelled.schema.json`.

```json
{
  "orderId": "uuid",
  "userId":  "uuid",
  "reason":  "OUT_OF_STOCK | PAYMENT_DECLINED | USER_CANCELLED | PAYMENT_TIMEOUT"
}
```

Envelope routing: exchange `orders.tx`, routing key `order.cancelled`, queue `notify.q.order-cancelled` (MUST be added to saga-contracts.md §2 queue table — currently listed only in §7 event catalog).

### `payment.failed` (from `payments.tx`)

Publisher: payment-service. Schema: `payment-failed.schema.json`.

```json
{
  "orderId":   "uuid",
  "paymentId": "uuid",
  "reason":    "DECLINED | TIMEOUT | FRAUD | INSUFFICIENT_FUNDS | UNKNOWN",
  "errorCode": "string | null"
}
```

Envelope routing: exchange `payments.tx`, routing key `payment.failed`, queue `notify.q.payment-failed`.

### `user.registered` (from `identity.tx`)

Publisher: identity-service. Schema: `user-registered.schema.json`. Exchange `identity.tx` is declared by `IdentityRabbitConfig.java` and is already live. [VERIFIED: IdentityRabbitConfig.java read in this session]

```json
{
  "userId":       "uuid",
  "email":        "ali@example.com",
  "fullName":     "Ali Kendir",
  "registeredAt": "2026-04-30T12:00:00Z"
}
```

Envelope routing: exchange `identity.tx`, routing key `user.registered`, queue `notify.q.user-registered`.

---

## Common Pitfalls

### Pitfall 1: `notify.q.order-cancelled` Missing from Queue Topology

**What goes wrong:** The planner declares only the three queues listed in saga-contracts.md §2 (`order-confirmed`, `payment-failed`, `user-registered`), forgets that §7 also lists notification-service as a consumer of `order.cancelled`. Cancellation notifications never fire. Demo: user cancels order, no "Siparişiniz iptal edildi" notification in logs.

**Why it happens:** The saga-contracts.md §2 queue table does not include `notify.q.order-cancelled` (verified in this session). The §7 event catalog does note the consumer.

**How to avoid:** Add `notify.q.order-cancelled` explicitly in `NotificationRabbitConfig` with DLQ. Update saga-contracts.md §2 table as part of Phase 7 Wave 0.

**Warning signs:** Four expected consumers; only three queues declared; no `OrderCancelledConsumer.java` in the service.

### Pitfall 2: Non-Idempotent Consumer on Re-Delivery

**What goes wrong:** `order.confirmed` arrives twice (RabbitMQ redelivery after connection blip). Notification-service inserts two rows in `notifications` and emits two log lines for the same order. Demo shows duplicate notification records.

**Why it happens:** Developer assumes at-most-once delivery. RabbitMQ explicitly guarantees at-least-once. CLAUDE.md Rule #3 mandates idempotency on every consumer.

**How to avoid:** `processedEventRepository.existsById(eventId)` check INSIDE the `@Transactional` service method, before any insert. Same pattern as inventory-service/InventoryOrderService.java.

**Warning signs:** `processed_events` count for a given `event_id` is 1 but `notifications` count for the same `correlation_id` is 2.

### Pitfall 3: Bean Disambiguation Failure in infra-tests Multi-Service Classpath

**What goes wrong:** QUAL-04 test boots notification-service + payment-service + order-service on the same classpath. Two services each have a class named `ProcessedEvent` or `SampleHealthController`. Spring context fails to start with `BeanDefinitionStoreException: found multiple beans of type ...`.

**Why it happens:** Plan 05-04 decision (STATE.md): "Bean disambiguation mandatory for multi-service classpath: `@Entity(name=...)`, `@RestController(beanName)`, `@Component(beanName)` required for all shared-name classes." notification-service's local `ProcessedEvent` entity MUST carry `@Entity(name = "NotificationProcessedEvent")`.

**How to avoid:** Follow Plan 05-04 pattern. Name all entities and components with service-specific prefixes when designing for infra-tests inclusion.

**Warning signs:** `UnsatisfiedDependencyException` or `BeanDefinitionStoreException` when running `./gradlew :infra-tests:test`.

### Pitfall 4: Flyway Version Collision in infra-tests

**What goes wrong:** infra-tests boots with `flyway.locations=classpath:db/migration` and finds `V1__init_processed_events.sql` from BOTH notification-service AND payment-service at the same path → `FlywayException: Found more than one migration with version 1`.

**Why it happens:** Plan 05-04 fix (STATE.md): "infra-tests Flyway must use `classpath:db/migration/<schema>` subdirectory not `classpath:db/migration`." The QUAL-04 test config must follow this pattern.

**How to avoid:** Copy notification-service migrations to `infra-tests/src/test/resources/db/migration/notification/V1__init_processed_events.sql` and `V2__init_notifications.sql`. Set `spring.flyway.locations=classpath:db/migration/notification` in the QUAL-04 test's application config (or a per-test `@SpringBootTest` property).

**Warning signs:** `FlywayException: Found more than one migration with version N`.

### Pitfall 5: `identity.tx` Exchange Not Re-Declared in notification-service

**What goes wrong:** `notify.q.user-registered` cannot bind to `identity.tx` if `identity-service` starts after notification-service. Notification-service fails to start with `ChannelClosedException: channel error; channel ... reason: ...`.

**Why it happens:** Spring AMQP declares exchanges and queues at context startup; if the exchange doesn't exist yet, the queue declaration fails (even though `identity.tx` is durable — it persists across broker restart but not across fresh broker launch when identity-service hasn't started yet).

**How to avoid:** `NotificationRabbitConfig` must include an idempotent re-declaration of `identity.tx` (just as `InventoryRabbitConfig` re-declares `payments.tx`). Idempotent AMQP re-declarations are safe. [VERIFIED pattern: InventoryRabbitConfig.java line 47-50, read in this session]

**Warning signs:** notification-service context startup fails with AMQP topology error when identity-service is not yet running.

### Pitfall 6: Omitting `@EntityScan("com.n11")` on Application Class

**What goes wrong:** notification-service imports `common-logging` or another shared module that ships JPA entities. Spring only scans `com.n11.notification.*` by default. Entities from shared modules are invisible. `Table 'notification.processed_events' not found` at startup.

**Why it happens:** Plan 05-01 decision (STATE.md): "`@EntityScan("com.n11")` required on `@SpringBootApplication` classes when a shared Gradle library module contributes JPA entities." notification-service's local `ProcessedEvent` and `Notification` entities live in `com.n11.notification.*` — `@SpringBootApplication(scanBasePackages="com.n11")` covers this. BUT if any shared module ever adds JPA entities in the future, `@EntityScan("com.n11")` on the application class is the defensive fix.

**How to avoid:** Apply `@EntityScan("com.n11")` and `@SpringBootApplication(scanBasePackages="com.n11")` from day 1.

---

## Code Examples

### QUAL-04 — Extending SagaHappyPathE2ETest for Notification

The existing `SagaHappyPathE2ETest` in infra-tests boots only `payment-service` (via `PaymentServiceTestConfig`). QUAL-04 requires extending the chain. The full happy path is:

`order.created` → inventory-service (`StockReservedConsumer`) → `stock.reserved` → payment-service (`StockReservedConsumer`) → `payment.completed` → order-service (`PaymentCompletedConsumer`) → `order.confirmed` → notification-service (`OrderConfirmedConsumer`)

The existing test already covers steps 1–4 (publishes `stock.reserved` and asserts `payment.completed`). QUAL-04 extends by:

1. Adding notification-service + order-service to the `@SpringBootTest(classes = {...})` list
2. Publishing a synthetic `stock.reserved` or `order.confirmed` (depending on test scope)
3. Awaiting a row in `notification.notifications` using `notificationRepository.findAll()` with Awaitility

```java
// infra-tests/src/test/java/com/n11/infratests/saga/QualFourSagaTest.java
// (New test — NOT replacing SagaHappyPathE2ETest)
@SpringBootTest(
    classes = { NotificationServiceTestConfig.class },
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.config.import=",
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "eureka.client.enabled=false"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class QualFourSagaNotificationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("n11").withUsername("postgres").withPassword("postgres");

    @Container @ServiceConnection
    static RabbitMQContainer RABBIT = new RabbitMQContainer(
        DockerImageName.parse("rabbitmq:3.13-management")); // NOT 4.0

    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired NotificationRepository notificationRepository;
    @Autowired ObjectMapper objectMapper;

    @Test
    void orderConfirmedEvent_persistsNotificationRow_within10s() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID userId  = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        String envelope = """
            {"eventId":"%s","eventType":"order.confirmed","eventVersion":1,
             "occurredAt":"%s","correlationId":"%s","causationId":null,"producer":"order-service",
             "payload":{"orderId":"%s","userId":"%s","totalAmount":149.90,
                        "items":[{"productId":"%s","qty":1,"unitPrice":149.90}]}}
            """.formatted(eventId, Instant.now(), orderId, orderId, userId, UUID.randomUUID());

        MessageProperties props = new MessageProperties();
        props.setMessageId(eventId.toString());
        props.setContentType("application/json");
        props.setCorrelationId(orderId.toString());
        Message msg = new Message(envelope.getBytes(), props);
        rabbitTemplate.send("orders.tx", "order.confirmed", msg);

        await().atMost(Duration.ofSeconds(10))
               .pollInterval(Duration.ofMillis(300))
               .untilAsserted(() -> {
                   List<Notification> rows = notificationRepository.findByUserId(userId);
                   assertThat(rows).hasSize(1);
                   assertThat(rows.get(0).getEventType()).isEqualTo("order.confirmed");
               });
    }
}
```

[ASSUMED: `NotificationServiceTestConfig` is a new test config class modeled on `PaymentServiceTestConfig.java` in infra-tests]

### Flyway infra-tests Resource Layout for QUAL-04

```
infra-tests/src/test/resources/db/migration/
├── notification/
│   ├── V1__init_processed_events.sql   (copy from notification-service; same content)
│   └── V2__init_notifications.sql      (copy from notification-service)
├── payment/           (already exists for SagaHappyPathE2ETest)
│   ├── V1__init_processed_events.sql
│   ├── V2__init_payment.sql
│   └── V3__iyzico_checkout_fields.sql
```

Application config for the new test context:

```yaml
# infra-tests/src/test/resources/application-notification-test.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration/notification
    schemas: notification
    default-schema: notification
    create-schemas: true
    placeholders:
      flyway.schema: notification
  datasource:
    hikari:
      connection-init-sql: "SET search_path = notification, public"
  jpa:
    properties:
      hibernate:
        default_schema: notification
```

[VERIFIED pattern from infra-tests/src/test/resources/application.yml and STATE.md Plan 05-04 decision, both read in this session]

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@Transactional` on `@RabbitListener` | `@Transactional` only on service delegate; listener is NOT transactional | Phase 4 Plan 04-02 | Eliminates AOP proxy bypassing on AMQP container thread |
| `AcknowledgeMode.MANUAL` without Channel | `AcknowledgeMode.AUTO` + `RejectAndDontRequeueRecoverer` | Phase 4 Plan 04-03 (commit `2b61689`) | Locked by D-10 ArchUnit gate |
| `rabbitmq:4.0-management` in Testcontainers | `rabbitmq:3.13-management` | Phase 4 Plan 04-02 | Eliminates EOFException / Connection reset during AMQP handshake |
| Direct `@Autowired RabbitTemplate.send()` in event publishers | `AbstractOutboxPoller` → `MessagePostProcessor` sets `messageId` from envelope JSON | Phase 5 Plan 05-01 | D-09 fix; `StatefulRetryInterceptor.messageKeyGenerator` requires `messageId` on every message |
| Per-service copy-pasted `OutboxPoller` | `common-outbox.AbstractOutboxPoller` (shared module) | Phase 5 Plan 05-01 | Structural prevention of the 999.2 regression |

---

## Infrastructure Already Available for Phase 7

These items require NO new provisioning — they exist from prior phases:

| Item | Provisioned By | Status |
|------|---------------|--------|
| `notification` Postgres schema | `infra/postgres/init.sh` (Phase 1 Plan 01-03) | Available |
| `notification_user` DB user | Same `init.sh` | Available |
| `NOTIFICATION_DB_PASSWORD` env var | `.env.example` line 12 | Available (value: `changeme-notification`) |
| `identity.tx` exchange declaration | `IdentityRabbitConfig.java` (Phase 3) | Available (durable; survives broker restart) |
| `orders.tx`, `payments.tx` exchanges | Phase 4/5 services | Available (durable) |
| `notify.q.user-registered` binding entry | saga-contracts.md (Phase 3 update) | Documented; queue itself declared by Phase 7 |
| `notify.q.order-confirmed`, `notify.q.payment-failed` | saga-contracts.md §2 | Documented; queues declared by Phase 7 |
| `common-events` `RabbitRetryConfig` | Phase 1 Plan 01-04 | All consumers reference this factory bean |
| ArchUnit `AmqpAckModeArchTest` gate | Phase 5 Plan 05-01 D-10 | notification-service consumers automatically gated on next `./gradlew :infra-tests:test` |

[VERIFIED: all items confirmed from init.sh grep, .env.example grep, IdentityRabbitConfig.java read, saga-contracts.md read, this session]

---

## Planning Inputs — Key Decisions the Planner Must Make

These items were not resolved in research and require planner decision:

1. **Port assignment for notification-service:** The 808x convention gives: identity=8081, product=8082, inventory=8083, cart=8084, order=8085, payment=8086. Notification should be **8087**. [ASSUMED — 8087 is next in sequence; planner should confirm no conflict]

2. **`notify.q.order-cancelled` queue:** The saga-contracts.md §2 table omits this queue; §7 event catalog lists notification-service as a consumer. The planner must decide: (a) add the queue in Phase 7 and update saga-contracts.md §2 in Wave 0; or (b) scope the consumer to only three events for Phase 7 ROADMAP requirements (NOTIF-01 says "order.confirmed / order.failed / order.cancelled" — both order.confirmed and order.cancelled are required). Decision: ADD the queue; update saga-contracts.md §2.

3. **QUAL-04 test scope:** The ROADMAP says `OrderCreated → StockReserved → PaymentCompleted → OrderConfirmed → notification logged`. This is a five-hop chain requiring four services on the infra-tests classpath (inventory, payment, order, notification). Alternatively, QUAL-04 can be a narrower integration test that publishes `order.confirmed` directly and asserts notification logging (a two-service test: publisher stub + notification-service). The narrower test is easier to build but less architecturally impressive. Recommendation: **implement both** — a two-service idempotency test per service (`OrderConfirmedConsumerIdempotencyTest` in notification-service) PLUS a five-hop end-to-end test in infra-tests that extends or parallels `SagaHappyPathE2ETest`.

4. **Optional `/notifications` inbox REST endpoint:** ARCHITECTURE.md §2.10 lists `GET /notifications (current user — optional, for in-app inbox)` with "Behind gateway? Optional inbox endpoint YES, otherwise internal." Phase 7 should ship this endpoint if the planner has capacity; it's not required for NOTIF-01/02/03 but makes the service genuinely observable in the demo. Recommendation: ship a minimal `GET /notifications` returning the last 20 notifications for the `X-User-Id` user.

5. **Hikari pool size for notification-service:** PITFALLS.md #8 says services with no compute pressure (notification is log-only mock) should use `maximum-pool-size: 2`. The config-server YAML for notification-service should set `spring.datasource.hikari.maximum-pool-size: 2`.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker (Testcontainers) | QUAL-04 integration test | Available (CI runner Linux has Docker pre-installed) | Verified by existing Testcontainers tests passing | — |
| `pgvector/pgvector:pg16` image | Testcontainers Postgres container | Available (already pulled by existing tests) | pg16 + pgvector 0.8.2 | — |
| `rabbitmq:3.13-management` image | Testcontainers RabbitMQ container | Available (already used in infra-tests) | 3.13 | — |
| `notification` Postgres schema + `notification_user` | Runtime `docker-compose up` | Available — provisioned by init.sh | — | — |
| `NOTIFICATION_DB_PASSWORD` env var | Runtime | Available in `.env.example` | — | — |

[VERIFIED: Testcontainers images confirmed from existing `SagaHappyPathE2ETest.java` and `infra-tests/build.gradle.kts`, read in this session]

---

## Validation Architecture

> `workflow.nyquist_validation: true` is set in `.planning/config.json` — this section is required. [VERIFIED: config.json read in this session]

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5.x (Spring Boot 3.5.14 BOM) + Testcontainers 2.0.5 + Awaitility 4.2.0 |
| Config file | per-service `src/test/resources/application.yml` (`optional:configserver:` + `hikari.connection-init-sql=SET search_path=notification`) |
| Quick run command | `./gradlew :notification-service:test` |
| Full suite command | `./gradlew test` (root — all modules) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| NOTIF-01 | `order.confirmed` consumer writes `processed_events` row and `notifications` row; redelivery is no-op | integration (Testcontainers Postgres) | `./gradlew :notification-service:test --tests OrderConfirmedConsumerIdempotencyTest` | ❌ Wave 0 |
| NOTIF-01 | `order.cancelled` consumer writes notification row; redelivery is no-op | integration (Testcontainers Postgres) | `./gradlew :notification-service:test --tests OrderCancelledConsumerIdempotencyTest` | ❌ Wave 0 |
| NOTIF-01 | `payment.failed` consumer writes notification row; redelivery is no-op | integration (Testcontainers Postgres) | `./gradlew :notification-service:test --tests PaymentFailedConsumerIdempotencyTest` | ❌ Wave 0 |
| NOTIF-01 | `user.registered` consumer writes notification row; redelivery is no-op | integration (Testcontainers Postgres) | `./gradlew :notification-service:test --tests UserRegisteredConsumerIdempotencyTest` | ❌ Wave 0 |
| NOTIF-02 | Structured JSON log line includes `recipient`, `subject`, Turkish body, `correlationId`, `eventType` | unit (LogCaptor / Spring Boot test log assertion) | `./gradlew :notification-service:test --tests NotificationServiceLogTest` | ❌ Wave 0 |
| NOTIF-03 | notification-service module starts, registers with Eureka, Flyway migrations apply; schema isolation | smoke (live `docker compose up`) | `curl http://localhost:8087/actuator/health` | ❌ Wave 2 (smoke runbook) |
| QUAL-04 | Full saga chain: `order.confirmed` published → notification-service consumer fires → row in `notifications` table within 10 s | integration E2E (Testcontainers Postgres + RabbitMQ) | `./gradlew :infra-tests:test --tests QualFourSagaNotificationTest` | ❌ Wave 0 |
| ARCH-07 | DLQ routing: malformed message → DLQ after 3 attempts; sane message → no DLQ entry | integration (Testcontainers RabbitMQ) | `./gradlew :notification-service:test --tests ConsumerDlqRoutingTest` | ❌ Wave 1 (optional if time-constrained) |

### Sampling Rate

- **Per task commit:** `./gradlew :notification-service:test` (per-service slice, ~30 s)
- **Per wave merge:** `./gradlew test` (full suite, ~120–150 s — adds notification-service + infra-tests)
- **Phase gate:** Full suite green + Phase 7 smoke runbook executed before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `notification-service/src/test/java/com/n11/notification/messaging/OrderConfirmedConsumerIdempotencyTest.java` — covers NOTIF-01 (repeat-delivery assertion)
- [ ] `notification-service/src/test/java/com/n11/notification/messaging/NotificationServiceLogTest.java` — covers NOTIF-02 (log line shape)
- [ ] `notification-service/src/test/resources/application.yml` — `optional:configserver:` + search_path
- [ ] `infra-tests/src/test/java/com/n11/infratests/saga/QualFourSagaNotificationTest.java` — covers QUAL-04
- [ ] `infra-tests/src/test/java/com/n11/infratests/saga/NotificationServiceTestConfig.java` — bean config for QUAL-04 (modeled on `PaymentServiceTestConfig.java`)
- [ ] `infra-tests/src/test/resources/db/migration/notification/V1__init_processed_events.sql` — Flyway location for infra-tests
- [ ] `infra-tests/src/test/resources/db/migration/notification/V2__init_notifications.sql` — Flyway location for infra-tests

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | notification-service port = 8087 (next in 808x sequence) | Standard Stack / Config | Port conflict; planner should verify docker-compose has no 8087 binding |
| A2 | `notify.q.order-cancelled` queue does not exist and must be added; this requires a saga-contracts.md §2 update in Wave 0 | Architecture Patterns / DLQ topology | If queue already exists from an undiscovered service, the declare is idempotent — no harm |
| A3 | `notifications` audit table uses `subject + body TEXT` columns rather than `payload JSONB` as in ARCHITECTURE.md §2.10 | Pattern 4 DDL | Planner should align with ARCHITECTURE.md §2.10 `payload_json` if `JSONB` is preferred for querying |
| A4 | `QUAL-04` test is a two-hop test (publisher stub → notification-service) in notification-service + a five-hop test in infra-tests; the "full chain" requirement is satisfied by the infra-tests test | Validation Architecture | Planner must confirm both granularities are acceptable under ROADMAP SC-3 |
| A5 | Turkish email body templates are sufficient as `String.format` inline literals (no external template engine) | Pattern 5 | Planner may prefer Freemarker if bodies become complex; for v1 inline strings are simpler |
| A6 | `hikari.maximum-pool-size=2` for notification-service is appropriate (leaf consumer, no REST compute load) | Planning Inputs | Acceptable performance reduction if consumer concurrent delivery spikes; 2 is fine for demo scale |

---

## Open Questions

1. **Does identity-service actually emit `user.registered` events in the current docker-compose stack?**
   - What we know: `OutboxBackedUserRegistrationOutboxPublisher.publishRegistered()` is wired in identity-service (verified in this session). `IdentityRabbitConfig` declares `identity.tx`. The outbox poller (based on `common-outbox.AbstractOutboxPoller`) polls every 5 s.
   - What's unclear: Whether the `identity-service` poller is actively publishing on the deployed stack (it migrated to `common-outbox` in Phase 5 Plan 05-01 — verify it was committed cleanly).
   - Recommendation: Phase 7 Wave 0 smoke — register a new user and grep `identity-service` logs for outbox poll → AMQP publish trace. If the event never appears, notification-service will silently skip `user.registered` without a queue error (queue is declared but never receives messages).

2. **Should `notify.q.order-cancelled` be declared as part of notification-service or as an update to order-service's topology config?**
   - What we know: saga-contracts.md §2 table omits this queue; notification-service is the logical owner of its own consumer queues.
   - Recommendation: notification-service declares the queue (with DLQ) in `NotificationRabbitConfig`. No change to order-service is needed — order-service only publishes to `orders.tx/order.cancelled`; it does not know who is listening.

3. **How wide should the QUAL-04 integration test be?**
   - What we know: ROADMAP says "OrderCreated → StockReserved → PaymentCompleted → OrderConfirmed → notification logged" — a five-service chain. Existing `SagaHappyPathE2ETest` already covers steps 1–4 (boots payment-service, asserts `payment.completed`).
   - What's unclear: Adding order-service to the infra-tests boot context carries the same bean-disambiguation risk encountered in Plan 05-04 (`OrderPaymentFailureCompensationE2ETest`). The planner should check whether `OrderServiceApplication` already carries proper `@EntityScan`/`@SpringBootApplication(scanBasePackages="com.n11")` annotations before adding it to the multi-service context.
   - Recommendation: Start with the narrower two-service test (publish `order.confirmed` directly → assert notification row). Then extend to five-hop if time permits; document as QUAL-04 satisfied by the narrower test + Phase 5's `SagaHappyPathE2ETest` as a chain witness.

---

## Sources

### Primary (HIGH confidence)

- `inventory-service/src/main/java/com/n11/inventory/messaging/OrderCreatedConsumer.java` — canonical consumer pattern (listener / delegate split, AcknowledgeMode.AUTO, AmqpRejectAndDontRequeueException) — VERIFIED read in session
- `inventory-service/src/main/java/com/n11/inventory/messaging/InventoryRabbitConfig.java` — queue / DLQ / binding declaration pattern — VERIFIED read in session
- `.planning/saga-contracts.md` §1-9 — envelope schema, queue topology (§2), retry policy (§4), idempotency contract (§5), event catalog (§7) — VERIFIED read in session
- `.planning/saga-contracts/order-confirmed.schema.json` — `order.confirmed` payload shape — VERIFIED read in session
- `.planning/saga-contracts/order-cancelled.schema.json` — `order.cancelled` payload shape — VERIFIED read in session
- `.planning/saga-contracts/payment-failed.schema.json` — `payment.failed` payload shape — VERIFIED read in session
- `.planning/saga-contracts/user-registered.schema.json` — `user.registered` payload shape — VERIFIED read in session
- `infra-tests/src/test/java/com/n11/infratests/saga/SagaHappyPathE2ETest.java` — Testcontainers + Awaitility E2E test pattern (Postgres + RabbitMQ, sniffer queue, messageId assertion, non-autoDelete sniffer) — VERIFIED read in session
- `infra-tests/build.gradle.kts` — infra-tests dep list (Testcontainers, Awaitility 4.2.0, ArchUnit 1.4.2) — VERIFIED read in session
- `infra-tests/src/test/resources/application.yml` — per-schema Flyway config for infra-tests, search_path, rabbitmq auto-startup — VERIFIED read in session
- `infra/postgres/init.sh` grep — `notification` schema, `notification_user`, `NOTIFICATION_DB_PASSWORD` already provisioned — VERIFIED in session
- `.env.example` — `NOTIFICATION_DB_PASSWORD=changeme-notification` — VERIFIED in session
- `identity-service/src/main/java/com/n11/identity/outbox/IdentityRabbitConfig.java` — `identity.tx` exchange declaration — VERIFIED read in session
- `identity-service/src/main/java/com/n11/identity/outbox/OutboxBackedUserRegistrationOutboxPublisher.java` — `user.registered` envelope construction — VERIFIED read in session
- `.planning/STATE.md` — all Phase 4/5 accumulated decisions (Plan 04-02: Testcontainers RabbitMQ version; Plan 04-02: @Transactional split; Plan 05-01: @EntityScan; Plan 05-02: cart-service local ProcessedEvent; Plan 05-04: bean disambiguation; Plan 05-04: Flyway subdirectory; Plan 05-04: non-autoDelete sniffer) — VERIFIED read in session
- `.planning/config.json` — `nyquist_validation: true`, `security_enforcement: false` — VERIFIED read in session

### Secondary (MEDIUM confidence)

- `.planning/research/ARCHITECTURE.md` §2.10 — notification-service contract (owned data, REST surface, events consumed) — researched 2026-04-28; patterns confirmed by Phase 5 implementation

### Tertiary (LOW confidence)

- Turkish email body template wording — derived from CLAUDE.md instructions (Turkish UI copy) and project n11 context; exact strings not verified against user acceptance

---

## Metadata

**Confidence breakdown:**

- Standard Stack: HIGH — all versions from verified in-repo build files; no new external libraries
- Architecture: HIGH — queue topology from saga-contracts.md (VERIFIED); consumer pattern from OrderCreatedConsumer.java (VERIFIED); DLQ config from InventoryRabbitConfig.java (VERIFIED)
- Event Payloads: HIGH — direct from schema JSON files (VERIFIED)
- QUAL-04 test pattern: HIGH — based on `SagaHappyPathE2ETest.java` (VERIFIED); minor ASSUMED items for new test class structure
- Turkish copy: MEDIUM — wording illustrative; content correct per CLAUDE.md; exact phrasing is planner discretion

**Research date:** 2026-04-30
**Valid until:** 2026-05-30 (stable patterns; only risk is Spring Boot patch version bump, which has no impact here)
