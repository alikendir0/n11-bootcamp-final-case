package com.n11.order.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import com.n11.order.clients.AddressSnapshot;
import com.n11.order.clients.CartView;
import com.n11.order.idempotency.OrderIdempotencyKey;
import com.n11.order.idempotency.OrderIdempotencyKeyRepository;
import com.n11.order.order.dto.CreateOrderRequest;
import com.n11.order.order.dto.OrderResponse;
import com.n11.outbox.OutboxEvent;
import com.n11.order.outbox.OrderOutboxRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderTransactionalService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderShippingAddressRepository addressRepository;
    private final OrderIdempotencyKeyRepository idempotencyKeyRepository;
    private final OrderOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderTransactionalService(OrderRepository orderRepository,
                                     OrderItemRepository orderItemRepository,
                                     OrderShippingAddressRepository addressRepository,
                                     OrderIdempotencyKeyRepository idempotencyKeyRepository,
                                     OrderOutboxRepository outboxRepository,
                                     ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.addressRepository = addressRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderResponse persistOrder(UUID userId, UUID idempotencyKey, CreateOrderRequest body,
                                      CartView cart, AddressSnapshot address) {
        UUID orderId = UUID.randomUUID();
        UUID correlationId = orderId;  // saga root: correlationId = orderId (saga-contracts.md §1)

        BigDecimal total = cart.items().stream()
            .map(l -> l.unitPriceSnapshot().multiply(BigDecimal.valueOf(l.qty())))
            .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);

        Order order = new Order(orderId, userId, OrderStatus.PENDING, total, "TRY", correlationId, idempotencyKey);
        orderRepository.save(order);

        for (CartView.Line line : cart.items()) {
            orderItemRepository.save(new OrderItem(orderId, line.productId(), line.nameSnapshot(), line.qty(), line.unitPriceSnapshot()));
        }

        addressRepository.save(new OrderShippingAddress(
            orderId, address.recipientName(), address.phone(), address.il(), address.ilce(),
            address.mahalle(), address.streetLine(), address.postalCode(), address.title()
        ));

        try {
            idempotencyKeyRepository.save(new OrderIdempotencyKey(idempotencyKey, userId, orderId));
        } catch (DataIntegrityViolationException dive) {
            // Pitfall 2 race — another thread won. Re-fetch the winner and replay.
            UUID winnerOrderId = idempotencyKeyRepository.findByIdempotencyKeyAndUserId(idempotencyKey, userId)
                .map(OrderIdempotencyKey::getOrderId)
                .orElseThrow(() -> dive);
            return toResponseFor(winnerOrderId, true);
        }

        // Outbox: order.created envelope per order-created.schema.json
        UUID eventId = UUID.randomUUID();
        OrderCreatedPayload payload = new OrderCreatedPayload(orderId, userId, "TRY", total,
            cart.items().stream().map(l -> new OrderCreatedItem(l.productId(), l.qty(), l.unitPriceSnapshot(), l.nameSnapshot())).toList());
        Envelope envelope = new Envelope(
            eventId.toString(), "order.created", 1, Instant.now(),
            correlationId.toString(),
            null,
            "order-service",
            objectMapper.valueToTree(payload)
        );
        try {
            String json = objectMapper.writeValueAsString(envelope);
            outboxRepository.save(new OutboxEvent(eventId, "orders", "order.created", json, Instant.now()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize order.created envelope", e);
        }

        return new OrderResponse(orderId, OrderStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public OrderResponse toResponseFor(UUID orderId, boolean replay) {
        Order o = orderRepository.findById(orderId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Sipariş bulunamadı: " + orderId));
        return new OrderResponse(o.getId(), o.getStatus());
    }

    public record OrderCreatedPayload(UUID orderId, UUID userId, String currency, BigDecimal totalAmount, List<OrderCreatedItem> items) {}
    public record OrderCreatedItem(UUID productId, int qty, BigDecimal unitPrice, String nameSnapshot) {}
}
