package com.n11.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import com.n11.notification.config.NotificationRabbitConfig;
import com.n11.notification.messaging.payloads.PaymentFailedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Saga consumer for {@code payment.failed} events (NOTIF-01).
 *
 * <p>Listener method is NOT @Transactional — the listener/delegate split is mandatory because
 * Spring AMQP's container thread can bypass the AOP proxy for @Transactional
 * (Plan 04-02 STATE.md lesson). All transactional side effects are delegated to
 * {@link NotificationService#handlePaymentFailed}.
 *
 * <p>D-10 invariant: declares Spring AMQP {@code Message} parameter only;
 * NO rabbitmq Channel parameter.
 *
 * <p>Payload record {@link PaymentFailedPayload} is a standalone top-level type from
 * {@code com.n11.notification.messaging.payloads} (NOT an inner class) — matches
 * payment-failed.schema.json 1:1. Note: schema has no userId field; orderId is used for
 * audit row traceability. Fixes revision BLOCKER 1 (plan revision iteration 1).
 */
@Component
public class PaymentFailedConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentFailedConsumer.class);

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public PaymentFailedConsumer(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = NotificationRabbitConfig.QUEUE_NOTIFY_PAYMENT_FAILED)
    public void handlePaymentFailed(Message amqpMessage) {
        String body = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
        Envelope envelope;
        try {
            envelope = objectMapper.readValue(body, Envelope.class);
        } catch (Exception e) {
            LOG.error("notification.consumer.payment-failed: malformed envelope, routing to DLQ. body={}", body, e);
            throw new AmqpRejectAndDontRequeueException("Malformed saga envelope", e);
        }
        UUID eventId;
        try {
            eventId = UUID.fromString(envelope.eventId());
        } catch (Exception e) {
            LOG.error("notification.consumer.payment-failed: invalid eventId '{}', routing to DLQ", envelope.eventId(), e);
            throw new AmqpRejectAndDontRequeueException("Invalid eventId: " + envelope.eventId(), e);
        }
        if (envelope.payload() == null || envelope.payload().isNull()) {
            LOG.error("notification.consumer.payment-failed: null payload for event {}, routing to DLQ", eventId);
            throw new AmqpRejectAndDontRequeueException("Null payload for event " + eventId);
        }
        PaymentFailedPayload payload;
        try {
            payload = objectMapper.treeToValue(envelope.payload(), PaymentFailedPayload.class);
        } catch (Exception e) {
            LOG.error("notification.consumer.payment-failed: cannot parse payload for event {}", eventId, e);
            throw new AmqpRejectAndDontRequeueException("Cannot deserialize payment.failed payload", e);
        }
        if (payload == null) {
            LOG.error("notification.consumer.payment-failed: null deserialized payload for event {}", eventId);
            throw new AmqpRejectAndDontRequeueException("Deserialized payload is null for event " + eventId);
        }
        // Propagate transient exceptions — StatefulRetryInterceptor 3x retries → DLQ
        notificationService.handlePaymentFailed(eventId, envelope, payload);
    }
}
