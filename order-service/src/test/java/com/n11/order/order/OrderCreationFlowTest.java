package com.n11.order.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.order.clients.AddressSnapshot;
import com.n11.order.clients.CartClient;
import com.n11.order.clients.CartView;
import com.n11.order.clients.IdentityClient;
import com.n11.order.clients.ProductClient;
import com.n11.order.clients.ProductSnapshot;
import com.n11.order.idempotency.OrderIdempotencyKeyRepository;
import com.n11.order.order.dto.CreateOrderRequest;
import com.n11.order.order.dto.OrderResponse;
import com.n11.order.outbox.OrderOutboxRepository;
import org.junit.jupiter.api.Test;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
class OrderCreationFlowTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("n11").withUsername("postgres").withPassword("postgres");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path = orders, public");
    }

    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderIdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired OrderOutboxRepository outboxRepository;
    @Autowired ObjectMapper objectMapper;
    @MockBean CartClient cartClient;
    @MockBean ProductClient productClient;
    @MockBean IdentityClient identityClient;

    @Test
    void postOrders_happyPath_writesOrderItemsAddressIdempotencyKeyAndOutbox() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();

        when(cartClient.getCart(userId)).thenReturn(new CartView(userId,
            List.of(new CartView.Line(productId, "Telefon", null, 2, new BigDecimal("100.00"), new BigDecimal("200.00"))),
            Instant.now()));
        when(productClient.fetchSnapshot(productId)).thenReturn(new ProductSnapshot(productId, "Telefon", new BigDecimal("100.00")));
        when(identityClient.getAddress(addressId, userId)).thenReturn(addressFor(addressId, userId));

        OrderResponse resp = orderService.createOrder(userId, idempotencyKey,
            new CreateOrderRequest(addressId, "CARD"));

        assertThat(resp.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(orderRepository.findById(resp.orderId())).isPresent();
        assertThat(idempotencyKeyRepository.findByIdempotencyKeyAndUserId(idempotencyKey, userId)).isPresent();
        // Outbox row exists for this orderId
        assertThat(outboxRepository.findAll().stream()
            .anyMatch(e -> "order.created".equals(e.getEventType()) && e.getPayload().contains(resp.orderId().toString())))
            .isTrue();
    }

    @Test
    void postOrders_replayWithSameIdempotencyKey_returnsSameOrderId_andCreatesOnlyOneOrder() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();

        when(cartClient.getCart(userId)).thenReturn(new CartView(userId,
            List.of(new CartView.Line(productId, "Çanta", null, 1, new BigDecimal("50.00"), new BigDecimal("50.00"))),
            Instant.now()));
        when(productClient.fetchSnapshot(productId)).thenReturn(new ProductSnapshot(productId, "Çanta", new BigDecimal("50.00")));
        when(identityClient.getAddress(addressId, userId)).thenReturn(addressFor(addressId, userId));

        OrderResponse first = orderService.createOrder(userId, idempotencyKey, new CreateOrderRequest(addressId, "CARD"));
        OrderResponse replay = orderService.createOrder(userId, idempotencyKey, new CreateOrderRequest(addressId, "CARD"));

        assertThat(replay.orderId()).isEqualTo(first.orderId());
        assertThat(orderRepository.findByUserIdOrderByCreatedAtDesc(userId)).hasSize(1);
    }

    @Test
    void postOrders_priceDrift_throwsBeforePersistence() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();

        // Cart snapshot says 100.00, current product price is 80.00 → drift
        when(cartClient.getCart(userId)).thenReturn(new CartView(userId,
            List.of(new CartView.Line(productId, "Kitap", null, 1, new BigDecimal("100.00"), new BigDecimal("100.00"))),
            Instant.now()));
        when(productClient.fetchSnapshot(productId)).thenReturn(new ProductSnapshot(productId, "Kitap", new BigDecimal("80.00")));
        when(identityClient.getAddress(addressId, userId)).thenReturn(addressFor(addressId, userId));

        long before = orderRepository.count();
        assertThatThrownBy(() -> orderService.createOrder(userId, idempotencyKey, new CreateOrderRequest(addressId, "CARD")))
            .isInstanceOf(OrderService.PriceDriftException.class);
        assertThat(orderRepository.count()).isEqualTo(before);  // no order written
    }

    private static AddressSnapshot addressFor(UUID addressId, UUID userId) {
        return new AddressSnapshot(addressId, userId, "Ali Demir", "+905551112233",
            "İstanbul", "Kadıköy", "Caferağa", "Sokak No:1", "34710", "Ev");
    }
}
