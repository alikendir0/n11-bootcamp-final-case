package com.n11.product.seed;

import com.n11.product.category.CategoryRepository;
import com.n11.product.product.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts PROD-09 (>=50 Turkish products) and PROD-03 (8 top-level categories).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class SeedDataAssertionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("n11")
                    .withUsername("product_user")
                    .withPassword("test-password");

    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;

    @Test
    void atLeastFiftyProductsSeeded() {
        assertThat(productRepository.count()).isGreaterThanOrEqualTo(50L);
    }

    @Test
    void eightTopLevelCategoriesSeeded() {
        assertThat(categoryRepository.findByParentIsNullOrderBySortOrderAsc()).hasSize(8);
    }
}
