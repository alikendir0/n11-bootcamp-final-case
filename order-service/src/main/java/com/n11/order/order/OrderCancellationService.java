package com.n11.order.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import com.n11.order.messaging.OrderSagaService;
import com.n11.order.outbox.OrderOutboxRepository;
import com.n11.outbox.OutboxEvent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrderCancellationService {

    private final OrderRepository orderRepository;
    private final OrderOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderCancellationService(OrderRepository orderRepository,
                                    OrderOutboxRepository outboxRepository,
                                    ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void cancel(UUID userId, UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Sipariş bulunamadı: " + orderId));
        if (!order.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sipariş bulunamadı: " + orderId);
        }
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.STOCK_RESERVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Bu sipariş artık iptal edilemez (status=" + order.getStatus() + ")");
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason("USER_CANCELLED");
        orderRepository.save(order);

        UUID eventId = UUID.randomUUID();
        OrderSagaService.OrderCancelledPayload payload = new OrderSagaService.OrderCancelledPayload(
            order.getId(), order.getUserId(), "USER_CANCELLED");
        Envelope envelope = new Envelope(
            eventId.toString(), "order.cancelled", 1, Instant.now(),
            order.getCorrelationId().toString(), null, "order-service",
            objectMapper.valueToTree(payload));
        try {
            String json = objectMapper.writeValueAsString(envelope);
            outboxRepository.save(new OutboxEvent(eventId, "orders", "order.cancelled", json, Instant.now()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize order.cancelled envelope", e);
        }
    }
}
