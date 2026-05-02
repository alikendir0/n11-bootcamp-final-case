package com.n11.inventory.migrations;

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
 * Wave 0 Nyquist test: V1/V2/V3 migrations apply cleanly; expected tables exist.
 * B-03 fix for VALIDATION.md nyquist_compliant.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class InventoryFlywayMigrationsTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("n11")
                    .withUsername("inventory_user")
                    .withPassword("test-password");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void v1_processedEventsTableExists() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables " +
            "WHERE table_schema = 'inventory' AND table_name = 'processed_events'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void v2_stockTableExists() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables " +
            "WHERE table_schema = 'inventory' AND table_name = 'stock'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void v2_stockReservationsTableExists() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables " +
            "WHERE table_schema = 'inventory' AND table_name = 'stock_reservations'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void v2_outboxTableExists() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables " +
            "WHERE table_schema = 'inventory' AND table_name = 'outbox'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void stockSeedIncludesOriginalAndDummyJsonRows() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM inventory.stock", Integer.class);
        assertThat(count).isEqualTo(246);
    }
}
