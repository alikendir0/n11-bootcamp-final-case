package com.n11.infratests.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.inventory.messaging.ProcessedEventRepository;
import com.n11.inventory.reservation.StockReservation;
import com.n11.inventory.reservation.StockReservationRepository;
import com.n11.inventory.stock.Stock;
import com.n11.inventory.stock.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Cross-service compensation proof for {@code payment.failed} (Plan 06-05 Task 2,
 * QUAL-05 + PAY-03).
 *
 * <p>Boots inventory-service in isolation against real Testcontainers Postgres + RabbitMQ;
 * publishes a canonical {@code payment.failed} envelope to {@code payments.tx} with
 * {@code messageId=eventId} (D-09 invariant); awaits inventory-service's
 * {@link com.n11.inventory.messaging.PaymentFailedConsumer} firing through real AMQP
 * delivery (NOT direct invocation) and asserts the saga reservation has transitioned
 * to {@code RELEASED}.
 *
 * <p>Test 2 redelivers the same event and asserts the side effect is stable — only
 * one stock reservation gets released even when the broker delivers the event twice
 * (CLAUDE.md Rule #3, ARCH-07 idempotency invariant).
 *
 * <p>Order-service compensation (Test 3 in Plan 06-05) lives in
 * {@link OrderPaymentFailureCompensationE2ETest} (same file): the two side effects need
 * separate Spring contexts because each service mounts its own JPA EntityManager with
 * its own {@code default_schema} and its own copy of {@code processed_events}. A single
 * boot would collide on the {@code processed_events.event_id} primary key when both
 * consumer beans tried to record the same envelope.
 *
 * <p>Filtered eventId assertions (Phase 5 SagaConsumerIdempotencyTest lesson): we never
 * assert {@code processed_events.count() == N} because the table is shared across tests
 * within the same context — instead we filter by {@code eventId} or {@code orderId}.
 */
@SpringBootTest(classes = InventoryCompensationTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.config.import=",
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "eureka.client.enabled=false",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/inventory",
        "spring.flyway.schemas=inventory",
        "spring.flyway.default-schema=inventory",
        "spring.flyway.create-schemas=true",
        "spring.jpa.properties.hibernate.default_schema=inventory",
        "spring.datasource.hikari.connection-init-sql=SET search_path = inventory, public"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class PaymentFailureCompensationE2ETest {

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
    @Autowired StockRepository stockRepository;
    @Autowired StockReservationRepository reservationRepository;
    @Autowired ProcessedEventRepository processedEventsRepository;

    @BeforeEach
    void redeclarePaymentsExchange() {
        // Defensive: ensure the producer-side exchange exists before we publish.
        // inventory-service's InventoryRabbitConfig idempotently re-declares payments.tx,
        // but the Spring context's bean order is not deterministic across tests.
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        TopicExchange paymentsTx = ExchangeBuilder.topicExchange("payments.tx").durable(true).build();
        admin.declareExchange(paymentsTx);
        // Sniffer queue (non-durable, NOT auto-delete — Phase 5 lesson) so we can confirm
        // routing without affecting the inventory consumer queue.
        admin.declareQueue(QueueBuilder.nonDurable("test.sniffer.payment-failed-inventory").build());
        admin.declareBinding(BindingBuilder.bind(QueueBuilder.nonDurable("test.sniffer.payment-failed-inventory").build())
            .to(paymentsTx).with("payment.failed"));
    }

    @Test
    void paymentFailed_releasesStockReservationForOrder() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        seedStockAndReservation(productId, orderId, 5);

        UUID eventId = UUID.randomUUID();
        publishPaymentFailed(eventId, orderId, "DECLINED", "IYZICO_DECLINED");

        await().atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                List<StockReservation> released = reservationRepository
                    .findByOrderIdAndStatus(orderId, "RELEASED");
                assertThat(released).hasSize(1);
                assertThat(released.get(0).getReservedQty()).isEqualTo(5);
                // Filter by this specific eventId — never call .count() on the whole table.
                assertThat(processedEventsRepository.existsById(eventId)).isTrue();
            });
    }

    @Test
    void duplicatePaymentFailed_isIdempotent_releasesOnlyOnce() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        seedStockAndReservation(productId, orderId, 3);

        UUID eventId = UUID.randomUUID();
        publishPaymentFailed(eventId, orderId, "DECLINED", "IYZICO_DECLINED");

        // Wait for first delivery to complete.
        await().atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() ->
                assertThat(reservationRepository.findByOrderIdAndStatus(orderId, "RELEASED"))
                    .hasSize(1));

        // Capture available qty after the first release: stock should be back to 5
        // (3 reserved + 2 free; release brings reserved → 0, available → 5).
        Stock afterFirstRelease = stockRepository.findById(productId).orElseThrow();
        int availableAfterFirst = afterFirstRelease.getAvailableQty();

        // Republish the SAME envelope (eventId reused) — at-least-once delivery semantics.
        publishPaymentFailed(eventId, orderId, "DECLINED", "IYZICO_DECLINED");

        // Sleep-and-poll: the second delivery should be a no-op. Wait long enough that any
        // erroneous re-processing would surface, then assert state is unchanged.
        await().pollDelay(Duration.ofSeconds(3)).timeout(Duration.ofSeconds(8))
            .untilAsserted(() -> {
                Stock latest = stockRepository.findById(productId).orElseThrow();
                assertThat(latest.getAvailableQty()).isEqualTo(availableAfterFirst);
                List<StockReservation> released = reservationRepository
                    .findByOrderIdAndStatus(orderId, "RELEASED");
                assertThat(released).hasSize(1);
                // Filter by this specific eventId — only one row regardless of redeliveries.
                assertThat(processedEventsRepository.existsById(eventId)).isTrue();
            });
    }

    private void seedStockAndReservation(UUID productId, UUID orderId, int qty) {
        // Seed stock with enough free qty so reservation is valid.
        Stock stock = new Stock(productId, qty + 2, 5);   // available=qty+2, low_stock_threshold=5
        stock.reserve(qty);
        stockRepository.save(stock);
        StockReservation reservation = new StockReservation(orderId, productId, qty);
        reservationRepository.save(reservation);
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
