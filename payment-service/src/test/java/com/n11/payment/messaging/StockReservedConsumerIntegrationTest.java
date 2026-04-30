package com.n11.payment.messaging;

import com.n11.payment.outbox.PaymentOutboxRepository;
import com.n11.payment.payment.PaymentRepository;
import com.n11.payment.payment.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link StockReservedConsumer}: Testcontainers Postgres,
 * direct consumer invocation (no AMQP broker needed for per-service test).
 *
 * <p>Asserts:
 * 1. One payments row written with the REAL totalAmount from the inbound envelope (W4 invariant).
 * 2. One outbox payment.completed row.
 * 3. Redelivery (same eventId) is a no-op — idempotency (ARCH-07).
 */
@SpringBootTest(classes = com.n11.payment.PaymentServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "mock.payment.delay-ms=0"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class StockReservedConsumerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("n11").withUsername("postgres").withPassword("postgres");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path = payment, public");
    }

    @Autowired StockReservedConsumer consumer;
    @Autowired PaymentRepository paymentRepository;
    @Autowired ProcessedEventRepository processedEventsRepository;
    @Autowired PaymentOutboxRepository outboxRepository;

    @Test
    void stockReserved_writesPaymentsRowCompleted_andOutboxPaymentCompleted_redeliveryIsNoop() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        String env = """
            {"eventId":"%s","eventType":"stock.reserved","eventVersion":1,
             "occurredAt":"%s","correlationId":"%s","causationId":null,"producer":"inventory-service",
             "payload":{"orderId":"%s","reservationId":"%s","reservedItems":[{"productId":"%s","qty":2}],"totalAmount":250.50}}
            """.formatted(eventId, Instant.now(), orderId, orderId, UUID.randomUUID(), UUID.randomUUID());

        Message m = new Message(env.getBytes(), new MessageProperties());
        consumer.handleStockReserved(m);
        consumer.handleStockReserved(m);  // redelivery — must be a no-op

        var payments = paymentRepository.findByOrderId(orderId);
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payments.get(0).getAmount()).isEqualByComparingTo(new java.math.BigDecimal("250.50"));  // W4 invariant
        assertThat(payments.get(0).getIyzicoPaymentId()).startsWith("mock-");

        long outboxCount = outboxRepository.findAll().stream()
            .filter(e -> "payment.completed".equals(e.getEventType()))
            .filter(e -> e.getPayload().contains(orderId.toString()))
            .count();
        assertThat(outboxCount).isEqualTo(1);

        assertThat(processedEventsRepository.existsById(eventId)).isTrue();
        assertThat(processedEventsRepository.count()).isEqualTo(1);
    }
}
