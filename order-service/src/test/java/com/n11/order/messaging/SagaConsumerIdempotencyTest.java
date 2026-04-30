package com.n11.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.order.clients.CartClient;
import com.n11.order.clients.IdentityClient;
import com.n11.order.clients.ProductClient;
import com.n11.order.order.Order;
import com.n11.order.order.OrderItem;
import com.n11.order.order.OrderItemRepository;
import com.n11.order.order.OrderRepository;
import com.n11.order.order.OrderStatus;
import com.n11.order.outbox.OrderOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.n11.order.OrderServiceApplication.class,
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
class SagaConsumerIdempotencyTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("n11").withUsername("postgres").withPassword("postgres");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path = orders, public");
    }

    @Autowired StockReservedConsumer stockReservedConsumer;
    @Autowired PaymentCompletedConsumer paymentCompletedConsumer;
    @Autowired StockReserveFailedConsumer stockReserveFailedConsumer;
    @Autowired PaymentFailedConsumer paymentFailedConsumer;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired ProcessedEventRepository processedEventsRepository;
    @Autowired OrderOutboxRepository outboxRepository;
    @MockBean CartClient cartClient;
    @MockBean ProductClient productClient;
    @MockBean IdentityClient identityClient;

    @Test
    void stockReserved_movesPendingToStockReserved_redeliveryIsNoop() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        seedPendingOrder(orderId, userId, new BigDecimal("200.00"));

        String envelopeJson = """
            {"eventId":"%s","eventType":"stock.reserved","eventVersion":1,
             "occurredAt":"%s","correlationId":"%s","causationId":null,"producer":"inventory-service",
             "payload":{"orderId":"%s","reservationId":"%s","reservedItems":[{"productId":"%s","qty":2}]}}
            """.formatted(eventId, Instant.now(), orderId, orderId, UUID.randomUUID(), UUID.randomUUID());

        Message m = new Message(envelopeJson.getBytes(), new MessageProperties());
        stockReservedConsumer.handleStockReserved(m);
        stockReservedConsumer.handleStockReserved(m);  // redelivery

        assertThat(orderRepository.findById(orderId)).isPresent()
            .get().extracting(Order::getStatus).isEqualTo(OrderStatus.STOCK_RESERVED);
        assertThat(processedEventsRepository.existsById(eventId)).isTrue();
        // Count per specific eventId must be 1 (not total count — tests share DB context)
        assertThat(processedEventsRepository.findAll().stream()
            .filter(e -> e.getEventId().equals(eventId)).count()).isEqualTo(1);
    }

    @Test
    void paymentCompleted_movesStockReservedToConfirmed_writesOrderConfirmedOutbox() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        seedPendingOrder(orderId, userId, new BigDecimal("100.00"));
        // Move to STOCK_RESERVED first
        Order o = orderRepository.findById(orderId).orElseThrow();
        o.setStatus(OrderStatus.STOCK_RESERVED);
        orderRepository.save(o);

        String env = """
            {"eventId":"%s","eventType":"payment.completed","eventVersion":1,
             "occurredAt":"%s","correlationId":"%s","causationId":null,"producer":"payment-service",
             "payload":{"orderId":"%s","paymentId":"%s","iyzicoPaymentId":"mock-1","amount":100.00,"currency":"TRY"}}
            """.formatted(eventId, Instant.now(), orderId, orderId, UUID.randomUUID());

        Message m = new Message(env.getBytes(), new MessageProperties());
        paymentCompletedConsumer.handlePaymentCompleted(m);

        Order after = orderRepository.findById(orderId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(outboxRepository.findAll().stream().anyMatch(e -> "order.confirmed".equals(e.getEventType())))
            .isTrue();
    }

    @Test
    void stockReserveFailed_movesPendingToCancelled_writesOrderCancelledOutbox() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        seedPendingOrder(orderId, userId, new BigDecimal("50.00"));

        String env = """
            {"eventId":"%s","eventType":"stock.reserve_failed","eventVersion":1,
             "occurredAt":"%s","correlationId":"%s","causationId":null,"producer":"inventory-service",
             "payload":{"orderId":"%s","reason":"INSUFFICIENT_STOCK","failedItems":[{"productId":"%s","requestedQty":2,"availableQty":0}]}}
            """.formatted(eventId, Instant.now(), orderId, orderId, UUID.randomUUID());

        Message m = new Message(env.getBytes(), new MessageProperties());
        stockReserveFailedConsumer.handleStockReserveFailed(m);

        Order after = orderRepository.findById(orderId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(after.getCancelReason()).isEqualTo("OUT_OF_STOCK");
        assertThat(outboxRepository.findAll().stream().anyMatch(e -> "order.cancelled".equals(e.getEventType())))
            .isTrue();
    }

    @Test
    void paymentFailed_movesStockReservedToCancelled_reasonPaymentDeclined() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        seedPendingOrder(orderId, userId, new BigDecimal("75.00"));
        Order o = orderRepository.findById(orderId).orElseThrow();
        o.setStatus(OrderStatus.STOCK_RESERVED);
        orderRepository.save(o);

        String env = """
            {"eventId":"%s","eventType":"payment.failed","eventVersion":1,
             "occurredAt":"%s","correlationId":"%s","causationId":null,"producer":"payment-service",
             "payload":{"orderId":"%s","paymentId":"%s","reason":"DECLINED","errorCode":"E50"}}
            """.formatted(eventId, Instant.now(), orderId, orderId, UUID.randomUUID());

        Message m = new Message(env.getBytes(), new MessageProperties());
        paymentFailedConsumer.handlePaymentFailed(m);

        Order after = orderRepository.findById(orderId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(after.getCancelReason()).isEqualTo("PAYMENT_DECLINED");
    }

    private void seedPendingOrder(UUID orderId, UUID userId, BigDecimal total) {
        UUID idempotencyKey = UUID.randomUUID();
        Order order = new Order(orderId, userId, OrderStatus.PENDING, total, "TRY", orderId, idempotencyKey);
        orderRepository.save(order);
        orderItemRepository.save(new OrderItem(orderId, UUID.randomUUID(), "Test", 1, total));
    }
}
