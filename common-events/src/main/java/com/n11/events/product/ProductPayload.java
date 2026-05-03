package com.n11.events.product;

import java.util.UUID;

/**
 * Shared payload for product.created and product.updated events.
 */
public record ProductPayload(
    UUID productId,
    String sku,
    String nameTr,
    String descriptionTr,
    Double priceGross,
    UUID categoryId
) {}
