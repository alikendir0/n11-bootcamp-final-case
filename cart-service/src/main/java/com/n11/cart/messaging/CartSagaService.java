package com.n11.cart.messaging;

import com.n11.cart.cart.CartItemRepository;
import com.n11.events.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Transactional service that processes saga events for cart-service.
 * Idempotency check + side effect + processed_events row all inside one @Transactional
 * (CLAUDE.md Rule #3 — every saga consumer must be idempotent).
 */
@Service
public class CartSagaService {

    private static final Logger LOG = LoggerFactory.getLogger(CartSagaService.class);

    private final ProcessedEventRepository processedEventsRepository;
    private final CartItemRepository cartItemRepository;

    public CartSagaService(ProcessedEventRepository processedEventsRepository,
                           CartItemRepository cartItemRepository) {
        this.processedEventsRepository = processedEventsRepository;
        this.cartItemRepository = cartItemRepository;
    }

    /**
     * D-07: clear ENTIRE cart on order.confirmed — DELETE FROM cart_items WHERE user_id = ?
     * The carts row stays (it's the per-user record per D-11).
     */
    @Transactional
    public void processOrderConfirmed(UUID eventId, Envelope envelope,
                                      OrderConfirmedConsumer.OrderConfirmedPayload payload) {
        if (processedEventsRepository.existsById(eventId)) {
            LOG.debug("cart.saga: duplicate event {}, skipping", eventId);
            return;
        }
        int deleted = cartItemRepository.deleteByUserId(payload.userId());
        LOG.info("cart.saga: cleared {} cart_items for user {} on order.confirmed orderId={}",
            deleted, payload.userId(), payload.orderId());
        processedEventsRepository.save(
            new ProcessedEvent(eventId, "OrderConfirmedConsumer", envelope.eventType()));
    }
}
