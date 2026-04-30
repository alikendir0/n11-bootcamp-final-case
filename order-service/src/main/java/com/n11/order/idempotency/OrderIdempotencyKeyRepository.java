package com.n11.order.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface OrderIdempotencyKeyRepository extends JpaRepository<OrderIdempotencyKey, OrderIdempotencyKeyId> {
    Optional<OrderIdempotencyKey> findByIdempotencyKeyAndUserId(UUID idempotencyKey, UUID userId);
    Optional<OrderIdempotencyKey> findFirstByIdempotencyKey(UUID idempotencyKey);  // RESEARCH Pitfall 2: detect cross-user collision
}
