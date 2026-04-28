package com.n11.events;

import org.junit.jupiter.api.Test;

/**
 * Phase-1 self-test that proves the classpath schema loader + networknt validator
 * pairing works end-to-end against {@code /saga-schemas/envelope.schema.json}.
 *
 * <p>The test passes if a minimal valid envelope JSON validates against the
 * envelope schema with zero errors — proving the D-08 drift gate is operational
 * before any Phase 5+ producer extends {@link AbstractEventSchemaTest}.
 */
class EnvelopeSchemaSelfTest extends AbstractEventSchemaTest {

    @Test
    void fixtureEnvelopeValidatesAgainstEnvelopeSchema() {
        // Minimal valid envelope per .planning/saga-contracts/envelope.schema.json.
        // String form (not a Jackson 2.x JsonNode) so we sidestep the Jackson 2/3 split
        // — networknt 3.0.2's Schema.validate(String, InputFormat) parses internally.
        String fixture = """
            {
              "eventId":       "11111111-1111-1111-1111-111111111111",
              "eventType":     "order.created",
              "eventVersion":  1,
              "occurredAt":    "2026-04-28T10:15:30Z",
              "correlationId": "22222222-2222-2222-2222-222222222222",
              "causationId":   null,
              "producer":      "order-service",
              "payload":       {}
            }
            """;
        assertEventValid("envelope.schema.json", fixture);
    }
}
