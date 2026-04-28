package com.n11.logging;

import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.stereotype.Component;

/**
 * Wire #3A of 5: outbound AMQP correlation-ID propagation (post-processor half).
 *
 * Reads MDC correlationId and:
 *   - sets the AMQP standard `correlation_id` property (RabbitMQ-native field)
 *   - sets X-Correlation-Id as a message header (parallel to HTTP convention)
 *
 * The saga envelope's `correlationId` field (per ARCHITECTURE.md §3.4 / D-06)
 * is populated by the producer service's outbox writer at event-creation time;
 * this MPP layers the wire-protocol propagation on top.
 */
@Component
public class CorrelationIdMessagePostProcessor implements MessagePostProcessor {

    @Override
    public Message postProcessMessage(Message message) {
        String cid = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (cid != null && !cid.isBlank()) {
            message.getMessageProperties().setCorrelationId(cid);
            message.getMessageProperties().setHeader(CorrelationIdFilter.HEADER, cid);
        }
        return message;
    }
}
