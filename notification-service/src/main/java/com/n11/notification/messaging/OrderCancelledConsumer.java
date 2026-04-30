package com.n11.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import com.n11.notification.config.NotificationRabbitConfig;
import com.n11.notification.messaging.payloads.OrderCancelledPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Saga consumer for {@code order.cancelled} events (NOTIF-01).
 *
 * <p>Listener method is NOT @Transactional — the listener/delegate split is mandatory because
 * Spring AMQP's container thread can bypass the AOP proxy for @Transactional
 * (Plan 04-02 STATE.md lesson). All transactional side effects are delegated to
 * {@link NotificationService#handleOrderCancelled}.
 *
 * <p>D-10 invariant: declares Spring AMQP {@code Message} parameter only;
 * NO rabbitmq Channel parameter.
 *
 * <p>Payload record {@link OrderCancelledPayload} is a standalone top-level type from
 * {@code com.n11.notification.messaging.payloads} (NOT an inner class) — matches
 * order-cancelled.schema.json 1:1. Fixes revision BLOCKER 1 (plan revision iteration 1).
 */
@Component
public class OrderCancelledConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(OrderCancelledConsumer.class);

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public OrderCancelledConsumer(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = NotificationRabbitConfig.QUEUE_NOTIFY_ORDER_CANCELLED)
    public void handleOrderCancelled(Message amqpMessage) {
        String body = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
        Envelope envelope;
        try {
            envelope = objectMapper.readValue(body, Envelope.class);
        } catch (Exception e) {
            LOG.error("notification.consumer.order-cancelled: malformed envelope, routing to DLQ. body={}", body, e);
            throw new AmqpRejectAndDontRequeueException("Malformed saga envelope", e);
        }
        UUID eventId;
        try {
            eventId = UUID.fromString(envelope.eventId());
        } catch (Exception e) {
            LOG.error("notification.consumer.order-cancelled: invalid eventId '{}', routing to DLQ", envelope.eventId(), e);
            throw new AmqpRejectAndDontRequeueException("Invalid eventId: " + envelope.eventId(), e);
        }
        if (envelope.payload() == null || envelope.payload().isNull()) {
            LOG.error("notification.consumer.order-cancelled: null payload for event {}, routing to DLQ", eventId);
            throw new AmqpRejectAndDontRequeueException("Null payload for event " + eventId);
        }
        OrderCancelledPayload payload;
        try {
            payload = objectMapper.treeToValue(envelope.payload(), OrderCancelledPayload.class);
        } catch (Exception e) {
            LOG.error("notification.consumer.order-cancelled: cannot parse payload for event {}", eventId, e);
            throw new AmqpRejectAndDontRequeueException("Cannot deserialize order.cancelled payload", e);
        }
        if (payload == null) {
            LOG.error("notification.consumer.order-cancelled: null deserialized payload for event {}", eventId);
            throw new AmqpRejectAndDontRequeueException("Deserialized payload is null for event " + eventId);
        }
        // Propagate transient exceptions — StatefulRetryInterceptor 3x retries → DLQ
        notificationService.handleOrderCancelled(eventId, envelope, payload);
    }
}
