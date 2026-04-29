package com.n11.identity.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Drain query for the poller. {@code FOR UPDATE SKIP LOCKED} ensures two poller
     * instances (if scaled) cannot double-publish the same row. Risk A3: requires
     * nativeQuery=true (Spring Data @Query won't pass-through the lock clause via JPQL).
     */
    @Query(
            value = "SELECT * FROM identity.outbox WHERE sent_at IS NULL ORDER BY occurred_at LIMIT :batchSize FOR UPDATE SKIP LOCKED",
            nativeQuery = true
    )
    List<OutboxEvent> findUnsentBatch(@Param("batchSize") int batchSize);
}
