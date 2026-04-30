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
 * CD-08: Saga compensation consumer for {@code payment.failed} events.
 *
 * <p>When payment fails (Phase 6: real Iyzico decline), this consumer releases the stock
 * reservation that inventory-service made for the order. Wired here in Phase 5 even though
 * no real {@code payment.failed} events fire yet — Phase 6 picks up seamlessly.
 *
 * <p>D-10 invariant: Message parameter present; no Channel parameter (AcknowledgeMode.AUTO).
 */
@Component
public class PaymentFailedConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentFailedConsumer.class);

    private final InventoryOrderService inventoryOrderService;
    private final ObjectMapper objectMapper;

    public PaymentFailedConsumer(InventoryOrderService inventoryOrderService, ObjectMapper objectMapper) {
        this.inventoryOrderService = inventoryOrderService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = InventoryRabbitConfig.QUEUE_INV_PAYMENT_FAILED)
    public void handlePaymentFailed(Message amqpMessage) {
        Envelope envelope = parseEnvelope(amqpMessage, "payment.failed");
        UUID eventId = parseEventId(envelope);

        if (envelope.payload() == null || envelope.payload().isNull()) {
            throw new AmqpRejectAndDontRequeueException("Null payload for event " + eventId);
        }

        PaymentFailedPayload payload;
        try {
            payload = objectMapper.treeToValue(envelope.payload(), PaymentFailedPayload.class);
        } catch (Exception e) {
            throw new AmqpRejectAndDontRequeueException("Cannot parse payment.failed payload", e);
        }
        if (payload == null) {
            throw new AmqpRejectAndDontRequeueException("Null deserialized payload for event " + eventId);
        }

        inventoryOrderService.releaseStockForOrder(eventId, envelope, payload.orderId(), "PaymentFailedConsumer");
    }

    private Envelope parseEnvelope(Message m, String expected) {
        try {
            return objectMapper.readValue(new String(m.getBody(), StandardCharsets.UTF_8), Envelope.class);
        } catch (Exception e) {
            LOG.error("inventory.consumer({}): malformed envelope", expected, e);
            throw new AmqpRejectAndDontRequeueException("Malformed envelope", e);
        }
    }

    private UUID parseEventId(Envelope e) {
        try {
            return UUID.fromString(e.eventId());
        } catch (Exception ex) {
            throw new AmqpRejectAndDontRequeueException("Invalid eventId: " + e.eventId(), ex);
        }
    }

    public record PaymentFailedPayload(UUID orderId, UUID paymentId, String reason, String errorCode) {}
}
