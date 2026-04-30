package com.n11.notification.messaging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import com.n11.notification.messaging.payloads.OrderCancelledPayload;
import com.n11.notification.messaging.payloads.OrderConfirmedPayload;
import com.n11.notification.messaging.payloads.PaymentFailedPayload;
import com.n11.notification.messaging.payloads.UserRegisteredPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the NOTIF-02 contract: NotificationService emits exactly one structured INFO log line
 * per processed event with the following required fields:
 * {@code notification.sent recipient=<userId> subject="<turkishSubject>" correlationId=<uuid>
 * eventType=<routingKey> channel=EMAIL}
 *
 * The exact key list is the operator-visible structure. Any drift in NotificationService log format
 * MUST be caught here before reaching production.
 *
 * Implementation uses Logback in-memory ListAppender (zero extra deps — Logback is on classpath
 * via spring-boot-starter-test). Each @BeforeEach resets the appender so each @Test starts fresh.
 */
@SpringBootTest(classes = com.n11.notification.NotificationServiceApplication.class,
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
class NotificationServiceLogTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("n11").withUsername("postgres").withPassword("postgres");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path = notification, public");
    }

    @Autowired NotificationService notificationService;
    @Autowired ObjectMapper objectMapper;

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void attachAppender() {
        ch.qos.logback.classic.Logger nsLogger = (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(NotificationService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        nsLogger.addAppender(listAppender);
        nsLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void detachAppender() {
        ch.qos.logback.classic.Logger nsLogger = (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(NotificationService.class);
        nsLogger.detachAppender(listAppender);
    }

    @Test
    void orderConfirmed_emitsStructuredLogLine() {
        UUID eventId       = UUID.randomUUID();
        UUID orderId       = UUID.randomUUID();
        UUID userId        = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        Envelope env = new Envelope(
            eventId.toString(), "order.confirmed", 1,
            Instant.now(), correlationId.toString(), null, "order-service", null);
        OrderConfirmedPayload payload = new OrderConfirmedPayload(
            orderId, userId, new BigDecimal("199.90"),
            List.of(new OrderConfirmedPayload.Item(UUID.randomUUID(), 1, new BigDecimal("199.90"))));

        notificationService.handleOrderConfirmed(eventId, env, payload);

        String formatted = capturedInfoMessages();
        assertThat(formatted).contains("notification.sent");
        assertThat(formatted).contains("recipient=" + userId);
        assertThat(formatted).contains("subject=\"Siparişiniz onaylandı\"");
        assertThat(formatted).contains("correlationId=" + correlationId);
        assertThat(formatted).contains("eventType=order.confirmed");
        assertThat(formatted).contains("channel=EMAIL");
    }

    @Test
    void orderCancelled_emitsStructuredLogLine() {
        UUID eventId       = UUID.randomUUID();
        UUID orderId       = UUID.randomUUID();
        UUID userId        = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        Envelope env = new Envelope(
            eventId.toString(), "order.cancelled", 1,
            Instant.now(), correlationId.toString(), null, "order-service", null);
        OrderCancelledPayload payload = new OrderCancelledPayload(orderId, userId, "USER_CANCELLED");

        notificationService.handleOrderCancelled(eventId, env, payload);

        String formatted = capturedInfoMessages();
        assertThat(formatted).contains("notification.sent");
        assertThat(formatted).contains("recipient=" + userId);
        assertThat(formatted).contains("subject=\"Siparişiniz iptal edildi\"");
        assertThat(formatted).contains("correlationId=" + correlationId);
        assertThat(formatted).contains("eventType=order.cancelled");
        assertThat(formatted).contains("channel=EMAIL");
    }

    @Test
    void paymentFailed_emitsStructuredLogLine() {
        UUID eventId       = UUID.randomUUID();
        UUID orderId       = UUID.randomUUID();
        UUID paymentId     = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        Envelope env = new Envelope(
            eventId.toString(), "payment.failed", 1,
            Instant.now(), correlationId.toString(), null, "payment-service", null);
        // payment.failed has no userId — orderId stored in notifications.user_id (see PaymentFailedConsumerIdempotencyTest)
        PaymentFailedPayload payload = new PaymentFailedPayload(orderId, paymentId, "DECLINED", "10051");

        notificationService.handlePaymentFailed(eventId, env, payload);

        String formatted = capturedInfoMessages();
        assertThat(formatted).contains("notification.sent");
        assertThat(formatted).contains("recipient=" + orderId);
        assertThat(formatted).contains("subject=\"Ödemeniz alınamadı\"");
        assertThat(formatted).contains("correlationId=" + correlationId);
        assertThat(formatted).contains("eventType=payment.failed");
        assertThat(formatted).contains("channel=EMAIL");
    }

    @Test
    void userRegistered_emitsStructuredLogLine() {
        UUID eventId       = UUID.randomUUID();
        UUID userId        = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        Envelope env = new Envelope(
            eventId.toString(), "user.registered", 1,
            Instant.now(), correlationId.toString(), null, "identity-service", null);
        UserRegisteredPayload payload = new UserRegisteredPayload(
            userId, "alice@buyer.example.com", "Ali Kendir", "2026-04-30T12:00:00Z");

        notificationService.handleUserRegistered(eventId, env, payload);

        String formatted = capturedInfoMessages();
        assertThat(formatted).contains("notification.sent");
        assertThat(formatted).contains("recipient=" + userId);
        assertThat(formatted).contains("subject=\"Hoş geldiniz!\"");
        assertThat(formatted).contains("correlationId=" + correlationId);
        assertThat(formatted).contains("eventType=user.registered");
        assertThat(formatted).contains("channel=EMAIL");
    }

    private String capturedInfoMessages() {
        return listAppender.list.stream()
            .filter(e -> e.getLevel() == Level.INFO)
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.joining("\n"));
    }
}
