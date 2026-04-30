package com.n11.order.order;

import com.n11.order.clients.CartClient;
import com.n11.order.clients.IdentityClient;
import com.n11.order.clients.ProductClient;
import com.n11.order.order.dto.OrderDetailDto;
import com.n11.order.order.dto.OrderListItemDto;
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
import java.util.List;
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
class OrderListingAndDetailTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("n11").withUsername("postgres").withPassword("postgres");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path = orders, public");
    }

    @Autowired OrderQueryService orderQueryService;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired OrderShippingAddressRepository addressRepository;
    @MockBean CartClient cartClient;
    @MockBean ProductClient productClient;
    @MockBean IdentityClient identityClient;

    @Test
    void list_returnsCallerOrdersOnly_sortedByCreatedAtDesc() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        UUID o1 = seed(alice, OrderStatus.PENDING,        new BigDecimal("10.00"));
        Thread.sleep(20);
        UUID o2 = seed(alice, OrderStatus.STOCK_RESERVED, new BigDecimal("20.00"));
        Thread.sleep(20);
        UUID o3 = seed(alice, OrderStatus.CONFIRMED,      new BigDecimal("30.00"));
        seed(bob,  OrderStatus.CONFIRMED,                 new BigDecimal("40.00"));   // foreign user

        List<OrderListItemDto> rows = orderQueryService.listForUser(alice);

        assertThat(rows).extracting(OrderListItemDto::id).containsExactly(o3, o2, o1); // DESC
        assertThat(rows).noneMatch(r -> r.totalAmount().compareTo(new BigDecimal("40.00")) == 0); // bob's order excluded
    }

    @Test
    void detail_returnsCanonicalStatus_cancelReason_andItems() {
        UUID userId = UUID.randomUUID();
        UUID orderId = seed(userId, OrderStatus.CANCELLED, new BigDecimal("99.99"));
        // Set cancel_reason on the seeded order
        Order o = orderRepository.findById(orderId).orElseThrow();
        o.setCancelReason("USER_CANCELLED");
        orderRepository.save(o);

        OrderDetailDto d = orderQueryService.getDetail(userId, orderId);

        assertThat(d.id()).isEqualTo(orderId);
        assertThat(d.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(d.cancelReason()).isEqualTo("USER_CANCELLED");
        assertThat(d.items()).isNotEmpty();
        assertThat(d.shippingAddress()).isNotNull();
        assertThat(d.shippingAddress().il()).isEqualTo("İstanbul");
    }

    @Test
    void detail_returns404_whenOrderBelongsToAnotherUser() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID orderId = seed(alice, OrderStatus.CONFIRMED, new BigDecimal("50.00"));

        assertThatThrownBy(() -> orderQueryService.getDetail(bob, orderId))
            .hasMessageContaining("Sipariş bulunamadı");
    }

    private UUID seed(UUID userId, OrderStatus status, BigDecimal total) {
        UUID orderId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        Order order = new Order(orderId, userId, status, total, "TRY", orderId, idempotencyKey);
        orderRepository.save(order);
        orderItemRepository.save(new OrderItem(orderId, UUID.randomUUID(), "Test", 1, total));
        addressRepository.save(new OrderShippingAddress(orderId, "Ali Demir", "+905551112233",
            "İstanbul", "Kadıköy", "Caferağa", "Sokak No:1", "34710", "Ev"));
        return orderId;
    }
}
