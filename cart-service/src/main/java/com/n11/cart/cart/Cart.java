package com.n11.cart.cart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "carts")
public class Cart {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Cart() { /* JPA */ }

    public Cart(UUID userId) {
        this.userId = userId;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId()      { return userId; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void touch() { this.updatedAt = Instant.now(); }
}
