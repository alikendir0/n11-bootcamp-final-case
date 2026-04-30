package com.n11.order.idempotency;

import java.io.Serializable;
import java.util.UUID;

public record OrderIdempotencyKeyId(UUID idempotencyKey, UUID userId) implements Serializable {}
