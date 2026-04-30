package com.n11.cart.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.cart.cart.Cart;
import com.n11.cart.cart.CartItemRepository;
import com.n11.cart.cart.CartRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.n11.cart.CartServiceApplication.class,
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
class OrderConfirmedConsumerIdempotencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("n11").withUsername("postgres").withPassword("postgres");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path = cart, public");
    }

    @Autowired OrderConfirmedConsumer consumer;
    @Autowired ProcessedEventRepository processedEventsRepository;
    @Autowired CartRepository cartRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired ObjectMapper objectMapper;

    @Test
    void duplicateEvent_clearsCartExactlyOnce_processedEventsCountIs1() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        // Seed cart with 2 items
        cartRepository.save(new Cart(userId));
        cartItemRepository.upsertAddQty(userId, UUID.randomUUID(), 1, new BigDecimal("10.00"), "A", null);
        cartItemRepository.upsertAddQty(userId, UUID.randomUUID(), 2, new BigDecimal("20.00"), "B", null);
        assertThat(cartItemRepository.findByUserIdOrderByAddedAt(userId)).hasSize(2);

        // Build envelope JSON matching order-confirmed.schema.json
        String envelopeJson = """
            {"eventId":"%s","eventType":"order.confirmed","eventVersion":1,
             "occurredAt":"%s","correlationId":"%s","causationId":null,
             "producer":"order-service",
             "payload":{"orderId":"%s","userId":"%s","totalAmount":50.00,
               "items":[{"productId":"%s","qty":1,"unitPrice":10.00}]}}
            """.formatted(eventId, Instant.now(), orderId, orderId, userId, UUID.randomUUID());

        Message m = new Message(envelopeJson.getBytes(), new MessageProperties());

        // Deliver TWICE (simulating RabbitMQ at-least-once redelivery)
        consumer.handleOrderConfirmed(m);
        consumer.handleOrderConfirmed(m);

        // Assert cart cleared
        assertThat(cartItemRepository.findByUserIdOrderByAddedAt(userId)).isEmpty();
        // Assert processed_events has exactly 1 row for this eventId (idempotency inbox)
        assertThat(processedEventsRepository.existsById(eventId)).isTrue();
        assertThat(processedEventsRepository.count()).isEqualTo(1);
    }
}
