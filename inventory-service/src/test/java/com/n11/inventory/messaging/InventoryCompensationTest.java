package com.n11.inventory.messaging;

import com.n11.inventory.reservation.StockReservation;
import com.n11.inventory.reservation.StockReservationRepository;
import com.n11.inventory.stock.Stock;
import com.n11.inventory.stock.StockRepository;
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
 * Integration test for inventory-service compensation consumers (CD-08, CD-09).
 *
 * <p>Uses Testcontainers Postgres + direct consumer invocation (no AMQP broker needed).
 * Asserts that:
 * 1. {@link PaymentFailedConsumer} releases reserved stock and records processed_events (idempotent).
 * 2. {@link OrderCancelledConsumer} releases reserved stock.
 */
@SpringBootTest(classes = com.n11.inventory.InventoryServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class InventoryCompensationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("n11").withUsername("postgres").withPassword("postgres");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path = inventory, public");
    }

    @Autowired PaymentFailedConsumer paymentFailedConsumer;
    @Autowired OrderCancelledConsumer orderCancelledConsumer;
    @Autowired StockRepository stockRepository;
    @Autowired StockReservationRepository reservationRepository;
    @Autowired ProcessedEventRepository processedEventsRepository;

    @Test
    void paymentFailed_releasesReservedStock_redeliveryIsNoop() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        seedReservation(productId, orderId, 5, 100);

        // verify effective available dropped by 5 after reserve
        assertThat(stockRepository.findById(productId).orElseThrow().getEffectiveAvailable()).isEqualTo(95);

        String env = """
            {"eventId":"%s","eventType":"payment.failed","eventVersion":1,
             "occurredAt":"%s","correlationId":"%s","causationId":null,"producer":"payment-service",
             "payload":{"orderId":"%s","paymentId":"%s","reason":"DECLINED","errorCode":"E50"}}
            """.formatted(eventId, Instant.now(), orderId, orderId, UUID.randomUUID());

        Message m = new Message(env.getBytes(), new MessageProperties());
        paymentFailedConsumer.handlePaymentFailed(m);
        paymentFailedConsumer.handlePaymentFailed(m);  // redelivery — must be a no-op

        Stock s = stockRepository.findById(productId).orElseThrow();
        assertThat(s.getEffectiveAvailable()).isEqualTo(100);  // back to original
        assertThat(processedEventsRepository.existsById(eventId)).isTrue();
        // filter by eventId to avoid shared-context count sensitivity
        assertThat(processedEventsRepository.findAll().stream()
            .filter(e -> e.getEventId().equals(eventId)).count()).isEqualTo(1);
    }

    @Test
    void orderCancelled_releasesReservedStock() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        seedReservation(productId, orderId, 3, 50);

        assertThat(stockRepository.findById(productId).orElseThrow().getEffectiveAvailable()).isEqualTo(47);

        String env = """
            {"eventId":"%s","eventType":"order.cancelled","eventVersion":1,
             "occurredAt":"%s","correlationId":"%s","causationId":null,"producer":"order-service",
             "payload":{"orderId":"%s","userId":"%s","reason":"USER_CANCELLED"}}
            """.formatted(eventId, Instant.now(), orderId, orderId, UUID.randomUUID());

        orderCancelledConsumer.handleOrderCancelled(new Message(env.getBytes(), new MessageProperties()));

        Stock s = stockRepository.findById(productId).orElseThrow();
        assertThat(s.getEffectiveAvailable()).isEqualTo(50);  // back to original
    }

    /**
     * Seeds a Stock row (availableQty) and a RESERVED StockReservation (qty units reserved).
     * After seeding: effectiveAvailable = availableQty - qty.
     */
    private void seedReservation(UUID productId, UUID orderId, int qty, int availableQty) {
        Stock stock = new Stock(productId, availableQty);
        stock.reserve(qty);  // available drops by qty
        stockRepository.save(stock);
        reservationRepository.save(new StockReservation(orderId, productId, qty));
    }
}
