package com.n11.inventory.stock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @DataJpaTest slice with Testcontainers Postgres.
 * Verifies @Version optimistic locking on Stock entity:
 * - Two EntityManagers loading same entity; second save throws ObjectOptimisticLockingFailureException
 * - Version increments by 1 after a successful update
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class StockEntityVersionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("n11")
                    .withUsername("inventory_user")
                    .withPassword("test-password");

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
    StockRepository stockRepository;

    @PersistenceContext
    EntityManager em;

    @Test
    void optimisticLockConflictThrowsException() {
        UUID productId = UUID.randomUUID();
        // Save initial stock (version=0)
        stockRepository.saveAndFlush(new Stock(productId, 10, 5));

        // Load entity A — this will be used for "first" update
        Stock entityA = stockRepository.findById(productId).orElseThrow();
        // Load entity B with the same DB state (version=0) — detach it to simulate stale context
        Stock entityB = stockRepository.findById(productId).orElseThrow();
        em.detach(entityB);  // B is now detached and will be merged later

        // First update: entityA.reserve(2), flush → DB version becomes 1
        entityA.reserve(2);
        stockRepository.saveAndFlush(entityA);  // version in DB is now 1

        // Second update: try to merge detached entityB which still has version=0
        // Hibernate sees version mismatch (0 != 1) and throws ObjectOptimisticLockingFailureException
        entityB.reserve(3);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            stockRepository.saveAndFlush(entityB);
        });
    }

    @Test
    void versionIncrements() {
        UUID productId = UUID.randomUUID();
        Stock stock = stockRepository.saveAndFlush(new Stock(productId, 20, 5));
        Long initialVersion = stock.getVersion();

        stock.reserve(5);
        stockRepository.saveAndFlush(stock);

        Stock reloaded = stockRepository.findById(productId).orElseThrow();
        assertEquals(initialVersion + 1, reloaded.getVersion(),
                "Version should increment by 1 after a successful update");
    }
}
