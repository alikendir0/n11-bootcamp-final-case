package com.n11.order.idempotency;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "order_idempotency_keys") @IdClass(OrderIdempotencyKeyId.class)
public class OrderIdempotencyKey {
    @Id @Column(name = "idempotency_key") private UUID idempotencyKey;
    @Id @Column(name = "user_id") private UUID userId;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected OrderIdempotencyKey() {}
    public OrderIdempotencyKey(UUID idempotencyKey, UUID userId, UUID orderId) {
        this.idempotencyKey = idempotencyKey; this.userId = userId; this.orderId = orderId;
        this.createdAt = Instant.now();
    }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public UUID getUserId() { return userId; }
    public UUID getOrderId() { return orderId; }
    public Instant getCreatedAt() { return createdAt; }
}
