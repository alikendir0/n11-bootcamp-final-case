package com.n11.inventory.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Drain query for the poller. {@code FOR UPDATE SKIP LOCKED} ensures two poller
     * instances (if scaled) cannot double-publish the same row.
     */
    @Query(
            value = "SELECT * FROM inventory.outbox WHERE sent_at IS NULL ORDER BY occurred_at LIMIT :batchSize FOR UPDATE SKIP LOCKED",
            nativeQuery = true
    )
    List<OutboxEvent> findUnsentBatch(@Param("batchSize") int batchSize);

    /**
     * Used in tests to find outbox rows by event type without schema qualification.
     */
    List<OutboxEvent> findByEventType(String eventType);
}
