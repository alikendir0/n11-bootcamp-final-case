package com.n11.infra;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-schema boundary smoke test (D-05).
 *
 * <p>Verifies the role-level deny matrix in {@code infra/postgres/init.sh}
 * (written by Plan 01-03) prevents cross-schema reads at runtime — second
 * arm of ARCH-09. Plan 01-03 wrote the DDL; this test proves the deny works
 * against the same init.sh file (mounted via classpath copy from
 * {@code infra-tests/src/test/resources/init.sh}, derived from the rootProject's
 * init.sh by the {@code copyInitScript} Gradle task).
 *
 * <p>Singleton container (Pitfall #22): {@code @Container static} → one boot
 * per JVM, shared across all tests. Per-instance fields would pay 5–10s × N.
 *
 * <p>{@code withCopyFileToContainer} (not {@code withInitScript}) routes through
 * Postgres' canonical {@code /docker-entrypoint-initdb.d/} mechanism which
 * interpolates env vars in {@code .sh} scripts.
 */
@Testcontainers
class CrossSchemaDenyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("n11")
            .withUsername("postgres")
            .withPassword("postgres-test")
            .withEnv("IDENTITY_DB_PASSWORD",     "i-test")
            .withEnv("PRODUCT_DB_PASSWORD",      "p-test")
            .withEnv("INVENTORY_DB_PASSWORD",    "iv-test")
            .withEnv("CART_DB_PASSWORD",         "c-test")
            .withEnv("ORDERS_DB_PASSWORD",       "o-test")
            .withEnv("PAYMENT_DB_PASSWORD",      "pa-test")
            .withEnv("NOTIFICATION_DB_PASSWORD", "n-test")
            .withEnv("SEARCH_DB_PASSWORD",       "s-test")
            .withEnv("AI_DB_PASSWORD",           "ai-test")
            .withEnv("MCP_DB_PASSWORD",          "mcp-test")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("init.sh"),
                "/docker-entrypoint-initdb.d/00-init.sh"
            );

    /** product_user must NOT be able to read schema `cart` — REVOKE matrix in init.sh. */
    @Test
    void productUserCannotReadCartSchema() {
        SQLException ex = assertThrows(SQLException.class, () -> {
            try (Connection c = connectAs("product_user", "p-test");
                 Statement st = c.createStatement()) {
                st.executeQuery("SELECT 1 FROM cart.cart_items LIMIT 1");
            }
        });
        assertNotNull(ex.getMessage(), "SQLException must carry a message");
        assertTrue(
            ex.getMessage().contains("permission denied for schema cart"),
            () -> "Expected 'permission denied for schema cart' but got: " + ex.getMessage()
        );
    }

    /** Symmetric pair: cart_user must NOT be able to read schema `identity`. */
    @Test
    void cartUserCannotReadIdentitySchema() {
        SQLException ex = assertThrows(SQLException.class, () -> {
            try (Connection c = connectAs("cart_user", "c-test");
                 Statement st = c.createStatement()) {
                st.executeQuery("SELECT 1 FROM identity.users LIMIT 1");
            }
        });
        assertNotNull(ex.getMessage(), "SQLException must carry a message");
        assertTrue(
            ex.getMessage().contains("permission denied for schema identity"),
            () -> "Expected 'permission denied for schema identity' but got: " + ex.getMessage()
        );
    }

    /**
     * Positive control: product_user CAN read its own schema, current_schema() returns "product".
     * Proves (1) user works (rules out connection failure masquerading as deny) and
     * (2) ALTER USER product_user SET search_path = product, public from init.sh is in effect.
     */
    @Test
    void productUserCanReadOwnSchema() throws SQLException {
        try (Connection c = connectAs("product_user", "p-test");
             Statement st = c.createStatement();
             var rs = st.executeQuery("SELECT current_schema()")) {
            assertTrue(rs.next(), "current_schema() must return one row");
            assertEquals("product", rs.getString(1),
                "product_user's server-side search_path default must place it in schema `product`");
        }
    }

    /**
     * T-01-14 drift guard: the inline test passwords (*-test) MUST match the values
     * in .env.test. Both sources are in version control; this static block fails
     * fast if they diverge — eliminates silent cross-pollination risk.
     */
    static {
        Map<String, String> expected = Map.ofEntries(
            Map.entry("POSTGRES_PASSWORD",       "postgres-test"),
            Map.entry("IDENTITY_DB_PASSWORD",    "i-test"),
            Map.entry("PRODUCT_DB_PASSWORD",     "p-test"),
            Map.entry("INVENTORY_DB_PASSWORD",   "iv-test"),
            Map.entry("CART_DB_PASSWORD",        "c-test"),
            Map.entry("ORDERS_DB_PASSWORD",      "o-test"),
            Map.entry("PAYMENT_DB_PASSWORD",     "pa-test"),
            Map.entry("NOTIFICATION_DB_PASSWORD", "n-test"),
            Map.entry("SEARCH_DB_PASSWORD",      "s-test"),
            Map.entry("AI_DB_PASSWORD",          "ai-test"),
            Map.entry("MCP_DB_PASSWORD",         "mcp-test")
        );
        try (InputStream stream = CrossSchemaDenyTest.class.getResourceAsStream("/.env.test")) {
            if (stream == null) {
                throw new IllegalStateException(
                    "T-01-14 drift guard: /.env.test missing on the test classpath. " +
                    "Verify the `copyInitScript` Gradle task and processTestResources ran.");
            }
            Properties actual = new Properties();
            actual.load(stream);
            for (Map.Entry<String, String> e : expected.entrySet()) {
                String got = actual.getProperty(e.getKey());
                if (!e.getValue().equals(got)) {
                    throw new IllegalStateException(
                        "T-01-14 drift guard: .env.test value for " + e.getKey() +
                        " is '" + got + "' but inline test expects '" + e.getValue() + "'. " +
                        "These two sources MUST stay in sync — fix one to match the other.");
                }
            }
        } catch (IOException ioe) {
            throw new IllegalStateException("T-01-14 drift guard: failed to read .env.test", ioe);
        }
    }

    private static Connection connectAs(String user, String password) throws SQLException {
        String jdbcUrl = POSTGRES.getJdbcUrl();
        assertTrue(jdbcUrl.endsWith("/n11") || jdbcUrl.contains("/n11?"),
            "Testcontainers JDBC URL must point at database `n11`; got: " + jdbcUrl);
        return DriverManager.getConnection(jdbcUrl, user, password);
    }
}
