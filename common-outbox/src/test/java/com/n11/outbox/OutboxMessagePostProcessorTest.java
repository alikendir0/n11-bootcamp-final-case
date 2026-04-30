package com.n11.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxMessagePostProcessorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OutboxMessagePostProcessor pp = new OutboxMessagePostProcessor(mapper);

    @Test
    void postProcessor_setsMessageId_andCorrelationId_fromEnvelope() {
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID correlationId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        String envelopeJson = """
            {"eventId":"%s","eventType":"order.created","eventVersion":1,
             "occurredAt":"2026-04-30T00:00:00Z","correlationId":"%s",
             "causationId":null,"producer":"order-service","payload":{"orderId":"%s"}}
            """.formatted(eventId, correlationId, eventId);

        MessageProperties props = new MessageProperties();
        Message m = new Message(envelopeJson.getBytes(StandardCharsets.UTF_8), props);
        Message after = pp.postProcessMessage(m);

        assertThat(after.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
        assertThat(after.getMessageProperties().getCorrelationId()).isEqualTo(correlationId.toString());
        assertThat((Object) after.getMessageProperties().getHeader("X-Correlation-Id")).isEqualTo(correlationId.toString());
        assertThat(after.getMessageProperties().getContentType()).isEqualTo("application/json");
    }

    @Test
    void postProcessor_throws_ifEventIdMissing() {
        Message m = new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());
        assertThatThrownBy(() -> pp.postProcessMessage(m))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("missing envelope.eventId");
    }
}
