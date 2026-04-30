package com.n11.inventory.outbox;

import com.n11.outbox.OutboxEvent;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends com.n11.outbox.OutboxRepository {
    @Override
    @Query(
        value = "SELECT * FROM inventory.outbox WHERE sent_at IS NULL ORDER BY occurred_at LIMIT :batchSize FOR UPDATE SKIP LOCKED",
        nativeQuery = true
    )
    List<OutboxEvent> findUnsentBatch(@Param("batchSize") int batchSize);

    /** Used in tests to find outbox rows by event type without schema qualification. */
    List<OutboxEvent> findByEventType(String eventType);
}
