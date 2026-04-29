package com.n11.inventory.reservation;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code inventory.stock_reservations} table.
 *
 * <p>Each row represents one product reservation within a single order.
 * The UNIQUE constraint on {@code (order_id, product_id)} prevents double-reservation
 * of the same product within the same order (complements the processed_events
 * idempotency check at the consumer level).
 */
@Entity
@Table(name = "stock_reservations")
public class StockReservation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty;

    @Column(name = "status", nullable = false)
    private String status = "RESERVED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockReservation() { /* JPA */ }

    public StockReservation(UUID orderId, UUID productId, int reservedQty) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.productId = productId;
        this.reservedQty = reservedQty;
        this.status = "RESERVED";
        this.createdAt = Instant.now();
    }

    public UUID getId()          { return id; }
    public UUID getOrderId()     { return orderId; }
    public UUID getProductId()   { return productId; }
    public int getReservedQty()  { return reservedQty; }
    public String getStatus()    { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(String status) { this.status = status; }
}
