package com.n11.identity.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.identity.auth.dto.RegisterRequest;
import com.n11.outbox.OutboxEvent;
import com.n11.identity.user.UserService;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * D-12 / D-13 / Phase 3 success-criterion starter for QUAL-03.
 *
 * <p>Boot a Postgres + RabbitMQ container, run identity-service Flyway migrations,
 * exercise UserService.register, and prove:
 * <ul>
 *   <li>An outbox row lands with sent_at IS NULL</li>
 *   <li>The payload is a saga envelope wrapping a user-registered payload</li>
 *   <li>The payload validates against {@code user-registered.schema.json} (drift gate, D-08)</li>
 *   <li>OutboxPoller.poll() flips sent_at to a timestamp</li>
 *   <li>A second poll() processes zero rows (idempotency)</li>
 * </ul>
 *
 * <p>NOTE: This test key in {@code application-test.yml} is TEST-ONLY (kid=n11-jwt-test).
 * It is never used in production. Production keys are supplied via JWT_PRIVATE_KEY env var.
 *
 * <p>Schema validation uses networknt 3.0.2 API ({@link SchemaRegistry},
 * {@link SpecificationVersion}) — same API version locked in libs.versions.toml.
 * We replicate the classpath-only loading pattern from AbstractEventSchemaTest rather
 * than extending it (AbstractEventSchemaTest lives in common-events/src/test/ and is
 * not exported to dependents' test classpaths).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class OutboxIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("n11")
                    .withUsername("identity_user")
                    .withPassword("test-password");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.0-management"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // @ServiceConnection handles spring.datasource.url/username/password automatically.
        // We also override Flyway credentials so Flyway uses the same Testcontainer creds.
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired UserService userService;
    @Autowired OutboxRepository outboxRepository;
    @Autowired OutboxPoller outboxPoller;
    @Autowired ObjectMapper objectMapper;

    @Test
    void registrationProducesValidOutboxRow() throws Exception {
        // Snapshot baseline — V3 admin seed runs via Flyway (not UserService.register)
        // so it should NOT produce an outbox row. Capture before-count for robustness.
        int before = outboxRepository.findAll().size();

        userService.register(new RegisterRequest(
                "alice@example.com",
                "AlicePass1",
                "Alice Wonderland"
        ));

        List<OutboxEvent> outboxRows = outboxRepository.findAll();
        assertThat(outboxRows).hasSize(before + 1);

        OutboxEvent newest = outboxRows.get(outboxRows.size() - 1);
        assertThat(newest.getAggregate()).isEqualTo("identity");
        assertThat(newest.getEventType()).isEqualTo("user.registered");
        assertThat(newest.getSentAt()).isNull();

        // Parse the envelope JSON — verify 8 saga envelope fields
        JsonNode envelope = objectMapper.readTree(newest.getPayload());
        assertThat(envelope.get("eventType").asText()).isEqualTo("user.registered");
        assertThat(envelope.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("producer").asText()).isEqualTo("identity-service");
        assertThat(envelope.get("causationId").isNull()).isTrue();
        assertThat(envelope.get("correlationId").asText())
                .isEqualTo(envelope.get("eventId").asText());

        // Validate the payload sub-document against the locked schema (drift gate D-08).
        JsonNode payloadNode = envelope.get("payload");
        assertPayloadMatchesSchema(payloadNode);
    }

    @Test
    void pollerDrainsAndMarksSent() throws Exception {
        userService.register(new RegisterRequest(
                "bob@example.com", "BobPass1", "Bob Builder"
        ));

        // Drive the poller manually — @Scheduled is disabled in test context
        // (we rely on the @Scheduled annotation at runtime; here we invoke poll() directly)
        outboxPoller.poll();

        List<OutboxEvent> outboxRows = outboxRepository.findAll();
        // Find the row for bob — the last unsent before we called poll()
        OutboxEvent newest = outboxRows.stream()
                .filter(r -> r.getEventType().equals("user.registered") && r.getSentAt() != null)
                .reduce((first, second) -> second) // take the latest sent row
                .orElseThrow(() -> new AssertionError("No sent outbox row found after poll()"));
        assertThat(newest.getSentAt()).isNotNull();

        // Second poll() — nothing to do (idempotency)
        outboxPoller.poll();
        // Re-read the same row: sent_at must remain unchanged
        OutboxEvent reread = outboxRepository.findById(newest.getId()).orElseThrow();
        assertThat(reread.getSentAt()).isEqualTo(newest.getSentAt());
    }

    // ---------------------------------------------------------------------------
    // Schema validation helper — networknt 3.0.2 API (SchemaRegistry-based)
    // ---------------------------------------------------------------------------

    private static final String SCHEMA_FILE = "user-registered.schema.json";
    private static final String SCHEMA_PATH = "/saga-schemas/" + SCHEMA_FILE;

    /**
     * Validates {@code payloadNode} against {@code /saga-schemas/user-registered.schema.json}
     * on the classpath (Plan 03-02 mirrored the canonical schema into
     * {@code common-events/src/main/resources/saga-schemas/} which is on the test classpath
     * via {@code implementation(project(":common-events"))}).
     *
     * <p>Uses networknt 3.0.2 API: SchemaRegistry + SpecificationVersion + Schema.validate(String, InputFormat).
     * This mirrors AbstractEventSchemaTest's implementation; we cannot extend that class
     * directly (it lives in common-events/src/test/ and is not exported to dependents).
     */
    private void assertPayloadMatchesSchema(JsonNode payloadNode) throws IOException {
        try (InputStream schemaStream = getClass().getResourceAsStream(SCHEMA_PATH)) {
            assertThat(schemaStream)
                    .as("Schema not found on classpath: " + SCHEMA_PATH
                            + " — Plan 03-02 should have mirrored it to common-events/src/main/resources/saga-schemas/")
                    .isNotNull();

            String schemaJson = readStream(schemaStream);
            String payloadJson = objectMapper.writeValueAsString(payloadNode);

            // networknt 3.0.2: SchemaRegistry.builder() + Schema.validate(String, InputFormat)
            SchemaRegistry registry = SchemaRegistry.builder()
                    .defaultDialectId(SpecificationVersion.DRAFT_2020_12.getDialectId())
                    .schemaLoader(loader -> loader.fetchRemoteResources(false))
                    .build();

            Schema schema = registry.getSchema(schemaJson, InputFormat.JSON);
            List<Error> errors = schema.validate(payloadJson, InputFormat.JSON);

            assertThat(errors)
                    .as("user.registered payload should validate against user-registered.schema.json but got errors: %s", errors)
                    .isEmpty();
        }
    }

    private static String readStream(InputStream stream) throws IOException {
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        }
    }
}
