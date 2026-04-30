package com.n11.notification.messaging.payloads;

import java.util.UUID;

/**
 * Saga payload for {@code user.registered} events — matches user-registered.schema.json 1:1.
 * registeredAt is RFC3339 / ISO-8601 string (parsed in service if needed).
 */
public record UserRegisteredPayload(UUID userId, String email, String fullName, String registeredAt) {}
