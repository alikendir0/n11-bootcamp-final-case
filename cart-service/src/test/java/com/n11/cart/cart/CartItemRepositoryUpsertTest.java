package com.n11.cart.cart;

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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.n11.cart.CartServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "eureka.client.enabled=false",
        "spring.rabbitmq.host=localhost",
        "spring.rabbitmq.port=15672",
        "spring.rabbitmq.listener.simple.auto-startup=false"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class CartItemRepositoryUpsertTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("n11").withUsername("postgres").withPassword("postgres");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path = cart, public");
    }

    @Autowired CartItemRepository cartItemRepository;
    @Autowired CartRepository cartRepository;

    @Test
    void upsertAddQty_sameProductTwice_sumsQty() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        cartRepository.save(new Cart(userId));

        cartItemRepository.upsertAddQty(userId, productId, 2, new BigDecimal("100.00"), "Telefon", null);
        cartItemRepository.upsertAddQty(userId, productId, 3, new BigDecimal("100.00"), "Telefon", null);

        List<CartItem> items = cartItemRepository.findByUserIdOrderByAddedAt(userId);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getQty()).isEqualTo(5);
    }

    @Test
    void deleteLine_removesRow() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        cartRepository.save(new Cart(userId));
        cartItemRepository.upsertAddQty(userId, productId, 1, new BigDecimal("50.00"), "Kitap", null);

        int n = cartItemRepository.deleteLine(userId, productId);

        assertThat(n).isEqualTo(1);
        assertThat(cartItemRepository.findByUserIdOrderByAddedAt(userId)).isEmpty();
    }

    @Test
    void cartPersists_byUserId_acrossLogins_simulatedViaSeparateRead() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        cartRepository.save(new Cart(userId));
        cartItemRepository.upsertAddQty(userId, productId, 4, new BigDecimal("200.00"), "Çanta", "img.jpg");

        // Simulating "log out + log back in" = a separate read by the same user_id
        List<CartItem> items = cartItemRepository.findByUserIdOrderByAddedAt(userId);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getQty()).isEqualTo(4);
        assertThat(items.get(0).getImageUrlSnapshot()).isEqualTo("img.jpg");
    }
}
