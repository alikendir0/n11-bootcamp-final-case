package com.n11.inventory.messaging;

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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Transactional service that processes an {@code order.created} saga event.
 *
 * <p>All side effects (stock reservation, outbox row, processed_events row) are written
 * inside ONE {@code @Transactional} method — CLAUDE.md Rule #3.
 *
 * <p>The separation from {@link OrderCreatedConsumer} (which is NOT {@code @Transactional})
 * is intentional: Spring AMQP's {@code @RabbitListener} invokes listener methods via
 * reflection on the AMQP container thread, and mixing {@code @Transactional} on the
 * listener method itself can cause AOP proxy bypassing issues. Delegating to a Spring-managed
 * service bean guarantees the transactional proxy is honoured on every invocation.
 */
@Service
public class InventoryOrderService {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryOrderService.class);

    private final ProcessedEventRepository processedEventsRepository;
    private final StockRepository stockRepository;
    private final StockReservationRepository reservationRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public InventoryOrderService(ProcessedEventRepository processedEventsRepository,
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

    /**
     * Process a single {@code order.created} event atomically.
     *
     * <p>All DB writes (processed_events, stock_reservations, outbox) happen in one transaction.
     * If any write fails, everything rolls back and the AMQP message will be redelivered.
     *
     * @param eventId  parsed event ID (for idempotency key lookup)
     * @param envelope full envelope (for correlation/causation chain)
     * @param payload  parsed order payload
     */
    @Transactional
    public void processOrderCreated(UUID eventId, Envelope envelope, OrderCreatedPayload payload)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        // ---- IDEMPOTENCY CHECK (CLAUDE.md Rule #3) -------------------------
        if (processedEventsRepository.existsById(eventId)) {
            LOG.debug("inventory.service: duplicate event {}, skipping", eventId);
            return;
        }

        UUID orderId = payload.orderId();
        List<ReservedItem> reservedItems = new ArrayList<>();
        List<FailedItem> failedItems = new ArrayList<>();

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
            publishReserveFailed(orderId, "INSUFFICIENT_STOCK",
                    List.of(new FailedItem(e.getProductId(), e.getRequested(), e.getAvailable())),
                    envelope);

        } catch (ProductNotFoundException e) {
            publishReserveFailed(orderId, "PRODUCT_NOT_FOUND",
                    List.of(new FailedItem(e.getProductId(), 0, 0)),
                    envelope);

        } catch (ObjectOptimisticLockingFailureException e) {
            LOG.warn("inventory.service: optimistic lock conflict for order {}, publishing reserve_failed", orderId);
            publishReserveFailed(orderId, "RESERVATION_CONFLICT", failedItems, envelope);
        }

        // ALWAYS save processed_events (success or failure) — prevents re-processing
        processedEventsRepository.save(
                new ProcessedEvent(eventId, "OrderCreatedConsumer", envelope.eventType())
        );
    }

    private void publishReserveFailed(UUID orderId, String reason,
                                      List<FailedItem> failedItems, Envelope incoming) {
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

        try {
            String failEnvelopeJson = objectMapper.writeValueAsString(failEnvelope);
            outboxRepository.save(new OutboxEvent(
                    UUID.randomUUID(), "inventory", "stock.reserve_failed", failEnvelopeJson, Instant.now()
            ));
        } catch (Exception e) {
            LOG.error("inventory.service: failed to persist stock.reserve_failed outbox event for order {}", orderId, e);
        }
    }

    // -------------------------------------------------------------------------
    // Package-private record types shared with OrderCreatedConsumer
    // -------------------------------------------------------------------------

    record OrderCreatedPayload(
            java.util.UUID orderId,
            java.util.UUID userId,
            String currency,
            java.math.BigDecimal totalAmount,
            List<OrderItemPayload> items
    ) {}

    record OrderItemPayload(
            java.util.UUID productId,
            int qty,
            java.math.BigDecimal unitPrice,
            String nameSnapshot
    ) {}

    record StockReservedPayload(
            java.util.UUID orderId,
            java.util.UUID reservationId,
            List<ReservedItemOut> reservedItems
    ) {}

    record ReservedItemOut(java.util.UUID productId, int qty) {}

    record StockReserveFailedPayload(
            java.util.UUID orderId,
            String reason,
            List<FailedItem> failedItems
    ) {}

    record ReservedItem(java.util.UUID productId, int qty, java.util.UUID reservationId) {}

    record FailedItem(java.util.UUID productId, int requestedQty, int availableQty) {}
}
