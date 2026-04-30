package com.n11.inventory.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * CD-09: Saga compensation consumer for {@code order.cancelled} events.
 *
 * <p>When an order is cancelled (user-cancel via {@code POST /orders/{id}/cancel} or
 * stock-reserve-failed compensation), this consumer releases the stock reservation
 * that inventory-service made for the order.
 *
 * <p>D-10 invariant: Message parameter present; no Channel parameter (AcknowledgeMode.AUTO).
 */
@Component
public class OrderCancelledConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(OrderCancelledConsumer.class);

    private final InventoryOrderService inventoryOrderService;
    private final ObjectMapper objectMapper;

    public OrderCancelledConsumer(InventoryOrderService inventoryOrderService, ObjectMapper objectMapper) {
        this.inventoryOrderService = inventoryOrderService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = InventoryRabbitConfig.QUEUE_INV_ORDER_CANCELLED)
    public void handleOrderCancelled(Message amqpMessage) {
        Envelope envelope;
        try {
            envelope = objectMapper.readValue(new String(amqpMessage.getBody(), StandardCharsets.UTF_8), Envelope.class);
        } catch (Exception e) {
            LOG.error("inventory.consumer(order.cancelled): malformed envelope", e);
            throw new AmqpRejectAndDontRequeueException("Malformed envelope", e);
        }

        UUID eventId;
        try {
            eventId = UUID.fromString(envelope.eventId());
        } catch (Exception e) {
            throw new AmqpRejectAndDontRequeueException("Invalid eventId: " + envelope.eventId(), e);
        }

        if (envelope.payload() == null || envelope.payload().isNull()) {
            throw new AmqpRejectAndDontRequeueException("Null payload for event " + eventId);
        }

        OrderCancelledPayload payload;
        try {
            payload = objectMapper.treeToValue(envelope.payload(), OrderCancelledPayload.class);
        } catch (Exception e) {
            throw new AmqpRejectAndDontRequeueException("Cannot parse order.cancelled payload", e);
        }
        if (payload == null) {
            throw new AmqpRejectAndDontRequeueException("Null deserialized payload for event " + eventId);
        }

        inventoryOrderService.releaseStockForOrder(eventId, envelope, payload.orderId(), "OrderCancelledConsumer");
    }

    public record OrderCancelledPayload(UUID orderId, UUID userId, String reason) {}
}
