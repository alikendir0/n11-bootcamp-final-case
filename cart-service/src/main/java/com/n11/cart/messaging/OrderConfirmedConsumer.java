package com.n11.cart.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Idempotent saga consumer for {@code order.confirmed} events (D-07, CLAUDE.md Rule #3).
 *
 * <p>Delegates ALL transactional side effects (cart_items DELETE, processed_events row)
 * to {@link CartSagaService#processOrderConfirmed}. Listener method is NOT @Transactional
 * (Phase 4 Plan 04-02 lesson — AMQP container thread can bypass AOP proxy).
 *
 * <p>D-10 invariant: declares Spring AMQP {@code Message} parameter; NO
 * {@code com.rabbitmq.client.Channel} parameter. Plan 05-01 ArchUnit test enforces.
 */
@Component
public class OrderConfirmedConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(OrderConfirmedConsumer.class);

    private final CartSagaService cartSagaService;
    private final ObjectMapper objectMapper;

    public OrderConfirmedConsumer(CartSagaService cartSagaService, ObjectMapper objectMapper) {
        this.cartSagaService = cartSagaService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = CartRabbitConfig.QUEUE_CART_ORDER_CONFIRMED)
    public void handleOrderConfirmed(Message amqpMessage) {
        String body = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
        Envelope envelope;
        try {
            envelope = objectMapper.readValue(body, Envelope.class);
        } catch (Exception e) {
            LOG.error("cart.consumer: malformed envelope, routing to DLQ. body={}", body, e);
            throw new AmqpRejectAndDontRequeueException("Malformed saga envelope", e);
        }

        UUID eventId;
        try {
            eventId = UUID.fromString(envelope.eventId());
        } catch (Exception e) {
            LOG.error("cart.consumer: invalid eventId '{}', routing to DLQ", envelope.eventId(), e);
            throw new AmqpRejectAndDontRequeueException("Invalid eventId: " + envelope.eventId(), e);
        }

        if (envelope.payload() == null || envelope.payload().isNull()) {
            LOG.error("cart.consumer: null payload for event {}, routing to DLQ", eventId);
            throw new AmqpRejectAndDontRequeueException("Null payload for event " + eventId);
        }

        OrderConfirmedPayload payload;
        try {
            payload = objectMapper.treeToValue(envelope.payload(), OrderConfirmedPayload.class);
        } catch (Exception e) {
            LOG.error("cart.consumer: cannot parse order.confirmed payload for event {}", eventId, e);
            throw new AmqpRejectAndDontRequeueException("Cannot deserialize order.confirmed payload", e);
        }

        if (payload == null) {
            LOG.error("cart.consumer: deserialized payload is null for event {}, routing to DLQ", eventId);
            throw new AmqpRejectAndDontRequeueException("Deserialized payload is null for event " + eventId);
        }

        // Propagate transient exceptions — StatefulRetryInterceptor retries 3x → DLQ.
        cartSagaService.processOrderConfirmed(eventId, envelope, payload);
    }

    /**
     * Matches order-confirmed.schema.json payload shape.
     * Only userId is strictly needed for cart-clear (D-07), but the full payload shape is kept
     * for consistency with the schema and for potential future audit logging.
     */
    public record OrderConfirmedPayload(
        UUID orderId,
        UUID userId,
        BigDecimal totalAmount,
        List<Item> items
    ) {
        public record Item(UUID productId, int qty, BigDecimal unitPrice) {}
    }
}
