package com.n11.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import com.n11.order.order.Order;
import com.n11.order.order.OrderItem;
import com.n11.order.order.OrderItemRepository;
import com.n11.order.order.OrderRepository;
import com.n11.order.order.OrderStatus;
import com.n11.outbox.OutboxEvent;
import com.n11.order.outbox.OrderOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderSagaService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderSagaService.class);

    private final ProcessedEventRepository processedEventsRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderSagaService(ProcessedEventRepository processedEventsRepository,
                            OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            OrderOutboxRepository outboxRepository,
                            ObjectMapper objectMapper) {
        this.processedEventsRepository = processedEventsRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void processStockReserved(UUID eventId, Envelope envelope, StockReservedConsumer.StockReservedPayload payload) {
        if (processedEventsRepository.existsById(eventId)) return;
        Order order = orderRepository.findById(payload.orderId()).orElse(null);
        if (order == null) {
            LOG.warn("order.saga: stock.reserved for unknown orderId={}, recording processed event and skipping", payload.orderId());
            recordProcessed(eventId, envelope, "StockReservedConsumer");
            return;
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.STOCK_RESERVED);
            orderRepository.save(order);
        } else {
            LOG.warn("order.saga: stock.reserved for order {} in status {}, no transition", order.getId(), order.getStatus());
        }
        recordProcessed(eventId, envelope, "StockReservedConsumer");
    }

    @Transactional
    public void processStockReserveFailed(UUID eventId, Envelope envelope, StockReserveFailedConsumer.StockReserveFailedPayload payload) {
        if (processedEventsRepository.existsById(eventId)) return;
        Order order = orderRepository.findById(payload.orderId()).orElse(null);
        if (order == null) { recordProcessed(eventId, envelope, "StockReserveFailedConsumer"); return; }
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelReason("OUT_OF_STOCK");
            orderRepository.save(order);
            publishOrderCancelled(order, envelope, "OUT_OF_STOCK");
        } else {
            LOG.warn("order.saga: stock.reserve_failed for order {} in status {}, no transition", order.getId(), order.getStatus());
        }
        recordProcessed(eventId, envelope, "StockReserveFailedConsumer");
    }

    @Transactional
    public void processPaymentCompleted(UUID eventId, Envelope envelope, PaymentCompletedConsumer.PaymentCompletedPayload payload) {
        if (processedEventsRepository.existsById(eventId)) return;
        Order order = orderRepository.findById(payload.orderId()).orElse(null);
        if (order == null) { recordProcessed(eventId, envelope, "PaymentCompletedConsumer"); return; }
        // Accept both PENDING and STOCK_RESERVED as source states (Pitfall 9 — race with stock.reserved)
        if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.STOCK_RESERVED) {
            order.setStatus(OrderStatus.CONFIRMED);  // skip transient PAID — saga semantics: PAID → CONFIRMED is one atomic step
            orderRepository.save(order);
            publishOrderConfirmed(order, envelope);
        } else {
            LOG.warn("order.saga: payment.completed for order {} in status {}, no transition", order.getId(), order.getStatus());
        }
        recordProcessed(eventId, envelope, "PaymentCompletedConsumer");
    }

    @Transactional
    public void processPaymentFailed(UUID eventId, Envelope envelope, PaymentFailedConsumer.PaymentFailedPayload payload) {
        if (processedEventsRepository.existsById(eventId)) return;
        Order order = orderRepository.findById(payload.orderId()).orElse(null);
        if (order == null) { recordProcessed(eventId, envelope, "PaymentFailedConsumer"); return; }
        if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.STOCK_RESERVED) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelReason("PAYMENT_DECLINED");
            orderRepository.save(order);
            publishOrderCancelled(order, envelope, "PAYMENT_DECLINED");
        } else {
            LOG.warn("order.saga: payment.failed for order {} in status {}, no transition", order.getId(), order.getStatus());
        }
        recordProcessed(eventId, envelope, "PaymentFailedConsumer");
    }

    private void publishOrderConfirmed(Order order, Envelope incoming) {
        var items = orderItemRepository.findByOrderId(order.getId()).stream()
            .map(oi -> new OrderConfirmedItem(oi.getProductId(), oi.getQty(), oi.getUnitPrice()))
            .toList();
        OrderConfirmedPayload payload = new OrderConfirmedPayload(order.getId(), order.getUserId(), order.getTotalAmount(), items);
        publishOutbox("order.confirmed", incoming, order, payload);
    }

    private void publishOrderCancelled(Order order, Envelope incoming, String reason) {
        OrderCancelledPayload payload = new OrderCancelledPayload(order.getId(), order.getUserId(), reason);
        publishOutbox("order.cancelled", incoming, order, payload);
    }

    private void publishOutbox(String eventType, Envelope incoming, Order order, Object payload) {
        UUID eventId = UUID.randomUUID();
        Envelope outEnv = new Envelope(
            eventId.toString(), eventType, 1, Instant.now(),
            order.getCorrelationId().toString(),
            incoming != null ? incoming.eventId() : null,
            "order-service",
            objectMapper.valueToTree(payload)
        );
        try {
            String json = objectMapper.writeValueAsString(outEnv);
            outboxRepository.save(new OutboxEvent(eventId, "orders", eventType, json, Instant.now()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize " + eventType + " envelope", e);
        }
    }

    private void recordProcessed(UUID eventId, Envelope envelope, String consumer) {
        processedEventsRepository.save(new ProcessedEvent(eventId, consumer, envelope.eventType()));
    }

    public record OrderConfirmedPayload(UUID orderId, UUID userId, BigDecimal totalAmount, List<OrderConfirmedItem> items) {}
    public record OrderConfirmedItem(UUID productId, int qty, BigDecimal unitPrice) {}
    public record OrderCancelledPayload(UUID orderId, UUID userId, String reason) {}
}
