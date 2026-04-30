package com.n11.order.order;

import com.n11.order.clients.CartClient;
import com.n11.order.clients.IdentityClient;
import com.n11.order.clients.ProductClient;
import com.n11.order.outbox.OrderOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class OrderCancelTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("n11").withUsername("postgres").withPassword("postgres");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path = orders, public");
    }

    @Autowired OrderCancellationService orderCancellationService;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderOutboxRepository outboxRepository;
    @MockBean CartClient cartClient;
    @MockBean ProductClient productClient;
    @MockBean IdentityClient identityClient;

    @Test
    void cancel_pendingOrder_succeeds_writesOutbox() {
        UUID userId = UUID.randomUUID();
        UUID orderId = seed(userId, OrderStatus.PENDING);

        orderCancellationService.cancel(userId, orderId);

        Order after = orderRepository.findById(orderId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(after.getCancelReason()).isEqualTo("USER_CANCELLED");
        assertThat(outboxRepository.findAll().stream().anyMatch(e -> "order.cancelled".equals(e.getEventType())))
            .isTrue();
    }

    @Test
    void cancel_confirmedOrder_throws409() {
        UUID userId = UUID.randomUUID();
        UUID orderId = seed(userId, OrderStatus.CONFIRMED);

        assertThatThrownBy(() -> orderCancellationService.cancel(userId, orderId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void cancel_foreignUsersOrder_returns404() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID orderId = seed(alice, OrderStatus.PENDING);

        assertThatThrownBy(() -> orderCancellationService.cancel(bob, orderId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    private UUID seed(UUID userId, OrderStatus status) {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, userId, status, new BigDecimal("100.00"), "TRY", orderId, UUID.randomUUID());
        orderRepository.save(order);
        return orderId;
    }
}
