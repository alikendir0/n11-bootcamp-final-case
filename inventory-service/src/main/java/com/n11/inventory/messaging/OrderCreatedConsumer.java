package com.n11.inventory.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Idempotent saga consumer for {@code order.created} events (PROD-08, CLAUDE.md Rule #3).
 *
 * <p>This class is responsible for AMQP deserialization and error routing only.
 * ALL transactional side effects (stock reservation, outbox row, processed_events row)
 * are delegated to {@link InventoryOrderService#processOrderCreated(UUID, Envelope, InventoryOrderService.OrderCreatedPayload)},
 * which carries the {@code @Transactional} annotation. This separation is required
 * because Spring AMQP's listener container invokes {@code @RabbitListener} methods via
 * the AMQP container thread; applying {@code @Transactional} directly to the listener
 * method risks AOP proxy bypassing on some Spring AMQP versions. The service delegate
 * is always invoked through its Spring-managed proxy, guaranteeing transactional behaviour.
 *
 * <p>CLAUDE.md Rule #3 contract:
 * <ul>
 *   <li>All DB writes happen in a single transaction inside {@code InventoryOrderService}</li>
 *   <li>Idempotency check (processedEventsRepository.existsById) happens INSIDE the transaction</li>
 *   <li>Re-delivering the same eventId produces exactly ONE row in each of processed_events,
 *       stock_reservations, and outbox</li>
 * </ul>
 *
 * <p>Error routing (with {@code AcknowledgeMode.AUTO} on the factory):
 * <ul>
 *   <li><b>Malformed / unrecoverable message</b> (bad JSON, invalid eventId, null payload):
 *       throws {@link AmqpRejectAndDontRequeueException} → container nacks with requeue=false
 *       → message routed to {@code inventory.q.order-created.dlq} via x-dead-letter.</li>
 *   <li><b>Transient service exception</b> (DB unavailable, optimistic lock not in try-catch):
 *       exception propagates → {@code StatefulRetryInterceptor} retries up to maxAttempts(3)
 *       → {@code RejectAndDontRequeueRecoverer} routes to DLQ after exhaustion.</li>
 *   <li><b>Successful processing</b>: method returns normally → container auto-acks.</li>
 * </ul>
 */
@Component
public class OrderCreatedConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(OrderCreatedConsumer.class);

    private final InventoryOrderService inventoryOrderService;
    private final ObjectMapper objectMapper;

    public OrderCreatedConsumer(InventoryOrderService inventoryOrderService,
                                ObjectMapper objectMapper) {
        this.inventoryOrderService = inventoryOrderService;
        this.objectMapper = objectMapper;
    }

    /**
     * Receives an AMQP message from {@code inventory.q.order-created}, deserializes it
     * into a saga {@link Envelope}, and delegates processing to {@link InventoryOrderService}.
     *
     * <p>This method is NOT {@code @Transactional}. Transaction management is handled
     * inside {@link InventoryOrderService#processOrderCreated}.
     *
     * <p>Unrecoverable messages (bad JSON, invalid UUID, null payload) are rejected immediately
     * via {@link AmqpRejectAndDontRequeueException} so the container nacks them to the DLQ
     * without consuming a retry slot. Transient failures (service exceptions) propagate
     * and are handled by the {@code StatefulRetryInterceptor} on the listener factory.
     */
    @RabbitListener(queues = InventoryRabbitConfig.QUEUE_INVENTORY_ORDERS)
    public void handleOrderCreated(Message amqpMessage) {
        String body = new String(amqpMessage.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        Envelope envelope;

        try {
            envelope = objectMapper.readValue(body, Envelope.class);
        } catch (Exception e) {
            LOG.error("inventory.consumer: malformed envelope, routing to DLQ. body={}", body, e);
            throw new AmqpRejectAndDontRequeueException("Malformed saga envelope", e);
        }

        UUID eventId;
        try {
            eventId = UUID.fromString(envelope.eventId());
        } catch (Exception e) {
            LOG.error("inventory.consumer: invalid eventId '{}', routing to DLQ", envelope.eventId(), e);
            throw new AmqpRejectAndDontRequeueException(
                    "Invalid eventId in saga envelope: " + envelope.eventId(), e);
        }

        if (envelope.payload() == null || envelope.payload().isNull()) {
            LOG.error("inventory.consumer: null payload in envelope for event {}, routing to DLQ", eventId);
            throw new AmqpRejectAndDontRequeueException(
                    "Null payload in saga envelope for event " + eventId);
        }

        InventoryOrderService.OrderCreatedPayload payload;
        try {
            payload = objectMapper.treeToValue(envelope.payload(),
                    InventoryOrderService.OrderCreatedPayload.class);
        } catch (Exception e) {
            LOG.error("inventory.consumer: cannot parse order.created payload for event {}", eventId, e);
            throw new AmqpRejectAndDontRequeueException(
                    "Cannot deserialize order.created payload for event " + eventId, e);
        }

        if (payload == null) {
            // treeToValue returned null without throwing (NullNode input) — treat as DLQ candidate
            LOG.error("inventory.consumer: deserialized payload is null for event {}, routing to DLQ", eventId);
            throw new AmqpRejectAndDontRequeueException(
                    "Deserialized payload is null for event " + eventId);
        }

        // Propagate transient service exceptions — StatefulRetryInterceptor will retry
        // and route to DLQ after maxAttempts(3). Do NOT swallow: with AcknowledgeMode.AUTO
        // a swallowed exception causes auto-ack and silent event loss.
        try {
            inventoryOrderService.processOrderCreated(eventId, envelope, payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // JsonProcessingException while building the outbox envelope JSON — treat as
            // transient (retry may help if it was a transient serialization glitch).
            LOG.error("inventory.consumer: JSON serialization error for event {}", eventId, e);
            throw new RuntimeException("Outbox serialization failure for event " + eventId, e);
        }
    }
}
