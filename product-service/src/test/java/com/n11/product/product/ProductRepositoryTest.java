package com.n11.product.product;

import com.n11.product.category.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @DataJpaTest slice with Testcontainers Postgres. Verifies pagination + sort
 * over the seeded data (V3__seed_products.sql runs in test against the container).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class ProductRepositoryTest {

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
    void paginationDefaultPageZeroSizeTwentyReturnsTwentyProducts() {
        Page<Product> page = productRepository.search(null, null, PageRequest.of(0, 20));
        assertThat(page.getNumber()).isEqualTo(0);
        assertThat(page.getSize()).isEqualTo(20);
        assertThat(page.getContent()).hasSize(20);
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(50L);
    }

    @Test
    void sortByPriceGrossAsc() {
        Page<Product> page = productRepository.search(
                null, null, PageRequest.of(0, 60, Sort.by(Sort.Direction.ASC, "price_gross")));
        BigDecimal prev = BigDecimal.ZERO.subtract(BigDecimal.ONE);
        for (Product p : page.getContent()) {
            assertThat(p.getPriceGross()).isGreaterThanOrEqualTo(prev);
            prev = p.getPriceGross();
        }
    }

    @Test
    void sortByCreatedAtDescIsDefaultOrder() {
        Page<Product> page = productRepository.search(
                null, null, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "created_at")));
        assertThat(page.getContent()).hasSize(5);
        assertThat(page.getContent().get(0).getCreatedAt())
                .isAfterOrEqualTo(page.getContent().get(4).getCreatedAt());
    }

    @Test
    void categoriesSeedHasEightTopLevelEntries() {
        assertThat(categoryRepository.findByParentIsNullOrderBySortOrderAsc()).hasSize(8);
    }
}
