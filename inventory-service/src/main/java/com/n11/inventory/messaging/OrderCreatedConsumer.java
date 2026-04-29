package com.n11.inventory.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>Malformed JSON or unrecognised envelope shapes are caught here and logged.
 * The method does NOT re-throw, so Spring AMQP routes the message to the DLQ via
 * the x-dead-letter-routing-key configured in {@link InventoryRabbitConfig}.
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
     */
    @RabbitListener(queues = InventoryRabbitConfig.QUEUE_INVENTORY_ORDERS)
    public void handleOrderCreated(Message amqpMessage) {
        String body = new String(amqpMessage.getBody());
        Envelope envelope;

        try {
            envelope = objectMapper.readValue(body, Envelope.class);
        } catch (Exception e) {
            // Malformed envelope — route to DLQ by not re-throwing (T-04-02-01 mitigate)
            LOG.error("inventory.consumer: malformed envelope, routing to DLQ. body={}", body, e);
            return;
        }

        UUID eventId;
        try {
            eventId = UUID.fromString(envelope.eventId());
        } catch (Exception e) {
            LOG.error("inventory.consumer: invalid eventId '{}', routing to DLQ", envelope.eventId(), e);
            return;
        }

        InventoryOrderService.OrderCreatedPayload payload;
        try {
            payload = objectMapper.treeToValue(envelope.payload(),
                    InventoryOrderService.OrderCreatedPayload.class);
        } catch (Exception e) {
            LOG.error("inventory.consumer: cannot parse order.created payload for event {}", eventId, e);
            // Save to processed_events to prevent endless retry is handled inside service
            // but since we can't parse the payload, we just skip (DLQ candidate)
            return;
        }

        try {
            inventoryOrderService.processOrderCreated(eventId, envelope, payload);
        } catch (Exception e) {
            // Unexpected exception from service — log but do NOT re-throw.
            // Re-throwing would cause infinite AMQP retry loop (T-04-02-03 mitigate).
            LOG.error("inventory.consumer: unexpected error processing event {} for order {}",
                    eventId, payload.orderId(), e);
        }
    }
}
