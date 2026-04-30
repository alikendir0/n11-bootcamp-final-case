package com.n11.order.outbox;

import com.n11.outbox.OutboxEvent;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface OrderOutboxRepository extends com.n11.outbox.OutboxRepository {
    @Override
    @Query(value = "SELECT * FROM orders.outbox WHERE sent_at IS NULL ORDER BY occurred_at LIMIT :batchSize FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> findUnsentBatch(@Param("batchSize") int batchSize);
}
