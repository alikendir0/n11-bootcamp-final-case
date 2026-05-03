package com.n11.product.product.outbox;

import com.n11.outbox.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID>, com.n11.outbox.OutboxRepository {

    @Override
    @Query(value = "SELECT * FROM product.outbox WHERE sent_at IS NULL ORDER BY occurred_at LIMIT :batchSize FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> findUnsentBatch(@Param("batchSize") int batchSize);
}
