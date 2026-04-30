package com.n11.cart.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for the cart.processed_events idempotency inbox.
 *
 * <p>{@code existsById(UUID)} is provided by JpaRepository and maps to a
 * fast primary-key lookup on {@code processed_events.event_id}.
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
    // existsById(UUID) inherited from JpaRepository — no custom queries needed.
}
