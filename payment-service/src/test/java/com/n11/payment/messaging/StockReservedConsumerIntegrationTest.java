package com.n11.payment.messaging;

import com.n11.payment.outbox.PaymentOutboxRepository;
import com.n11.payment.iyzico.IyzicoCheckoutClient;
import com.n11.payment.iyzico.IyzicoCheckoutResult;
import com.n11.payment.order.OrderPaymentContext;
import com.n11.payment.order.OrderPaymentContextClient;
import com.n11.payment.payment.PaymentRepository;
import com.n11.payment.payment.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration test for {@link StockReservedConsumer}: Testcontainers Postgres,
 * direct consumer invocation (no AMQP broker needed for per-service test).
 *
 * <p>Asserts:
 * 1. One PENDING payments row written with the hosted Checkout Form token/link.
 * 2. No payment.completed outbox row is written before the Iyzico callback.
 * 3. Redelivery (same eventId/orderId) reuses the active checkout — idempotency (ARCH-07/D-09).
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

    @Container
    static GenericContainer<?> RABBIT = new GenericContainer<>(DockerImageName.parse("rabbitmq:3.13-management"))
        .withExposedPorts(5672);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path = payment, public");
        r.add("spring.rabbitmq.host", RABBIT::getHost);
        r.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        r.add("spring.rabbitmq.username", () -> "guest");
        r.add("spring.rabbitmq.password", () -> "guest");
    }

    @Autowired StockReservedConsumer consumer;
    @Autowired PaymentRepository paymentRepository;
    @Autowired ProcessedEventRepository processedEventsRepository;
    @Autowired PaymentOutboxRepository outboxRepository;
    @MockBean OrderPaymentContextClient orderPaymentContextClient;
    @MockBean IyzicoCheckoutClient iyzicoCheckoutClient;
    @MockBean RabbitTemplate rabbitTemplate;

    @Test
    void stockReserved_initializesPendingCheckout_andRedeliveryReusesActiveCheckout() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();
        when(orderPaymentContextClient.getPaymentContext(orderId)).thenReturn(context(orderId));
        when(iyzicoCheckoutClient.initialize(any(IyzicoCheckoutResult.CheckoutInitializationCommand.class)))
            .thenReturn(new IyzicoCheckoutResult.InitializedCheckout(
                "token-123", "https://sandbox.iyzico/pay/token-123", "success", null, null));

        String env = stockReservedEnvelope(orderId, eventId);
        String secondEnv = stockReservedEnvelope(orderId, secondEventId);

        consumer.handleStockReserved(new Message(env.getBytes(), new MessageProperties()));
        consumer.handleStockReserved(new Message(env.getBytes(), new MessageProperties()));  // same delivery — no-op
        consumer.handleStockReserved(new Message(secondEnv.getBytes(), new MessageProperties())); // same order — reuse active PENDING

        var payments = paymentRepository.findByOrderId(orderId);
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payments.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("250.50"));
        assertThat(payments.get(0).getIyzicoToken()).isEqualTo("token-123");
        assertThat(payments.get(0).getPaymentPageUrl()).isEqualTo("https://sandbox.iyzico/pay/token-123");
        assertThat(payments.get(0).getExpiresAt()).isNotNull();

        long completedOutboxCount = outboxRepository.findAll().stream()
            .filter(e -> "payment.completed".equals(e.getEventType()))
            .filter(e -> e.getPayload().contains(orderId.toString()))
            .count();
        assertThat(completedOutboxCount).isZero();

        assertThat(processedEventsRepository.existsById(eventId)).isTrue();
        assertThat(processedEventsRepository.existsById(secondEventId)).isTrue();
        verify(orderPaymentContextClient, times(1)).getPaymentContext(orderId);
        verify(iyzicoCheckoutClient, times(1)).initialize(any(IyzicoCheckoutResult.CheckoutInitializationCommand.class));
    }

    private static String stockReservedEnvelope(UUID orderId, UUID eventId) {
        return """
            {"eventId":"%s","eventType":"stock.reserved","eventVersion":1,
             "occurredAt":"%s","correlationId":"%s","causationId":null,"producer":"inventory-service",
             "payload":{"orderId":"%s","reservationId":"%s","reservedItems":[{"productId":"%s","qty":2}],"totalAmount":250.50}}
            """.formatted(eventId, Instant.now(), orderId, orderId, UUID.randomUUID(), UUID.randomUUID());
    }

    private static OrderPaymentContext context(UUID orderId) {
        return new OrderPaymentContext(
            orderId,
            UUID.randomUUID(),
            new BigDecimal("250.50"),
            "TRY",
            Instant.parse("2026-04-30T10:15:30Z"),
            new OrderPaymentContext.ShippingAddress(
                "Ayşe Yılmaz", "+90 535 000 00 00", "İstanbul", "Kadıköy", "Merkez Mah.", "Test Sk. No:1", "34000", "Ev"),
            List.of(new OrderPaymentContext.Item(UUID.randomUUID(), "Telefon", 2, new BigDecimal("125.25"))));
    }
}
