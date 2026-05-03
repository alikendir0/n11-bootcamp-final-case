package com.n11.events.product;

import java.util.UUID;

/**
 * Shared payload for product.deleted event.
 */
public record ProductDeletedPayload(
    UUID productId
) {}
