package com.n11.inventory.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import com.n11.inventory.outbox.OutboxEvent;
import com.n11.inventory.outbox.OutboxRepository;
import com.n11.inventory.reservation.StockReservation;
import com.n11.inventory.reservation.StockReservationRepository;
import com.n11.inventory.stock.InsufficientStockException;
import com.n11.inventory.stock.ProductNotFoundException;
import com.n11.inventory.stock.Stock;
import com.n11.inventory.stock.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Idempotent saga consumer for {@code order.created} events (PROD-08, CLAUDE.md Rule #3).
 *
 * <p>ALL side effects (stock reservation, outbox row, processed_events row) are written
 * in a SINGLE {@code @Transactional} method. If any step throws, the entire transaction
 * rolls back and the event will be redelivered by RabbitMQ.
 *
 * <p>Idempotency is guaranteed by the processed_events inbox:
 * {@code processedEventsRepository.existsById(eventId)} is checked BEFORE any mutation.
 * On duplicate delivery, the method returns immediately without side effects.
 *
 * <p>RESERVATION_CONFLICT: {@link ObjectOptimisticLockingFailureException} from the
 * {@code @Version} field on {@link Stock} is caught here and published as
 * {@code stock.reserve_failed} with {@code reason=RESERVATION_CONFLICT}.
 * We do NOT re-throw — doing so would cause infinite AMQP retry loops.
 *
 * <p>DLQ: malformed JSON that cannot be deserialized into {@link Envelope} is a
 * programming error; catch the exception, log it, and return without re-throw so
 * the AMQP framework routes the message to the DLQ (x-dead-letter-routing-key).
 */
@Component
public class OrderCreatedConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(OrderCreatedConsumer.class);

    private final ProcessedEventRepository processedEventsRepository;
    private final StockRepository stockRepository;
    private final StockReservationRepository reservationRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderCreatedConsumer(ProcessedEventRepository processedEventsRepository,
                                StockRepository stockRepository,
                                StockReservationRepository reservationRepository,
                                OutboxRepository outboxRepository,
                                ObjectMapper objectMapper) {
        this.processedEventsRepository = processedEventsRepository;
        this.stockRepository = stockRepository;
        this.reservationRepository = reservationRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = InventoryRabbitConfig.QUEUE_INVENTORY_ORDERS)
    @Transactional
    public void handleOrderCreated(Message amqpMessage) throws Exception {
        String body = new String(amqpMessage.getBody());
        Envelope envelope;
        try {
            envelope = objectMapper.readValue(body, Envelope.class);
        } catch (Exception e) {
            // Malformed envelope — route to DLQ by not re-throwing (AMQP default behavior)
            LOG.error("inventory.consumer: malformed envelope, routing to DLQ. body={}", body, e);
            return;
        }

        UUID eventId = UUID.fromString(envelope.eventId());

        // ---- IDEMPOTENCY CHECK (CLAUDE.md Rule #3) -------------------------
        if (processedEventsRepository.existsById(eventId)) {
            LOG.debug("inventory.consumer: duplicate event {}, skipping", eventId);
            return;
        }

        OrderCreatedPayload payload;
        try {
            payload = objectMapper.treeToValue(envelope.payload(), OrderCreatedPayload.class);
        } catch (Exception e) {
            LOG.error("inventory.consumer: cannot parse order.created payload for event {}", eventId, e);
            // Save to processed_events so we don't retry this bad event endlessly
            processedEventsRepository.save(new ProcessedEvent(eventId, "OrderCreatedConsumer", envelope.eventType()));
            return;
        }

        UUID orderId = payload.orderId();
        List<ReservedItem> reservedItems = new ArrayList<>();
        List<FailedItem> failedItems = new ArrayList<>();
        String failReason = null;

        try {
            for (OrderItemPayload item : payload.items()) {
                UUID productId = item.productId();
                int qty = item.qty();

                Stock stock = stockRepository.findById(productId)
                        .orElseThrow(() -> new ProductNotFoundException(productId));

                if (stock.getEffectiveAvailable() < qty) {
                    throw new InsufficientStockException(productId, qty, stock.getEffectiveAvailable());
                }

                stock.reserve(qty);
                stockRepository.save(stock);  // @Version check happens here

                StockReservation reservation = new StockReservation(orderId, productId, qty);
                reservationRepository.save(reservation);

                reservedItems.add(new ReservedItem(productId, qty, reservation.getId()));
            }

            // All items reserved successfully — publish stock.reserved outbox event
            UUID reservationId = reservedItems.isEmpty()
                    ? UUID.randomUUID()
                    : reservedItems.get(0).reservationId();

            StockReservedPayload successPayload = new StockReservedPayload(
                    orderId,
                    reservationId,
                    reservedItems.stream().map(r -> new ReservedItemOut(r.productId(), r.qty())).toList()
            );

            Envelope outboundEnvelope = new Envelope(
                    UUID.randomUUID().toString(),
                    "stock.reserved",
                    1,
                    Instant.now(),
                    envelope.correlationId(),
                    envelope.eventId(),
                    "inventory-service",
                    objectMapper.valueToTree(successPayload)
            );

            String envelopeJson = objectMapper.writeValueAsString(outboundEnvelope);
            outboxRepository.save(new OutboxEvent(
                    UUID.randomUUID(), "inventory", "stock.reserved", envelopeJson, Instant.now()
            ));

        } catch (InsufficientStockException e) {
            failReason = "INSUFFICIENT_STOCK";
            failedItems.add(new FailedItem(e.getProductId(), e.getRequested(), e.getAvailable()));
            publishReserveFailed(orderId, failReason, failedItems, envelope);

        } catch (ProductNotFoundException e) {
            failReason = "PRODUCT_NOT_FOUND";
            failedItems.add(new FailedItem(e.getProductId(), 0, 0));
            publishReserveFailed(orderId, failReason, failedItems, envelope);

        } catch (ObjectOptimisticLockingFailureException e) {
            failReason = "RESERVATION_CONFLICT";
            LOG.warn("inventory.consumer: optimistic lock conflict for order {}, publishing reserve_failed", orderId);
            publishReserveFailed(orderId, failReason, failedItems, envelope);
        }

        // ALWAYS save processed_events (success or failure) — prevents re-processing
        processedEventsRepository.save(
                new ProcessedEvent(eventId, "OrderCreatedConsumer", envelope.eventType())
        );
    }

    private void publishReserveFailed(UUID orderId, String reason,
                                      List<FailedItem> failedItems, Envelope incoming) throws Exception {
        StockReserveFailedPayload failedPayload = new StockReserveFailedPayload(orderId, reason, failedItems);

        Envelope failEnvelope = new Envelope(
                UUID.randomUUID().toString(),
                "stock.reserve_failed",
                1,
                Instant.now(),
                incoming.correlationId(),
                incoming.eventId(),
                "inventory-service",
                objectMapper.valueToTree(failedPayload)
        );

        String failEnvelopeJson = objectMapper.writeValueAsString(failEnvelope);
        outboxRepository.save(new OutboxEvent(
                UUID.randomUUID(), "inventory", "stock.reserve_failed", failEnvelopeJson, Instant.now()
        ));
    }

    // -------------------------------------------------------------------------
    // Inner record types for payload deserialization / serialization
    // -------------------------------------------------------------------------

    private record OrderCreatedPayload(
            UUID orderId,
            UUID userId,
            String currency,
            BigDecimal totalAmount,
            List<OrderItemPayload> items
    ) {}

    private record OrderItemPayload(
            UUID productId,
            int qty,
            BigDecimal unitPrice,
            String nameSnapshot
    ) {}

    private record StockReservedPayload(
            UUID orderId,
            UUID reservationId,
            List<ReservedItemOut> reservedItems
    ) {}

    private record ReservedItemOut(UUID productId, int qty) {}

    private record StockReserveFailedPayload(
            UUID orderId,
            String reason,
            List<FailedItem> failedItems
    ) {}

    private record ReservedItem(UUID productId, int qty, UUID reservationId) {}

    private record FailedItem(UUID productId, int requestedQty, int availableQty) {}
}
