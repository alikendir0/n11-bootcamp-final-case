package com.n11.cart.cart;

import com.n11.cart.cart.dto.CartView;
import com.n11.cart.product.ProductClient;
import com.n11.cart.product.ProductSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outer orchestrator — no @Transactional. Handles sync REST calls (product snapshot fetch)
 * BEFORE delegating to CartPersistenceService for the transactional DB work.
 *
 * <p>Two-bean split per Phase 4 Plan 04-02 lesson: @Transactional on a self-invoked method
 * is bypassed by the AOP proxy. Keeping REST calls outside @Transactional also follows
 * RESEARCH Pitfall 1 (no HTTP inside @Transactional).
 */
@Service
public class CartService {

    private final ProductClient productClient;
    private final CartPersistenceService cartPersistenceService;

    public CartService(ProductClient productClient, CartPersistenceService cartPersistenceService) {
        this.productClient = productClient;
        this.cartPersistenceService = cartPersistenceService;
    }

    public CartView getCart(UUID userId) {
        return cartPersistenceService.getOrCreateCartView(userId);
    }

    public CartView addItem(UUID userId, UUID productId, int qty) {
        ProductSnapshot snap = productClient.fetchSnapshot(productId);   // sync REST OUTSIDE tx
        BigDecimal unitPrice = snap.priceGross() != null ? snap.priceGross() : BigDecimal.ZERO;
        String name = snap.nameTr() != null ? snap.nameTr() : "(unnamed)";
        String imageUrl = (snap.imageUrls() != null && !snap.imageUrls().isEmpty())
                          ? snap.imageUrls().get(0) : null;
        return cartPersistenceService.upsertAndReturn(userId, productId, qty, unitPrice, name, imageUrl);
    }

    public CartView updateQty(UUID userId, UUID productId, int qty) {
        ProductSnapshot snap = productClient.fetchSnapshot(productId);   // sync REST OUTSIDE tx
        BigDecimal unitPrice = snap.priceGross() != null ? snap.priceGross() : BigDecimal.ZERO;
        String name = snap.nameTr() != null ? snap.nameTr() : "(unnamed)";
        String imageUrl = (snap.imageUrls() != null && !snap.imageUrls().isEmpty())
                          ? snap.imageUrls().get(0) : null;
        return cartPersistenceService.updateAndReturn(userId, productId, qty, unitPrice, name, imageUrl);
    }

    public void removeItem(UUID userId, UUID productId) {
        cartPersistenceService.removeLine(userId, productId);
    }
}
