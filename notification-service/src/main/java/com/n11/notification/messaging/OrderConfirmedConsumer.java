package com.n11.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import com.n11.notification.config.NotificationRabbitConfig;
import com.n11.notification.messaging.payloads.OrderConfirmedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Saga consumer for {@code order.confirmed} events (NOTIF-01).
 *
 * <p>Listener method is NOT @Transactional — the listener/delegate split is mandatory because
 * Spring AMQP's container thread can bypass the AOP proxy for @Transactional
 * (Plan 04-02 STATE.md lesson). All transactional side effects are delegated to
 * {@link NotificationService#handleOrderConfirmed}.
 *
 * <p>D-10 invariant: declares Spring AMQP {@code Message} parameter only;
 * NO rabbitmq Channel parameter.
 *
 * <p>Payload record {@link OrderConfirmedPayload} is a standalone top-level type from
 * {@code com.n11.notification.messaging.payloads} (NOT an inner class) — matches
 * order-confirmed.schema.json 1:1. Fixes revision BLOCKER 1 (plan revision iteration 1).
 */
@Component
public class OrderConfirmedConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(OrderConfirmedConsumer.class);

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public OrderConfirmedConsumer(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = NotificationRabbitConfig.QUEUE_NOTIFY_ORDER_CONFIRMED)
    public void handleOrderConfirmed(Message amqpMessage) {
        String body = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
        Envelope envelope;
        try {
            envelope = objectMapper.readValue(body, Envelope.class);
        } catch (Exception e) {
            LOG.error("notification.consumer.order-confirmed: malformed envelope, routing to DLQ. body={}", body, e);
            throw new AmqpRejectAndDontRequeueException("Malformed saga envelope", e);
        }
        UUID eventId;
        try {
            eventId = UUID.fromString(envelope.eventId());
        } catch (Exception e) {
            LOG.error("notification.consumer.order-confirmed: invalid eventId '{}', routing to DLQ", envelope.eventId(), e);
            throw new AmqpRejectAndDontRequeueException("Invalid eventId: " + envelope.eventId(), e);
        }
        if (envelope.payload() == null || envelope.payload().isNull()) {
            LOG.error("notification.consumer.order-confirmed: null payload for event {}, routing to DLQ", eventId);
            throw new AmqpRejectAndDontRequeueException("Null payload for event " + eventId);
        }
        OrderConfirmedPayload payload;
        try {
            payload = objectMapper.treeToValue(envelope.payload(), OrderConfirmedPayload.class);
        } catch (Exception e) {
            LOG.error("notification.consumer.order-confirmed: cannot parse payload for event {}", eventId, e);
            throw new AmqpRejectAndDontRequeueException("Cannot deserialize order.confirmed payload", e);
        }
        if (payload == null) {
            LOG.error("notification.consumer.order-confirmed: null deserialized payload for event {}", eventId);
            throw new AmqpRejectAndDontRequeueException("Deserialized payload is null for event " + eventId);
        }
        // Propagate transient exceptions — StatefulRetryInterceptor 3x retries → DLQ
        notificationService.handleOrderConfirmed(eventId, envelope, payload);
    }
}
