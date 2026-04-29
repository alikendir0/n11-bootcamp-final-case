package com.n11.inventory.stock;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code inventory.stock} table.
 *
 * <p>{@code @Version} on {@code version} causes Hibernate to append
 * {@code WHERE version = ?} to UPDATE statements. If two concurrent transactions
 * both loaded the same row and both try to update, the second will throw
 * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}.
 * The {@link com.n11.inventory.messaging.OrderCreatedConsumer} catches this
 * and publishes a {@code stock.reserve_failed} event with
 * {@code reason=RESERVATION_CONFLICT}.
 */
@Entity
@Table(name = "stock")
public class Stock {

    @Id
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "available_qty", nullable = false)
    private int availableQty;

    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty = 0;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold = 5;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Stock() { /* JPA */ }

    /**
     * Create a new stock entry for a product with the given available quantity
     * and a default low-stock threshold.
     *
     * @param productId     the product UUID (matches product-service V3 seed)
     * @param availableQty  initial on-hand quantity
     * @param lowStockThreshold threshold below which to show "Son N ürün!"
     */
    public Stock(UUID productId, int availableQty, int lowStockThreshold) {
        this.productId = productId;
        this.availableQty = availableQty;
        this.reservedQty = 0;
        this.lowStockThreshold = lowStockThreshold;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** Convenience constructor with default threshold=5. */
    public Stock(UUID productId, int availableQty) {
        this(productId, availableQty, 5);
    }

    // -------------------------------------------------------------------------
    // Domain methods
    // -------------------------------------------------------------------------

    /**
     * Effective available quantity = on-hand minus reserved.
     * Used by StockStateDto to determine STOKTA / SON_URUN / TUKENDI labels.
     */
    public int getEffectiveAvailable() {
        return availableQty - reservedQty;
    }

    /**
     * Increment reserved quantity (called when an order is being processed).
     * Does NOT validate — caller (StockService) checks availability first.
     */
    public void reserve(int qty) {
        this.reservedQty += qty;
        this.updatedAt = Instant.now();
    }

    /**
     * Decrement reserved quantity (called on cancellation / payment timeout).
     * Clamps to 0 to prevent negative reservation counts.
     */
    public void release(int qty) {
        this.reservedQty = Math.max(0, this.reservedQty - qty);
        this.updatedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Accessors (no raw setters for qty fields — use domain methods above)
    // -------------------------------------------------------------------------

    public UUID getProductId()       { return productId; }
    public int getAvailableQty()     { return availableQty; }
    public int getReservedQty()      { return reservedQty; }
    public int getLowStockThreshold() { return lowStockThreshold; }
    public Long getVersion()         { return version; }
    public Instant getCreatedAt()    { return createdAt; }
    public Instant getUpdatedAt()    { return updatedAt; }
}
