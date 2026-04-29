package com.n11.identity.user;

/**
 * Outbox publisher for {@code user.registered} events (D-12).
 * Implementation lives in {@code com.n11.identity.outbox} (Plan 03-05).
 *
 * <p>Placeholder interface so {@link UserService} compiles in isolation;
 * Plan 03-05 wires the real {@code OutboxRepository}-backed implementation.
 */
public interface UserRegistrationOutboxPublisher {

    /**
     * Insert a {@code user.registered} outbox row in the current transaction.
     * Caller must be {@code @Transactional}.
     */
    void publishRegistered(User user);
}
