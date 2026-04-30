package com.n11.notification.repository;

import com.n11.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for the {@code notifications} audit table.
 *
 * <p>{@code findByUserId} is required by the idempotency tests (Plan 07-04) and
 * the QUAL-04 end-to-end test (Plan 07-05).
 *
 * <p>{@code findByCorrelationId} is used by the QUAL-04 saga-trace assertion
 * and the smoke runbook step 9 follow-on.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserId(UUID userId);

    List<Notification> findByCorrelationId(UUID correlationId);
}
