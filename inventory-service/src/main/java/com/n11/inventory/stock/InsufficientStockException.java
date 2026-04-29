package com.n11.inventory.stock;

import java.util.UUID;

/**
 * Thrown by StockService.reserveStock when effective available quantity
 * is less than the requested quantity.
 *
 * <p>Caught in OrderCreatedConsumer to produce a {@code stock.reserve_failed}
 * outbox event with {@code reason=INSUFFICIENT_STOCK}.
 */
public class InsufficientStockException extends RuntimeException {

    private final UUID productId;
    private final int requested;
    private final int available;

    public InsufficientStockException(UUID productId, int requested, int available) {
        super("Insufficient stock for product " + productId
                + ": requested=" + requested + ", available=" + available);
        this.productId = productId;
        this.requested = requested;
        this.available = available;
    }

    public UUID getProductId() { return productId; }
    public int getRequested()  { return requested; }
    public int getAvailable()  { return available; }
}
