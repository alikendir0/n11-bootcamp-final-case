package com.n11.payment.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for the processed_events idempotency inbox.
 *
 * <p>{@code existsById(UUID)} is provided by JpaRepository and maps to a fast primary-key lookup.
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
    // existsById(UUID) inherited from JpaRepository
}
