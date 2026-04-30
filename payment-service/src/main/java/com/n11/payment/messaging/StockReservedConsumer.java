package com.n11.payment.messaging;

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
 * Idempotent saga consumer for {@code stock.reserved} events (D-06, CLAUDE.md Rule #3).
 *
 * <p>This class is responsible for AMQP deserialization and error routing only.
 * ALL transactional side effects are delegated to {@link PaymentSagaService}, which
 * then delegates to {@link PaymentTransactionalService} for the @Transactional boundary.
 *
 * <p>D-10 invariant: Message parameter present; no Channel parameter (AcknowledgeMode.AUTO).
 */
@Component
public class StockReservedConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(StockReservedConsumer.class);

    private final PaymentSagaService paymentSagaService;
    private final ObjectMapper objectMapper;

    public StockReservedConsumer(PaymentSagaService paymentSagaService, ObjectMapper objectMapper) {
        this.paymentSagaService = paymentSagaService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = PaymentRabbitConfig.QUEUE_PAYMENT_STOCK_RESERVED)
    public void handleStockReserved(Message amqpMessage) {
        String body = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
        Envelope envelope;
        try {
            envelope = objectMapper.readValue(body, Envelope.class);
        } catch (Exception e) {
            LOG.error("payment.consumer: malformed envelope", e);
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

        StockReservedPayload payload;
        try {
            payload = objectMapper.treeToValue(envelope.payload(), StockReservedPayload.class);
        } catch (Exception e) {
            throw new AmqpRejectAndDontRequeueException("Cannot parse stock.reserved payload", e);
        }
        if (payload == null) {
            throw new AmqpRejectAndDontRequeueException("Deserialized payload null for event " + eventId);
        }

        paymentSagaService.processStockReserved(eventId, envelope, payload);
    }

    /**
     * Inbound stock.reserved payload (W4 — totalAmount added to schema for real order amount).
     * payment-service uses totalAmount to charge the correct amount (Phase 6: passed to Iyzico).
     */
    public record StockReservedPayload(
            UUID orderId,
            UUID reservationId,
            List<Item> reservedItems,
            BigDecimal totalAmount) {
        public record Item(UUID productId, int qty) {}
    }
}
