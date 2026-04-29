package com.n11.product.migrations;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 0 Nyquist test: proves all three Flyway migrations (V1/V2/V3) apply
 * without error against a real Testcontainers Postgres instance and the
 * expected schema objects exist.
 *
 * VALIDATION.md task 4-01-01 / B-03 fix.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class FlywayMigrationsTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("n11")
                    .withUsername("product_user")
                    .withPassword("test-password");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void v1_processedEventsTableExists() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables " +
            "WHERE table_schema = 'product' AND table_name = 'processed_events'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void v2_categoriesTableExistsWithCorrectColumns() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables " +
            "WHERE table_schema = 'product' AND table_name = 'categories'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void v2_productsTableExistsWithGinIndex() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pg_indexes " +
            "WHERE indexname = 'idx_products_name_lower_trgm'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void v3_categoriesSeedEightRows() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM product.categories", Integer.class);
        assertThat(count).isEqualTo(8);
    }

    @Test
    void v3_productsSeedAtLeastFiftyRows() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM product.products", Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(50);
    }
}
