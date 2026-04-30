# Phase 7: Notification (Saga Closure) - Pattern Map

**Mapped:** 2026-04-30
**Files analyzed:** 22 new/modified files
**Analogs found:** 22 / 22

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `notification-service/build.gradle.kts` | config | N/A | `cart-service/build.gradle.kts` | exact |
| `notification-service/src/main/java/com/n11/notification/NotificationServiceApplication.java` | config | N/A | `cart-service/src/main/java/com/n11/cart/CartServiceApplication.java` | exact |
| `notification-service/src/main/java/com/n11/notification/config/NotificationRabbitConfig.java` | config | event-driven | `inventory-service/src/main/java/com/n11/inventory/messaging/InventoryRabbitConfig.java` | exact |
| `notification-service/src/main/java/com/n11/notification/messaging/OrderConfirmedConsumer.java` | middleware | event-driven | `cart-service/src/main/java/com/n11/cart/messaging/OrderConfirmedConsumer.java` | exact |
| `notification-service/src/main/java/com/n11/notification/messaging/OrderCancelledConsumer.java` | middleware | event-driven | `inventory-service/src/main/java/com/n11/inventory/messaging/OrderCancelledConsumer.java` | exact |
| `notification-service/src/main/java/com/n11/notification/messaging/PaymentFailedConsumer.java` | middleware | event-driven | `inventory-service/src/main/java/com/n11/inventory/messaging/PaymentFailedConsumer.java` | exact |
| `notification-service/src/main/java/com/n11/notification/messaging/UserRegisteredConsumer.java` | middleware | event-driven | `cart-service/src/main/java/com/n11/cart/messaging/OrderConfirmedConsumer.java` | role-match |
| `notification-service/src/main/java/com/n11/notification/messaging/NotificationService.java` | service | CRUD | `cart-service/src/main/java/com/n11/cart/messaging/CartSagaService.java` | exact |
| `notification-service/src/main/java/com/n11/notification/messaging/ProcessedEvent.java` | model | N/A | `cart-service/src/main/java/com/n11/cart/messaging/ProcessedEvent.java` | exact |
| `notification-service/src/main/java/com/n11/notification/messaging/ProcessedEventRepository.java` | model | CRUD | `cart-service/src/main/java/com/n11/cart/messaging/ProcessedEventRepository.java` | exact |
| `notification-service/src/main/java/com/n11/notification/domain/Notification.java` | model | N/A | `cart-service/src/main/java/com/n11/cart/messaging/ProcessedEvent.java` | role-match |
| `notification-service/src/main/java/com/n11/notification/repository/NotificationRepository.java` | model | CRUD | `cart-service/src/main/java/com/n11/cart/cart/CartRepository.java` | role-match |
| `notification-service/src/main/java/com/n11/notification/api/NotificationsController.java` | controller | request-response | `inventory-service/src/main/java/com/n11/inventory/stock/StockController.java` | role-match |
| `notification-service/src/main/resources/application.yml` | config | N/A | `cart-service/src/main/resources/application.yml` | exact |
| `notification-service/src/main/resources/db/migration/V1__init_processed_events.sql` | migration | N/A | `cart-service/src/main/resources/db/migration/V1__init_processed_events.sql` | exact |
| `notification-service/src/main/resources/db/migration/V2__init_notifications.sql` | migration | N/A | `cart-service/src/main/resources/db/migration/V2__init_cart.sql` | role-match |
| `config-server/src/main/resources/config/notification-service.yml` | config | N/A | `config-server/src/main/resources/config/cart-service.yml` | exact |
| `notification-service/src/test/java/com/n11/notification/messaging/OrderConfirmedConsumerIdempotencyTest.java` | test | event-driven | `cart-service/src/test/java/com/n11/cart/messaging/OrderConfirmedConsumerIdempotencyTest.java` | exact |
| `notification-service/src/test/resources/application.yml` | config | N/A | `cart-service/src/test/resources/application.yml` | exact |
| `infra-tests/src/test/java/com/n11/infratests/saga/QualFourSagaNotificationTest.java` | test | event-driven | `infra-tests/src/test/java/com/n11/infratests/saga/SagaHappyPathE2ETest.java` | exact |
| `infra-tests/src/test/java/com/n11/infratests/saga/NotificationServiceTestConfig.java` | config | N/A | `infra-tests/src/test/java/com/n11/infratests/saga/PaymentServiceTestConfig.java` | exact |
| `infra-tests/src/test/resources/db/migration/notification/V1__init_processed_events.sql` | migration | N/A | `infra-tests/src/test/resources/db/migration/payment/V1__init_processed_events.sql` | exact |
| `infra-tests/src/test/resources/db/migration/notification/V2__init_notifications.sql` | migration | N/A | `infra-tests/src/test/resources/db/migration/payment/V2__init_payment.sql` | role-match |

Also modified:
- `settings.gradle.kts` — add `"notification-service"` to include list
- `docker-compose.yml` — add `notification-service` entry
- `infra-tests/build.gradle.kts` — add `testImplementation(project(":notification-service"))`

---

## Pattern Assignments

### `notification-service/build.gradle.kts` (config)

**Analog:** `cart-service/build.gradle.kts` (lines 1–64)

**Core pattern** — copy verbatim, then:
- Remove `implementation(project(":common-outbox"))` — notification-service is a leaf consumer with no outbox
- Change `archiveBaseName.set("notification-service")`
- Change Jib container port to `8087`
- Change Jib image to `n11/notification-service:dev`

```kotlin
plugins {
    id("org.springframework.boot")
    id("com.google.cloud.tools.jib")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")

    implementation("org.flywaydb:flyway-core:12.5.0")
    implementation("org.flywaydb:flyway-database-postgresql:12.5.0")

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:8.0")

    // Cross-cutting — NO common-outbox (leaf consumer emits no events)
    implementation(project(":common-error"))
    implementation(project(":common-logging"))
    implementation(project(":common-events"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:rabbitmq")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.awaitility:awaitility:4.2.0")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("notification-service")
}

jib {
    from { image = "eclipse-temurin:21-jre" }
    to { image = "n11/notification-service:dev" }
    container {
        ports = listOf("8087")
        jvmFlags = listOf("-XX:+UseG1GC", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0")
    }
}
```

---

### `notification-service/src/main/java/com/n11/notification/NotificationServiceApplication.java` (config)

**Analog:** `cart-service/src/main/java/com/n11/cart/CartServiceApplication.java` (lines 1–14)

**Core pattern** — identical structure; change package and class name only.

```java
package com.n11.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

// @EntityScan("com.n11") is implicit via scanBasePackages="com.n11" (Pitfall 6 mitigation).
// All local @Entity classes in com.n11.notification.* are discovered automatically.
@SpringBootApplication(scanBasePackages = "com.n11")
@EnableDiscoveryClient
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
```

---

### `notification-service/src/main/java/com/n11/notification/config/NotificationRabbitConfig.java` (config, event-driven)

**Analog:** `inventory-service/src/main/java/com/n11/inventory/messaging/InventoryRabbitConfig.java` (lines 1–113)

**Imports pattern** (lines 1–6):
```java
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
```

**Core topology pattern** (lines 21–113) — four-queue variant. Critical notes:
- Re-declare `orders.tx`, `payments.tx`, `identity.tx` idempotently (bean names must be unique vs inventory's beans — use `ordersExchangeForNotification` etc., following the naming from `CartRabbitConfig.ordersExchangeForCart`)
- Each queue has its own DLQ via `x-dead-letter-exchange=""` + `x-dead-letter-routing-key=<queue>.dlq`
- `notify.q.order-cancelled` is the fourth queue (not in saga-contracts.md §2 yet — planner adds it)

```java
@Configuration
public class NotificationRabbitConfig {

    public static final String QUEUE_NOTIFY_ORDER_CONFIRMED = "notify.q.order-confirmed";
    public static final String QUEUE_NOTIFY_ORDER_CANCELLED = "notify.q.order-cancelled";
    public static final String QUEUE_NOTIFY_PAYMENT_FAILED  = "notify.q.payment-failed";
    public static final String QUEUE_NOTIFY_USER_REGISTERED = "notify.q.user-registered";

    // Idempotent re-declarations — use service-prefixed bean names to avoid clash in infra-tests
    @Bean
    public TopicExchange ordersExchangeForNotification() {
        return ExchangeBuilder.topicExchange("orders.tx").durable(true).build();
    }
    @Bean
    public TopicExchange paymentsExchangeForNotification() {
        return ExchangeBuilder.topicExchange("payments.tx").durable(true).build();
    }
    @Bean
    public TopicExchange identityExchangeForNotification() {
        return ExchangeBuilder.topicExchange("identity.tx").durable(true).build();
    }

    // --- notify.q.order-confirmed ---
    @Bean
    public Queue notifyOrderConfirmedQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFY_ORDER_CONFIRMED)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", QUEUE_NOTIFY_ORDER_CONFIRMED + ".dlq")
                .build();
    }
    @Bean public Queue notifyOrderConfirmedDlq() {
        return QueueBuilder.durable(QUEUE_NOTIFY_ORDER_CONFIRMED + ".dlq").build();
    }
    @Bean
    public Binding notifyOrderConfirmedBinding(Queue notifyOrderConfirmedQueue,
                                               TopicExchange ordersExchangeForNotification) {
        return BindingBuilder.bind(notifyOrderConfirmedQueue)
                .to(ordersExchangeForNotification).with("order.confirmed");
    }

    // Repeat pattern for order-cancelled (orders.tx/order.cancelled),
    // payment-failed (payments.tx/payment.failed),
    // user-registered (identity.tx/user.registered)
}
```

**Key invariants from InventoryRabbitConfig.java lines 39–52:**
- Exchange re-declarations are always idempotent by AMQP semantics
- Spring bean names for the same logical exchange MUST differ across services when running in the same JVM (infra-tests)
- `QueueBuilder.durable(...).withArgument("x-dead-letter-exchange", "").withArgument("x-dead-letter-routing-key", <dlq>)` is the canonical DLQ pattern

---

### `notification-service/src/main/java/com/n11/notification/messaging/OrderConfirmedConsumer.java` (middleware, event-driven)

**Analog:** `cart-service/src/main/java/com/n11/cart/messaging/OrderConfirmedConsumer.java` (lines 1–94)

**Imports pattern** (lines 1–16):
```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
```

**Core listener pattern** (lines 40–79) — listener method is NOT `@Transactional`. Four deserialization failure guards, each throwing `AmqpRejectAndDontRequeueException`:

```java
@Component
public class OrderConfirmedConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(OrderConfirmedConsumer.class);
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public OrderConfirmedConsumer(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

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
            LOG.error("notification.consumer: invalid eventId '{}', routing to DLQ", envelope.eventId(), e);
            throw new AmqpRejectAndDontRequeueException("Invalid eventId: " + envelope.eventId(), e);
        }

        if (envelope.payload() == null || envelope.payload().isNull()) {
            LOG.error("notification.consumer: null payload for event {}, routing to DLQ", eventId);
            throw new AmqpRejectAndDontRequeueException("Null payload for event " + eventId);
        }

        // Payload deserialization — use a local record or inner class mirroring event schema JSON
        OrderConfirmedPayload payload;
        try {
            payload = objectMapper.treeToValue(envelope.payload(), OrderConfirmedPayload.class);
        } catch (Exception e) {
            LOG.error("notification.consumer: cannot parse order.confirmed payload for event {}", eventId, e);
            throw new AmqpRejectAndDontRequeueException("Cannot deserialize order.confirmed payload", e);
        }

        if (payload == null) {
            LOG.error("notification.consumer: deserialized payload is null for event {}, routing to DLQ", eventId);
            throw new AmqpRejectAndDontRequeueException("Deserialized payload is null for event " + eventId);
        }

        // Propagate transient exceptions — StatefulRetryInterceptor retries 3x → DLQ
        notificationService.handleOrderConfirmed(eventId, envelope, payload);
    }

    // Inner record matches order-confirmed.schema.json payload shape
    public record OrderConfirmedPayload(UUID orderId, UUID userId, java.math.BigDecimal totalAmount,
                                        java.util.List<Item> items) {
        public record Item(UUID productId, int qty, java.math.BigDecimal unitPrice) {}
    }
}
```

**Repeat this pattern** for `OrderCancelledConsumer`, `PaymentFailedConsumer`, `UserRegisteredConsumer` — change queue constant, method name, payload record fields, and delegate method name only.

---

### `notification-service/src/main/java/com/n11/notification/messaging/NotificationService.java` (service, CRUD)

**Analog:** `cart-service/src/main/java/com/n11/cart/messaging/CartSagaService.java` (lines 1–48)

**Core transactional pattern** (lines 35–48):

```java
@Service
public class NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;

    public NotificationService(ProcessedEventRepository processedEventRepository,
                               NotificationRepository notificationRepository) {
        this.processedEventRepository = processedEventRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void handleOrderConfirmed(UUID eventId, Envelope envelope,
                                     OrderConfirmedConsumer.OrderConfirmedPayload payload) {
        // 1. Idempotency check INSIDE @Transactional (CLAUDE.md Rule #3)
        if (processedEventRepository.existsById(eventId)) {
            LOG.debug("notification.service: duplicate event {}, skipping", eventId);
            return;
        }
        // 2. Persist audit row
        String body = String.format(
            "Merhaba, %s siparişiniz onaylandı. Toplam tutar: %.2f TL.",
            payload.orderId(), payload.totalAmount());
        Notification n = new Notification(
            UUID.randomUUID(), payload.userId(), "EMAIL", "order.confirmed",
            "Siparişiniz onaylandı", body, envelope.correlationId() != null
                ? UUID.fromString(envelope.correlationId()) : null, Instant.now());
        notificationRepository.save(n);
        // 3. Mark processed (SAME transaction)
        processedEventRepository.save(
            new ProcessedEvent(eventId, "OrderConfirmedConsumer", envelope.eventType()));
        // 4. Structured log (side effect after commit — NOT inside transaction)
        LOG.info("notification.sent recipient={} subject={} correlationId={} eventType=order.confirmed",
            payload.userId(), "Siparişiniz onaylandı", envelope.correlationId());
    }
    // Repeat @Transactional method for each event type
}
```

**Key invariant from CartSagaService.java line 35:** `@Transactional` is on the service method, NOT on the `@RabbitListener` method. Idempotency check + audit save + processedEvent save are all in ONE transaction.

---

### `notification-service/src/main/java/com/n11/notification/messaging/ProcessedEvent.java` (model)

**Analog:** `cart-service/src/main/java/com/n11/cart/messaging/ProcessedEvent.java` (lines 1–49)

**Entity disambiguation pattern** (line 18) — `@Entity(name = "NotificationProcessedEvent")` is mandatory for infra-tests multi-service classpath (Pitfall 3):

```java
package com.n11.notification.messaging;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for notification.processed_events idempotency inbox (CLAUDE.md Rule #3).
 * @Entity name is prefixed to avoid bean name collision in infra-tests multi-service context
 * (Plan 05-04 lesson: bean disambiguation required for shared-name classes).
 */
@Entity(name = "NotificationProcessedEvent")
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "consumer", nullable = false)
    private String consumer;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() { /* JPA */ }

    public ProcessedEvent(UUID eventId, String consumer, String eventType) {
        this.eventId = eventId;
        this.consumer = consumer;
        this.eventType = eventType;
        this.processedAt = Instant.now();
    }

    public UUID getEventId()        { return eventId; }
    public String getConsumer()     { return consumer; }
    public String getEventType()    { return eventType; }
    public Instant getProcessedAt() { return processedAt; }
}
```

**Compare with `CartProcessedEvent` (line 18 of cart analog):** the only difference is `@Entity(name = "NotificationProcessedEvent")` vs `@Entity(name = "CartProcessedEvent")`.

---

### `notification-service/src/main/java/com/n11/notification/domain/Notification.java` (model)

**Analog:** `cart-service/src/main/java/com/n11/cart/messaging/ProcessedEvent.java` (role-match — same JPA entity structure)

**Core entity pattern** — apply same `@Entity(name = "NotificationAudit")` prefix discipline:

```java
package com.n11.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity(name = "NotificationAudit")
@Table(name = "notifications")
public class Notification {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    protected Notification() { /* JPA */ }

    public Notification(UUID id, UUID userId, String channel, String eventType,
                        String subject, String body, UUID correlationId, Instant sentAt) {
        this.id = id; this.userId = userId; this.channel = channel;
        this.eventType = eventType; this.subject = subject; this.body = body;
        this.correlationId = correlationId; this.sentAt = sentAt;
    }

    // getters ...
    public UUID getId()           { return id; }
    public UUID getUserId()       { return userId; }
    public String getChannel()    { return channel; }
    public String getEventType()  { return eventType; }
    public String getSubject()    { return subject; }
    public String getBody()       { return body; }
    public UUID getCorrelationId() { return correlationId; }
    public Instant getSentAt()    { return sentAt; }
}
```

---

### `notification-service/src/main/resources/application.yml` (config)

**Analog:** `cart-service/src/main/resources/application.yml` (lines 1–6)

**Core pattern** — copy and change name only:

```yaml
spring:
  application:
    name: notification-service
  config:
    import: optional:configserver:http://config-server:8888?fail-fast=true&max-attempts=10&max-interval=1500&multiplier=1.2&initial-interval=1100
```

---

### `notification-service/src/main/resources/db/migration/V1__init_processed_events.sql` (migration)

**Analog:** `cart-service/src/main/resources/db/migration/V1__init_processed_events.sql` (lines 1–24)

**Core pattern** — copy verbatim. This file is identical across all consumer services; the schema isolation comes from Flyway's `default-schema=notification` in config-server.

---

### `notification-service/src/main/resources/db/migration/V2__init_notifications.sql` (migration)

**Analog:** `cart-service/src/main/resources/db/migration/V2__init_cart.sql` (role-match for migration structure)

**Core DDL pattern** (per RESEARCH.md §Pattern 4):

```sql
-- V2__init_notifications.sql
-- Audit log for mock email notifications (NOTIF-02).
-- Pure append-only; no FK to any other service schema (schema boundary enforcement).

CREATE TABLE notifications (
    id              UUID          PRIMARY KEY,
    user_id         UUID          NOT NULL,
    channel         VARCHAR(32)   NOT NULL DEFAULT 'EMAIL',
    event_type      VARCHAR(128)  NOT NULL,
    subject         TEXT          NOT NULL,
    body            TEXT          NOT NULL,
    correlation_id  UUID,
    sent_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_id_sent_at ON notifications (user_id, sent_at DESC);
```

---

### `config-server/src/main/resources/config/notification-service.yml` (config)

**Analog:** `config-server/src/main/resources/config/cart-service.yml` (lines 1–65)

**Core pattern** — copy cart-service.yml, then change:
- `server.port: 8087`
- `spring.datasource.username: notification_user`
- `spring.datasource.password: ${NOTIFICATION_DB_PASSWORD}`
- `spring.datasource.hikari.connection-init-sql: SET search_path = notification, public`
- `spring.datasource.hikari.maximum-pool-size: 2` (leaf consumer — per RESEARCH.md Planning Input #5)
- `spring.flyway.schemas: notification`
- `spring.flyway.default-schema: notification`
- `spring.flyway.placeholders.schema: notification`
- `spring.flyway.placeholders.flyway.schema: notification`
- `spring.jpa.properties.hibernate.default_schema: notification`
- `spring.rabbitmq.*` block identical to cart-service.yml

```yaml
server:
  port: 8087

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
    username: notification_user
    password: ${NOTIFICATION_DB_PASSWORD}
    hikari:
      maximum-pool-size: 2
      connection-init-sql: SET search_path = notification, public

  flyway:
    enabled: true
    locations: classpath:db/migration
    schemas: notification
    default-schema: notification
    create-schemas: false
    user: ${spring.datasource.username}
    password: ${spring.datasource.password}
    placeholders:
      schema: notification
      flyway.schema: notification

  jpa:
    open-in-view: false
    properties:
      hibernate:
        default_schema: notification

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

---

### `notification-service/src/test/java/com/n11/notification/messaging/OrderConfirmedConsumerIdempotencyTest.java` (test, event-driven)

**Analog:** `cart-service/src/test/java/com/n11/cart/messaging/OrderConfirmedConsumerIdempotencyTest.java` (lines 1–91)

**Test class structure** (lines 28–91):

```java
@SpringBootTest(classes = com.n11.notification.NotificationServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"  // do not start listener — invoke directly
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class OrderConfirmedConsumerIdempotencyTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("n11").withUsername("postgres").withPassword("postgres");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path = notification, public");
    }

    @Autowired OrderConfirmedConsumer consumer;
    @Autowired ProcessedEventRepository processedEventRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired ObjectMapper objectMapper;

    @Test
    void duplicateEvent_writesNotificationExactlyOnce_processedEventsHasOneRow() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        String envelopeJson = """
            {"eventId":"%s","eventType":"order.confirmed","eventVersion":1,
             "occurredAt":"%s","correlationId":"%s","causationId":null,
             "producer":"order-service",
             "payload":{"orderId":"%s","userId":"%s","totalAmount":149.90,
               "items":[{"productId":"%s","qty":1,"unitPrice":149.90}]}}
            """.formatted(eventId, Instant.now(), orderId, orderId, userId, UUID.randomUUID());

        Message m = new Message(envelopeJson.getBytes(), new MessageProperties());

        // Deliver TWICE (simulating RabbitMQ at-least-once redelivery)
        consumer.handleOrderConfirmed(m);
        consumer.handleOrderConfirmed(m);

        // Notification row written exactly once
        assertThat(notificationRepository.findByUserId(userId)).hasSize(1);
        // Idempotency inbox has exactly one row for this eventId
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }
}
```

**Key difference from cart analog:** assert `notificationRepository.findByUserId(userId)` (size=1) AND `processedEventRepository.existsById(eventId)` (true). Cart analog asserts cart item count. Search path changes to `notification`.

---

### `notification-service/src/test/resources/application.yml` (config)

**Analog:** `cart-service/src/test/resources/application.yml` (lines 1–9)

**Core pattern** — copy verbatim, change application name only:

```yaml
spring:
  application:
    name: notification-service
  config:
    import: "optional:configserver:"
  flyway:
    enabled: false
```

Note: flyway is disabled in per-service tests because each test seeds its own Testcontainers Postgres via `@DynamicPropertySource` with `SET search_path`.

---

### `infra-tests/src/test/java/com/n11/infratests/saga/NotificationServiceTestConfig.java` (config)

**Analog:** `infra-tests/src/test/java/com/n11/infratests/saga/PaymentServiceTestConfig.java` (lines 1–68)

**Core pattern** — exact structural copy; change `com.n11.payment` → `com.n11.notification`, remove `com.n11.outbox` from entity scan (notification-service has no outbox entities), add `@EnableScheduling` only if needed (notification-service has no poller):

```java
package com.n11.infratests.saga;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Spring Boot application for QualFourSagaNotificationTest.
 *
 * Isolation mirrors PaymentServiceTestConfig: explicit scan scope +
 * excludeFilters blocking other services' @SpringBootApplication classes.
 * notification-service has no outbox — @EntityScan excludes com.n11.outbox.
 */
@SpringBootApplication(scanBasePackages = {
    "com.n11.notification",
    "com.n11.events",
    "com.n11.logging"
})
@ComponentScan(
    basePackages = {
        "com.n11.notification",
        "com.n11.events",
        "com.n11.logging"
    },
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.ANNOTATION,
            classes = org.springframework.boot.autoconfigure.SpringBootApplication.class
        )
    }
)
@EntityScan(basePackages = {"com.n11.notification"})
@EnableJpaRepositories(basePackages = {"com.n11.notification"})
class NotificationServiceTestConfig {
    // no-op: all beans come from component scan
}
```

**Critical invariants from PaymentServiceTestConfig.java lines 51–62:**
- `excludeFilters` blocks `@SpringBootApplication` classes from being processed as secondary config — without this, `NotificationServiceApplication`'s `scanBasePackages="com.n11"` would expand to the entire codebase
- No `@EnableScheduling` — notification-service has no `@Scheduled` outbox poller

---

### `infra-tests/src/test/java/com/n11/infratests/saga/QualFourSagaNotificationTest.java` (test, event-driven)

**Analog:** `infra-tests/src/test/java/com/n11/infratests/saga/SagaHappyPathE2ETest.java` (lines 1–133)

**Container declarations** (lines 64–71) — identical images. Use `rabbitmq:3.13-management` (Plan 04-02 lesson — NOT 4.0):

```java
@Container @ServiceConnection
static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
    DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
    .withDatabaseName("n11").withUsername("postgres").withPassword("postgres");

@Container @ServiceConnection
static RabbitMQContainer RABBIT = new RabbitMQContainer(
    DockerImageName.parse("rabbitmq:3.13-management"));   // NOT 4.0 — Plan 04-02 lesson
```

**`@SpringBootTest` properties** — copy from `OrderPaymentFailureCompensationE2ETest.java` lines 57–75, adapted for notification:

```java
@SpringBootTest(classes = NotificationServiceTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.config.import=",
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "eureka.client.enabled=false",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/notification",  // Pitfall 4 fix
        "spring.flyway.schemas=notification",
        "spring.flyway.default-schema=notification",
        "spring.flyway.create-schemas=true",
        "spring.jpa.properties.hibernate.default_schema=notification",
        "spring.datasource.hikari.connection-init-sql=SET search_path = notification, public",
        "spring.rabbitmq.listener.simple.auto-startup=true"  // listeners must fire in E2E test
    })
```

**`@BeforeEach` sniffer-queue setup pattern** (SagaHappyPathE2ETest.java lines 79–96) — notification test does NOT need a sniffer queue (it asserts a DB row, not a message):

```java
@BeforeEach
void redeclareExchanges() {
    RabbitAdmin admin = new RabbitAdmin(connectionFactory);
    admin.declareExchange(ExchangeBuilder.topicExchange("orders.tx").durable(true).build());
}
```

**Awaitility assertion** (SagaHappyPathE2ETest.java lines 119–131) — assert DB row, not queue message:

```java
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
    props.setMessageId(eventId.toString());  // D-09 invariant
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
```

---

### `infra-tests/src/test/resources/db/migration/notification/V1__init_processed_events.sql` (migration)

**Analog:** `infra-tests/src/test/resources/db/migration/payment/V1__init_processed_events.sql` (lines 1–22)

**Core pattern** — copy verbatim from the payment analog. Content is identical; the subdirectory `notification/` is what isolates it from other schemas (Pitfall 4 fix from Plan 05-04).

---

### `infra-tests/src/test/resources/db/migration/notification/V2__init_notifications.sql` (migration)

**Analog:** `infra-tests/src/test/resources/db/migration/payment/V2__init_payment.sql` (role-match)

**Core pattern** — copy `V2__init_notifications.sql` from `notification-service/src/main/resources/db/migration/` verbatim. Content must be identical to the service migration.

---

### `settings.gradle.kts` (config — modification)

**Analog:** `settings.gradle.kts` line 15 (`"payment-service"`)

**Pattern** — add `"notification-service"` to the `include(...)` list after `"payment-service"`:

```kotlin
include(
    // ... existing entries ...
    "payment-service",
    "notification-service",   // add this
    "service-template",
    "infra-tests"
)
```

---

### `infra-tests/build.gradle.kts` (config — modification)

**Analog:** `infra-tests/build.gradle.kts` line 52 (`testImplementation(project(":payment-service"))`)

**Pattern** — add after the `order-service` entry:

```kotlin
testImplementation(project(":notification-service"))
```

---

### `docker-compose.yml` (config — modification)

**Analog:** `docker-compose.yml` lines 286–312 (`inventory-service` entry — port 8083, simplest service with no sync REST deps)

notification-service is a pure async consumer with no sync REST dependencies, so it mirrors the inventory-service entry shape (no `depends_on` business services):

```yaml
  # notification-service -- Phase 7 (port 8087) — pure leaf saga consumer.
  # No sync REST deps. Consumes order.confirmed, order.cancelled, payment.failed, user.registered.
  notification-service:
    image: n11/notification-service:dev
    container_name: n11-notification-service
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
      test: ["CMD-SHELL", "wget -q -O- http://localhost:8087/actuator/health | grep -q '\"status\":\"UP\"'"]
      interval: 5s
      timeout: 3s
      retries: 6
      start_period: 25s
    restart: unless-stopped
    networks:
      - n11-net
```

---

## Shared Patterns

### Consumer Listener / Transactional Delegate Split

**Source:** `cart-service/src/main/java/com/n11/cart/messaging/OrderConfirmedConsumer.java` lines 40–79, and `CartSagaService.java` lines 35–47

**Apply to:** All four consumers (`OrderConfirmedConsumer`, `OrderCancelledConsumer`, `PaymentFailedConsumer`, `UserRegisteredConsumer`) and `NotificationService`

The split is mandatory (Plan 04-02 lesson — verified in STATE.md):
- `@RabbitListener` method: NOT `@Transactional`. Only deserializes + validates + delegates. Throws `AmqpRejectAndDontRequeueException` for unrecoverable messages.
- `@Transactional` method: in `NotificationService`. Idempotency check + audit row + processedEvent row, all in ONE transaction.

### Idempotency Check Pattern

**Source:** `cart-service/src/main/java/com/n11/cart/messaging/CartSagaService.java` lines 38–40

**Apply to:** Every `@Transactional` method in `NotificationService`

```java
if (processedEventRepository.existsById(eventId)) {
    LOG.debug("notification.service: duplicate event {}, skipping", eventId);
    return;
}
```

### Entity Name Disambiguation

**Source:** `cart-service/src/main/java/com/n11/cart/messaging/ProcessedEvent.java` line 18, `inventory-service/.../ProcessedEvent.java` line 18

**Apply to:** All `@Entity` classes in notification-service

Pattern: `@Entity(name = "Notification<EntityName>")` — e.g., `NotificationProcessedEvent`, `NotificationAudit`. Required because infra-tests loads multiple services in the same JVM.

### Flyway Subdirectory in infra-tests

**Source:** `infra-tests/src/test/resources/db/migration/payment/` directory structure; `infra-tests/src/test/resources/application.yml` line 29

**Apply to:** QUAL-04 test via `@SpringBootTest` properties

```
infra-tests/src/test/resources/db/migration/notification/
├── V1__init_processed_events.sql
└── V2__init_notifications.sql
```

Property: `spring.flyway.locations=classpath:db/migration/notification` (not `classpath:db/migration` — Pitfall 4).

### TestConfig Isolation Pattern

**Source:** `infra-tests/src/test/java/com/n11/infratests/saga/PaymentServiceTestConfig.java` lines 38–68

**Apply to:** `NotificationServiceTestConfig.java`

The `excludeFilters` blocking `@SpringBootApplication` classes is the key guard. Without it, Spring discovers `NotificationServiceApplication` (which has `scanBasePackages="com.n11"`) and scans the entire codebase, pulling in all other services' beans.

### DLQ Queue Declaration

**Source:** `inventory-service/src/main/java/com/n11/inventory/messaging/InventoryRabbitConfig.java` lines 57–67

**Apply to:** All four queues in `NotificationRabbitConfig`

```java
QueueBuilder.durable(QUEUE_NAME)
    .withArgument("x-dead-letter-exchange", "")
    .withArgument("x-dead-letter-routing-key", QUEUE_NAME + ".dlq")
    .build()
```

Always paired with a plain `QueueBuilder.durable(QUEUE_NAME + ".dlq").build()` DLQ declaration.

### Non-autoDelete Sniffer Queue (infra-tests only)

**Source:** `infra-tests/src/test/java/com/n11/infratests/saga/SagaHappyPathE2ETest.java` lines 92–95

**Apply to:** Any sniffer queue in QUAL-04 test (if added — notification test asserts DB rows, not queue messages, so no sniffer is needed)

If a sniffer is ever needed: `QueueBuilder.nonDurable(name).build()` with NO `.autoDelete()`.

---

## No Analog Found

All files have close analogs in the codebase. No files require falling back to RESEARCH.md external patterns.

---

## Metadata

**Analog search scope:** `cart-service/`, `inventory-service/`, `infra-tests/`, `config-server/`, `docker-compose.yml`, `settings.gradle.kts`
**Files scanned:** 24 source files read
**Pattern extraction date:** 2026-04-30

### Confirmed Infrastructure (no provisioning needed)

| Item | Source | Status |
|------|--------|--------|
| `notification` schema | `infra/postgres/init.sh` lines 97–101 | EXISTS |
| `notification_user` DB user | `infra/postgres/init.sh` line 98 | EXISTS |
| `NOTIFICATION_DB_PASSWORD` env var | `.env.example` | EXISTS (`changeme-notification`) |
| `identity.tx` exchange | `identity-service/IdentityRabbitConfig.java` | EXISTS (durable) |
| `orders.tx`, `payments.tx` exchanges | Phase 4/5 services | EXISTS (durable) |
| `NOTIFICATION_DB_PASSWORD` in docker-compose postgres env | `docker-compose.yml` line 43 | EXISTS |
