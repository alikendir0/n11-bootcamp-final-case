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
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Idempotency test for PaymentFailedConsumer (NOTIF-01).
 *
 * Delivers the same envelope twice to the consumer via direct method invocation
 * (listener auto-startup=false — Plan 04-02 lesson).
 *
 * Trade-off note: payment.failed has NO userId field in its payload (per payment-failed.schema.json).
 * NotificationService stores orderId in notifications.user_id for this event type as a correlation
 * key so that findByUserId can be used uniformly. This test uses orderId as the lookup key.
 *
 * Asserts: notifications.findByUserId(orderId).size() == 1 AND
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
class PaymentFailedConsumerIdempotencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("n11").withUsername("postgres").withPassword("postgres");

    @Container
    @ServiceConnection
    static RabbitMQContainer RABBIT = new RabbitMQContainer(
        DockerImageName.parse("rabbitmq:3.13-management"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path = notification, public");
    }

    @Autowired PaymentFailedConsumer consumer;
    @Autowired ProcessedEventRepository processedEventRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired ObjectMapper objectMapper;

    @Test
    void duplicateEvent_writesNotificationExactlyOnce_processedEventsCountIs1() throws Exception {
        // payment.failed payload has no userId — orderId is stored in notifications.user_id
        // by NotificationService.handlePaymentFailed as the correlation key for this event type.
        UUID orderId    = UUID.randomUUID();
        UUID paymentId  = UUID.randomUUID();
        UUID eventId    = UUID.randomUUID();

        String envelopeJson = """
            {"eventId":"%s","eventType":"payment.failed","eventVersion":1,
             "occurredAt":"%s","correlationId":"%s","causationId":null,
             "producer":"payment-service",
             "payload":{"orderId":"%s","paymentId":"%s","reason":"DECLINED","errorCode":"10051"}}
            """.formatted(eventId, Instant.now(), UUID.randomUUID(), orderId, paymentId);
        Message m = new Message(envelopeJson.getBytes(), new MessageProperties());

        // Deliver TWICE (simulating RabbitMQ at-least-once redelivery)
        consumer.handlePaymentFailed(m);
        consumer.handlePaymentFailed(m);

        // Trade-off: payment.failed has no userId; NotificationService stores orderId in user_id column
        // for this event type so lookup uses orderId as key.
        assertThat(notificationRepository.findByUserId(orderId)).hasSize(1);
        assertThat(notificationRepository.findByUserId(orderId).get(0).getEventType())
            .isEqualTo("payment.failed");
        // Assert exactly one processed_events row for this eventId
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }
}
