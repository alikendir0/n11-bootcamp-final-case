package com.n11.infratests.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.order.messaging.ProcessedEventRepository;
import com.n11.order.order.Order;
import com.n11.order.order.OrderRepository;
import com.n11.order.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Cross-service compensation proof for {@code payment.failed} on the order side
 * (Plan 06-05 Task 2 behavior 3, PAY-03 + QUAL-05).
 *
 * <p>Boots order-service in isolation against real Testcontainers Postgres + RabbitMQ;
 * publishes a canonical {@code payment.failed} envelope to {@code payments.tx} with
 * {@code messageId=eventId} (D-09 invariant); awaits order-service's
 * {@link com.n11.order.messaging.PaymentFailedConsumer} firing through real AMQP delivery
 * (NOT direct invocation) and asserts the order has transitioned to {@link OrderStatus#CANCELLED}
 * with {@code cancel_reason = "PAYMENT_DECLINED"}.
 *
 * <p>Filtered eventId/orderId assertions (Phase 5 SagaConsumerIdempotencyTest lesson):
 * we never assert {@code processed_events.count() == N} — we filter by {@code eventId} and
 * {@code orderId}, because the table is shared across tests within the same context.
 *
 * <p>The order side and inventory side run as separate Spring contexts because each service
 * mounts its own JPA EntityManager with its own {@code default_schema} and its own copy of
 * {@code processed_events}. A single boot would collide on the {@code processed_events.event_id}
 * primary key when both consumer beans tried to record the same envelope.
 */
@SpringBootTest(classes = OrderPaymentFailureTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.config.import=",
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "eureka.client.enabled=false",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/orders",
        "spring.flyway.schemas=orders",
        "spring.flyway.default-schema=orders",
        "spring.flyway.create-schemas=true",
        "spring.jpa.properties.hibernate.default_schema=orders",
        "spring.datasource.hikari.connection-init-sql=SET search_path = orders, public",
        // Order-service clients are not exercised by the saga consumer path under test
        // (no REST calls happen on payment.failed — only DB persist + outbox write).
        // Provide harmless placeholder URLs so RestClient bean construction does not fail.
        "app.clients.cart.base-url=http://localhost:0",
        "app.clients.identity.base-url=http://localhost:0",
        "app.clients.product.base-url=http://localhost:0"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext
class OrderPaymentFailureCompensationE2ETest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("n11").withUsername("postgres").withPassword("postgres");

    @Container
    @ServiceConnection
    static RabbitMQContainer RABBIT = new RabbitMQContainer(
        DockerImageName.parse("rabbitmq:3.13-management"));   // Plan 04-02 lesson: NOT 4.0

    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired ConnectionFactory connectionFactory;
    @Autowired ObjectMapper objectMapper;
    @Autowired OrderRepository orderRepository;
    @Autowired ProcessedEventRepository processedEventsRepository;

    @BeforeEach
    void redeclarePaymentsExchange() {
        // Defensive: ensure the producer-side exchange exists before we publish.
        // order-service's OrderRabbitConfig idempotently re-declares payments.tx,
        // but Spring context bean order is not deterministic across tests.
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        TopicExchange paymentsTx = ExchangeBuilder.topicExchange("payments.tx").durable(true).build();
        admin.declareExchange(paymentsTx);
    }

    @Test
    void paymentFailed_cancelsOrderWithPaymentDeclinedReason() {
        UUID orderId = UUID.randomUUID();
        seedPendingOrder(orderId);

        UUID eventId = UUID.randomUUID();
        publishPaymentFailed(eventId, orderId, "DECLINED", "IYZICO_DECLINED");

        await().atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                Order reloaded = orderRepository.findById(orderId).orElseThrow();
                assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CANCELLED);
                assertThat(reloaded.getCancelReason()).isEqualTo("PAYMENT_DECLINED");
                // Filter by this specific eventId — never call .count() on the whole table.
                assertThat(processedEventsRepository.existsById(eventId)).isTrue();
            });
    }

    @Test
    void paymentTimeoutEnvelope_cancelsOrderWithSamePaymentDeclinedReason() {
        // QUAL-05 invariant: the timeout path uses the same downstream compensation event
        // shape as Iyzico decline (reason=TIMEOUT, errorCode=PAYMENT_TIMEOUT). Order-service's
        // OrderSagaService.processPaymentFailed treats both equivalently — order moves to
        // CANCELLED with cancel_reason=PAYMENT_DECLINED regardless of upstream sub-cause.
        UUID orderId = UUID.randomUUID();
        seedPendingOrder(orderId);

        UUID eventId = UUID.randomUUID();
        publishPaymentFailed(eventId, orderId, "TIMEOUT", "PAYMENT_TIMEOUT");

        await().atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                Order reloaded = orderRepository.findById(orderId).orElseThrow();
                assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CANCELLED);
                assertThat(reloaded.getCancelReason()).isEqualTo("PAYMENT_DECLINED");
                assertThat(processedEventsRepository.existsById(eventId)).isTrue();
            });
    }

    @Test
    void duplicatePaymentFailed_isIdempotent_orderRemainsCancelled() {
        UUID orderId = UUID.randomUUID();
        seedPendingOrder(orderId);

        UUID eventId = UUID.randomUUID();
        publishPaymentFailed(eventId, orderId, "DECLINED", "IYZICO_DECLINED");

        // Wait for first delivery to land.
        await().atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() ->
                assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.CANCELLED));

        Order afterFirst = orderRepository.findById(orderId).orElseThrow();
        Instant updatedAtAfterFirst = afterFirst.getUpdatedAt();

        // Republish the SAME envelope (eventId reused) — at-least-once delivery semantics.
        publishPaymentFailed(eventId, orderId, "DECLINED", "IYZICO_DECLINED");

        // Sleep-and-poll: the second delivery should be a no-op (processed_events row exists,
        // OrderSagaService short-circuits before any state mutation).
        await().pollDelay(Duration.ofSeconds(3)).timeout(Duration.ofSeconds(8))
            .untilAsserted(() -> {
                Order latest = orderRepository.findById(orderId).orElseThrow();
                assertThat(latest.getStatus()).isEqualTo(OrderStatus.CANCELLED);
                assertThat(latest.getCancelReason()).isEqualTo("PAYMENT_DECLINED");
                // updated_at must not advance on the no-op redelivery.
                assertThat(latest.getUpdatedAt()).isEqualTo(updatedAtAfterFirst);
                // Filter by this specific eventId — only one row regardless of redeliveries.
                assertThat(processedEventsRepository.existsById(eventId)).isTrue();
            });
    }

    private void seedPendingOrder(UUID orderId) {
        UUID userId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        Order order = new Order(orderId, userId, OrderStatus.STOCK_RESERVED,
            new BigDecimal("250.00"), "TRY", orderId, idempotencyKey);
        orderRepository.save(order);
    }

    private void publishPaymentFailed(UUID eventId, UUID orderId, String reason, String errorCode) {
        UUID paymentId = UUID.randomUUID();
        String envelopeJson = """
            {"eventId":"%s","eventType":"payment.failed","eventVersion":1,
             "occurredAt":"%s","correlationId":"%s","causationId":null,"producer":"payment-service",
             "payload":{"orderId":"%s","paymentId":"%s","reason":"%s","errorCode":"%s"}}
            """.formatted(eventId, Instant.now(), orderId, orderId, paymentId, reason, errorCode);

        MessageProperties props = new MessageProperties();
        props.setMessageId(eventId.toString());     // D-09 invariant
        props.setContentType("application/json");
        props.setCorrelationId(orderId.toString());
        Message m = new Message(envelopeJson.getBytes(), props);
        rabbitTemplate.send("payments.tx", "payment.failed", m);
    }
}
