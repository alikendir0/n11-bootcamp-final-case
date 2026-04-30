package com.n11.cart.cart;

import com.n11.cart.cart.dto.CartLineView;
import com.n11.cart.cart.dto.CartView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Separate @Transactional bean per AOP-proxy bug-class fix (Phase 4 Plan 04-02 lesson).
 * CartService (outer, no-tx) delegates all DB writes here so @Transactional is honored.
 */
@Service
public class CartPersistenceService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    public CartPersistenceService(CartRepository cartRepository, CartItemRepository cartItemRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
    }

    @Transactional
    public CartView getOrCreateCartView(UUID userId) {
        cartRepository.findById(userId).orElseGet(() -> cartRepository.save(new Cart(userId)));
        return loadCartView(userId);
    }

    @Transactional
    public CartView upsertAndReturn(UUID userId, UUID productId, int qty,
                                    BigDecimal unitPrice, String name, String imageUrl) {
        cartRepository.findById(userId).orElseGet(() -> cartRepository.save(new Cart(userId)));
        cartItemRepository.upsertAddQty(userId, productId, qty, unitPrice, name, imageUrl);
        return loadCartView(userId);
    }

    @Transactional
    public CartView updateAndReturn(UUID userId, UUID productId, int qty,
                                    BigDecimal unitPrice, String name, String imageUrl) {
        int updated = cartItemRepository.updateLine(userId, productId, qty, unitPrice, name, imageUrl);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sepette ürün bulunamadı: " + productId);
        }
        return loadCartView(userId);
    }

    @Transactional
    public void removeLine(UUID userId, UUID productId) {
        int removed = cartItemRepository.deleteLine(userId, productId);
        if (removed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sepette ürün bulunamadı: " + productId);
        }
    }

    private CartView loadCartView(UUID userId) {
        Cart cart = cartRepository.findById(userId).orElse(new Cart(userId));
        List<CartLineView> lines = cartItemRepository.findByUserIdOrderByAddedAt(userId).stream()
            .map(ci -> new CartLineView(
                ci.getProductId(),
                ci.getNameSnapshot(),
                ci.getImageUrlSnapshot(),
                ci.getQty(),
                ci.getUnitPriceSnapshot(),
                ci.getUnitPriceSnapshot()
                    .multiply(BigDecimal.valueOf(ci.getQty()))
                    .setScale(2, RoundingMode.HALF_UP)
            ))
            .toList();
        return new CartView(userId, lines, cart.getUpdatedAt());
    }
}
