package com.n11.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    /**
     * Drain query for the poller. Each per-service subclass MUST provide a native
     * @Query implementation that schema-qualifies the SELECT
     * (e.g. {@code SELECT * FROM <schema>.outbox WHERE sent_at IS NULL ORDER BY occurred_at LIMIT :batchSize FOR UPDATE SKIP LOCKED}).
     * The schema qualifier is required so Testcontainers slice-tests using the
     * {@code postgres} superuser (search_path=public by default) still resolve the right table.
     */
    List<OutboxEvent> findUnsentBatch(int batchSize);
}
