package com.n11.inventory.stock;

import java.util.UUID;

/**
 * Thrown by StockService when a requested productId has no corresponding
 * stock entry in the inventory schema.
 *
 * <p>Caught in OrderCreatedConsumer to produce a {@code stock.reserve_failed}
 * outbox event with {@code reason=PRODUCT_NOT_FOUND}.
 */
public class ProductNotFoundException extends RuntimeException {

    private final UUID productId;

    public ProductNotFoundException(UUID productId) {
        super("Product not found in inventory: " + productId);
        this.productId = productId;
    }

    public UUID getProductId() { return productId; }
}
