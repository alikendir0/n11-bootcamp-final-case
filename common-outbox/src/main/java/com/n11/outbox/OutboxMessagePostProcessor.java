package com.n11.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * D-09 outbox publisher invariant — sets {@link MessageProperties#setMessageId(String)} and
 * {@link MessageProperties#setCorrelationId(String)} on every published outbox message by parsing
 * the envelope JSON in the message body. Without this, the consumer-side
 * {@code RabbitRetryConfig.sagaRetryInterceptor} rejects the message because its
 * {@code messageKeyGenerator} requires a non-blank messageId (Phase 4 999.2 retrospective).
 *
 * <p>Standalone {@code @Component} (split from AbstractOutboxPoller per I1) — both
 * invariants live in one class with one responsibility, and the class is unit-testable
 * without booting the poller's @Scheduled / @Transactional infrastructure.
 *
 * <p>Stateless and thread-safe; injected as a singleton bean.
 */
@Component
public class OutboxMessagePostProcessor implements MessagePostProcessor {

    private final ObjectMapper objectMapper;

    public OutboxMessagePostProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Message postProcessMessage(Message message) throws AmqpException {
        try {
            byte[] body = message.getBody();
            JsonNode envelope = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
            String eventId = envelope.path("eventId").asText(null);
            if (eventId == null || eventId.isBlank()) {
                throw new IllegalStateException("Outbox payload missing envelope.eventId — cannot set messageId");
            }
            MessageProperties props = message.getMessageProperties();
            props.setMessageId(eventId);
            props.setContentType("application/json");
            String correlationId = envelope.path("correlationId").asText(null);
            if (correlationId != null && !correlationId.isBlank()) {
                props.setCorrelationId(correlationId);
                props.setHeader("X-Correlation-Id", correlationId);
            }
            return message;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to inject messageId/correlationId from outbox envelope", e);
        }
    }
}
