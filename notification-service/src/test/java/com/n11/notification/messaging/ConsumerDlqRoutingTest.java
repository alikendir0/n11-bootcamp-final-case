package com.n11.notification.messaging;

import com.n11.notification.config.NotificationRabbitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * DLQ routing test proving saga-contracts.md §3 DLX/DLQ wiring is live (NOTIF-01).
 *
 * Sends a malformed envelope (invalid JSON) to the {@code orders.tx / order.confirmed} route.
 * OrderConfirmedConsumer deserializes it, throws {@code AmqpRejectAndDontRequeueException}
 * (immediate DLQ — no retries for unrecoverable deserialization errors per AMQP reject semantics),
 * and the message must appear on {@code notify.q.order-confirmed.dlq} within ~5s.
 *
 * <p>Scope: ONE consumer queue is sufficient to prove the wiring. The same
 * x-dead-letter-exchange / x-dead-letter-routing-key pattern is used for all 4 queues
 * (see NotificationRabbitConfig), so the contract is verified generically.
 *
 * <p>Note on retry behaviour: AmqpRejectAndDontRequeueException bypasses the 3-attempt
 * StatefulRetryInterceptor (which only handles transient/non-AmqpReject exceptions). A
 * malformed envelope lands on the DLQ on the FIRST delivery attempt — sub-second in practice.
 * The 8s Awaitility budget is conservative to absorb container startup latency.
 *
 * <p>Uses {@code rabbitmq:3.13-management} (NOT 4.x — Plan 04-02 lesson).
 * Listener {@code auto-startup=true} so the real @RabbitListener fires via AMQP delivery,
 * exercising the actual reject-and-don't-requeue → DLQ pipeline end-to-end.
 */
@SpringBootTest(classes = com.n11.notification.NotificationServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "eureka.client.enabled=false",
        // listener auto-startup=true so real AMQP delivery exercises the DLQ pipeline
        "spring.rabbitmq.listener.simple.auto-startup=true"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class ConsumerDlqRoutingTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("n11").withUsername("postgres").withPassword("postgres");

    @Container
    @ServiceConnection
    static RabbitMQContainer RABBIT = new RabbitMQContainer(
        DockerImageName.parse("rabbitmq:3.13-management"));   // Plan 04-02 lesson — NOT 4.x

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path = notification, public");
    }

    @Autowired RabbitTemplate rabbitTemplate;

    @Test
    void malformedEnvelope_routesToDlq_within5s() {
        String malformed = "this is not valid json";
        MessageProperties props = new MessageProperties();
        props.setMessageId(UUID.randomUUID().toString());
        props.setContentType("application/json");
        Message m = new Message(malformed.getBytes(StandardCharsets.UTF_8), props);

        // Publish to orders.tx with order.confirmed routing key.
        // OrderConfirmedConsumer receives it, fails deserialization, throws
        // AmqpRejectAndDontRequeueException → SimpleMessageListenerContainer routes
        // message to the DLQ via x-dead-letter-* arguments declared in NotificationRabbitConfig.
        rabbitTemplate.send("orders.tx", "order.confirmed", m);

        await().atMost(Duration.ofSeconds(8))
               .pollInterval(Duration.ofMillis(300))
               .untilAsserted(() -> {
                   // Read from the DLQ directly — it's a durable queue declared by NotificationRabbitConfig.
                   // No sniffer queue needed; DLQ name is derived from the main queue name + ".dlq".
                   Message dlqMsg = rabbitTemplate.receive(
                       NotificationRabbitConfig.QUEUE_NOTIFY_ORDER_CONFIRMED + ".dlq", 100);
                   assertThat(dlqMsg).isNotNull();
                   assertThat(new String(dlqMsg.getBody(), StandardCharsets.UTF_8)).isEqualTo(malformed);
               });
    }
}
