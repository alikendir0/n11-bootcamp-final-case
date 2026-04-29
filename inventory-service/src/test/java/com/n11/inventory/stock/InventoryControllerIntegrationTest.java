package com.n11.inventory.stock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full SpringBootTest integration test for StockController.
 * Verifies GET /inventory/{productId} returns StockStateDto with correct Turkish labels.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class InventoryControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("n11")
                    .withUsername("inventory_user")
                    .withPassword("test-password");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.0-management"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.schemas", () -> "inventory");
        registry.add("spring.flyway.default-schema", () -> "inventory");
        registry.add("spring.flyway.create-schemas", () -> "true");
        registry.add("spring.flyway.placeholders.schema", () -> "inventory");
        registry.add("spring.flyway.placeholders.flyway.schema", () -> "inventory");
    }

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    StockRepository stockRepository;

    @Test
    void getStockState_returnsOk_forExistingProduct() {
        UUID productId = UUID.randomUUID();
        stockRepository.save(new Stock(productId, 20, 5));

        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/inventory/" + productId, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("stockState"))
                .isIn("STOKTA", "SON_URUN", "TUKENDI");
        assertThat(response.getBody().get("stockStateLabel")).isNotNull();
    }

    @Test
    void getStockState_returnsOk_forUnknownProduct() {
        UUID unknownId = UUID.randomUUID();

        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/inventory/" + unknownId, Map.class);

        // Unknown product returns TUKENDI (not 404) — graceful degradation
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("stockState")).isEqualTo("TUKENDI");
    }

    @Test
    void getStockState_stokta_whenAboveThreshold() {
        UUID productId = UUID.randomUUID();
        stockRepository.save(new Stock(productId, 50, 5));

        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/inventory/" + productId, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("stockState")).isEqualTo("STOKTA");
        assertThat(response.getBody().get("stockStateLabel")).isEqualTo("Stokta");
    }

    @Test
    void getStockState_tukendi_whenZeroQty() {
        UUID productId = UUID.randomUUID();
        stockRepository.save(new Stock(productId, 0, 5));

        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/inventory/" + productId, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("stockState")).isEqualTo("TUKENDI");
        assertThat(response.getBody().get("stockStateLabel")).isEqualTo("Tükendi");
    }
}
