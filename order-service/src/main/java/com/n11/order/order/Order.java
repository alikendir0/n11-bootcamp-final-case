package com.n11.order.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "orders")
public class Order {
    @Id @Column(name = "id") private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private OrderStatus status;
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2) private BigDecimal totalAmount;
    @Column(name = "currency", nullable = false, length = 3) private String currency;
    @Column(name = "correlation_id", nullable = false) private UUID correlationId;
    @Column(name = "idempotency_key", nullable = false) private UUID idempotencyKey;
    @Column(name = "cancel_reason") private String cancelReason;
    @Version @Column(name = "version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Order() {}
    public Order(UUID id, UUID userId, OrderStatus status, BigDecimal totalAmount, String currency,
                 UUID correlationId, UUID idempotencyKey) {
        this.id = id; this.userId = userId; this.status = status; this.totalAmount = totalAmount;
        this.currency = currency; this.correlationId = correlationId; this.idempotencyKey = idempotencyKey;
        Instant now = Instant.now(); this.createdAt = now; this.updatedAt = now;
    }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public UUID getCorrelationId() { return correlationId; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public String getCancelReason() { return cancelReason; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setStatus(OrderStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; this.updatedAt = Instant.now(); }
}
