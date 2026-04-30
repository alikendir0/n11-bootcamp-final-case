package com.n11.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.notification.repository.NotificationRepository;
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
 * Idempotency test for OrderCancelledConsumer (NOTIF-01).
 *
 * Delivers the same envelope twice to the consumer via direct method invocation
 * (listener auto-startup=false — Plan 04-02 lesson: bypasses AMQP delivery to avoid
 * needing a running broker for idempotency assertions).
 *
 * Asserts: notifications.findByUserId(userId).size() == 1 AND
 *          processedEventRepository.existsById(eventId) AND
 *          processedEventRepository.count() == 1
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
class OrderCancelledConsumerIdempotencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("n11").withUsername("postgres").withPassword("postgres");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path = notification, public");
    }

    @Autowired OrderCancelledConsumer consumer;
    @Autowired ProcessedEventRepository processedEventRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired ObjectMapper objectMapper;

    @Test
    void duplicateEvent_writesNotificationExactlyOnce_processedEventsCountIs1() throws Exception {
        UUID userId  = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        String envelopeJson = """
            {"eventId":"%s","eventType":"order.cancelled","eventVersion":1,
             "occurredAt":"%s","correlationId":"%s","causationId":null,
             "producer":"order-service",
             "payload":{"orderId":"%s","userId":"%s","reason":"USER_CANCELLED"}}
            """.formatted(eventId, Instant.now(), UUID.randomUUID(), orderId, userId);
        Message m = new Message(envelopeJson.getBytes(), new MessageProperties());

        // Deliver TWICE (simulating RabbitMQ at-least-once redelivery)
        consumer.handleOrderCancelled(m);
        consumer.handleOrderCancelled(m);

        // Assert exactly one notification row for this user (idempotency invariant)
        assertThat(notificationRepository.findByUserId(userId)).hasSize(1);
        assertThat(notificationRepository.findByUserId(userId).get(0).getEventType())
            .isEqualTo("order.cancelled");
        // Assert exactly one processed_events row for this eventId
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }
}
