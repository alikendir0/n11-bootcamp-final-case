package com.n11.payment.outbox;

import com.n11.outbox.OutboxEvent;
import com.n11.outbox.OutboxRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Per-service outbox repository for payment-service (D-09).
 *
 * <p>Schema-qualifies the SELECT so Postgres resolves the table in the {@code payment} schema.
 */
public interface PaymentOutboxRepository extends OutboxRepository {

    @Override
    @Query(value = "SELECT * FROM payment.outbox WHERE sent_at IS NULL ORDER BY occurred_at LIMIT :batchSize FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    List<OutboxEvent> findUnsentBatch(@Param("batchSize") int batchSize);
}
