package com.n11.identity.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * Shape of the {@code payload} field for a {@code user.registered} event.
 * Must validate against {@code saga-schemas/user-registered.schema.json}.
 */
public record UserRegisteredPayload(
        UUID userId,
        String email,
        String fullName,
        Instant registeredAt
) { }
