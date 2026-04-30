package com.n11.infratests.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.notification.domain.Notification;
import com.n11.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
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
 * QUAL-04 saga closure: order.confirmed published to orders.tx → notification-service
 * OrderConfirmedConsumer fires → notifications row persisted within 10s.
 *
 * <p>Complements {@link SagaHappyPathE2ETest} (which proves the
 * order.created → stock.reserved → payment.completed segment of the chain).
 * This test proves the final hop: order.confirmed → notification logged.
 *
 * <p>Boots {@link NotificationServiceTestConfig} (NOT NotificationServiceApplication)
 * to keep the Spring context narrow on the multi-service infra-tests classpath
 * (Plan 05-04 STATE.md decision).
 *
 * <p>D-09 invariant: messageId on the published AMQP message equals envelope.eventId.
 */
@SpringBootTest(classes = NotificationServiceTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.config.import=",
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "eureka.client.enabled=false",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/notification",
        "spring.flyway.schemas=notification",
        "spring.flyway.default-schema=notification",
        "spring.flyway.create-schemas=true",
        "spring.jpa.properties.hibernate.default_schema=notification",
        "spring.datasource.hikari.connection-init-sql=SET search_path = notification, public",
        "spring.rabbitmq.listener.simple.auto-startup=true"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class QualFourSagaNotificationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("n11").withUsername("postgres").withPassword("postgres");

    @Container @ServiceConnection
    static RabbitMQContainer RABBIT = new RabbitMQContainer(
        DockerImageName.parse("rabbitmq:3.13-management"));   // Plan 04-02 lesson — NOT 4.x

    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired ConnectionFactory connectionFactory;
    @Autowired NotificationRepository notificationRepository;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void declareExchangesIdempotently() {
        // Defensive idempotent re-declaration — NotificationRabbitConfig should already
        // declare orders.tx via ordersExchangeForNotification(), but a second declare
        // is harmless and protects against a startup race where the test publishes
        // before the listener container finishes attaching.
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.declareExchange(ExchangeBuilder.topicExchange("orders.tx").durable(true).build());
    }

    @Test
    void orderConfirmedEvent_persistsNotificationRow_within10s() throws Exception {
        UUID orderId       = UUID.randomUUID();
        UUID userId        = UUID.randomUUID();
        UUID eventId       = UUID.randomUUID();
        UUID correlationId = orderId;

        String envelope = """
            {"eventId":"%s","eventType":"order.confirmed","eventVersion":1,
             "occurredAt":"%s","correlationId":"%s","causationId":null,"producer":"order-service",
             "payload":{"orderId":"%s","userId":"%s","totalAmount":149.90,
                        "items":[{"productId":"%s","qty":1,"unitPrice":149.90}]}}
            """.formatted(eventId, Instant.now(), correlationId, orderId, userId, UUID.randomUUID());

        MessageProperties props = new MessageProperties();
        props.setMessageId(eventId.toString());          // D-09 invariant
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
                   assertThat(rows.get(0).getCorrelationId()).isEqualTo(correlationId);
               });
    }
}
