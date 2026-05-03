package com.n11.infratests.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end saga publishing test: real Testcontainers Postgres + RabbitMQ, real AMQP delivery.
 *
 * <p>Boots only payment-service. Publishes a synthetic stock.reserved envelope to
 * {@code inventory.tx/stock.reserved}; the payment-service StockReservedConsumer fires,
 * persists a payments row, writes an outbox row, and the (in-process) PaymentOutboxPoller
 * publishes payment.completed to {@code payments.tx/payment.completed} within ~15s.
 *
 * <p>Asserts: a sniffer queue bound to {@code payments.tx/payment.completed} receives a
 * message whose body contains the orderId and eventType=payment.completed.
 *
 * <p>D-09 invariant: messageId on the published AMQP message equals envelope.eventId.
 *
 * <p>This complements the per-service tests (Plans 05-02, 05-03, 05-04) that use direct
 * consumer invocation — this test exercises the actual RabbitMQ wiring and the outbox
 * poller's AbstractOutboxPoller publish path, including MessageProperties.messageId injection.
 *
 * <p>Phase 5 SC-2 proof: stock.reserved → payment-service consumes → payment.completed published.
 */
@SpringBootTest(classes = PaymentServiceTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.config.import=",             // override the configserver import from application.yml
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "eureka.client.enabled=false",
        "mock.payment.delay-ms=0",           // skip the sleep so the Awaitility window stays tight
        "spring.rabbitmq.listener.simple.auto-startup=true" // override application-test.yml false
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class SagaHappyPathE2ETest {

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

    private static final String SNIFFER_QUEUE = "test.sniffer.payment-completed";

    @BeforeEach
    void setupSnifferQueue() {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        // I2 — idempotently re-declare inventory.tx in case payment-service's PaymentRabbitConfig
        // ran first and already created the exchange. Spring AMQP's declareExchange is idempotent
        // by AMQP semantics, but doing it here explicitly removes a flake mode where the test
        // publishes to inventory.tx before payment-service finishes initializing its exchange beans.
        TopicExchange inventoryTx = ExchangeBuilder.topicExchange("inventory.tx").durable(true).build();
        admin.declareExchange(inventoryTx);
        TopicExchange paymentsTx = ExchangeBuilder.topicExchange("payments.tx").durable(true).build();
        admin.declareExchange(paymentsTx);
        // Non-durable, NOT auto-delete: avoids queue deletion between Awaitility poll iterations.
        // An auto-delete queue would be removed when RabbitTemplate.receive()'s internal consumer
        // unsubscribes after each 100ms timeout, causing 404 on the next poll.
        Queue sniffer = QueueBuilder.nonDurable(SNIFFER_QUEUE).build();
        admin.declareQueue(sniffer);
        admin.declareBinding(BindingBuilder.bind(sniffer).to(paymentsTx).with("payment.completed"));
    }

    @Test
    void publishingStockReserved_yieldsPaymentCompletedOnPaymentsTx_within15s() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID correlationId = orderId;

        String envelope = """
            {"eventId":"%s","eventType":"stock.reserved","eventVersion":1,
             "occurredAt":"%s","correlationId":"%s","causationId":null,"producer":"inventory-service",
             "payload":{"orderId":"%s","reservationId":"%s","reservedItems":[{"productId":"%s","qty":2}],"totalAmount":150.00}}
            """.formatted(eventId, Instant.now(), correlationId, orderId, UUID.randomUUID(), UUID.randomUUID());

        // Publish to inventory.tx with the required messageId property (RabbitRetryConfig.messageKeyGenerator enforces).
        MessageProperties props = new MessageProperties();
        props.setMessageId(eventId.toString());
        props.setContentType("application/json");
        props.setCorrelationId(correlationId.toString());
        Message m = new Message(envelope.getBytes(), props);
        rabbitTemplate.send("inventory.tx", "stock.reserved", m);

        // Awaitility: poll sniffer queue until payment.completed message arrives (outbox poller fires every 5s)
        await().atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                Message received = rabbitTemplate.receive(SNIFFER_QUEUE, 100);
                assertThat(received).isNotNull();
                JsonNode env = objectMapper.readTree(new String(received.getBody()));
                assertThat(env.path("eventType").asText()).isEqualTo("payment.completed");
                assertThat(env.path("payload").path("orderId").asText()).isEqualTo(orderId.toString());
                // D-09 invariant: messageId must equal envelope.eventId
                String messageId = received.getMessageProperties().getMessageId();
                String envelopeEventId = env.path("eventId").asText();
                assertThat(messageId).isEqualTo(envelopeEventId);
            });
    }
}
