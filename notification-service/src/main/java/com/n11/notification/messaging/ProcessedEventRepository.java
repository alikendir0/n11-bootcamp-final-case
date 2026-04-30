package com.n11.notification.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data repository for the notification-service idempotency inbox.
 *
 * <p>{@code existsById(UUID)} and {@code save(ProcessedEvent)} are inherited from
 * {@link JpaRepository} — no custom methods required.
 *
 * <p>Used by {@link NotificationService} to gate duplicate event processing
 * (saga-contracts.md §5.2 idempotency contract, CLAUDE.md Rule #3).
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
