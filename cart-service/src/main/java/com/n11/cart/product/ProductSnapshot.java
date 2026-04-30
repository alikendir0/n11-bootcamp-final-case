package com.n11.cart.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Cart-service's view of product-service response (D-01).
 * Only the fields cart-service needs — Jackson ignores unknown fields by default.
 */
public record ProductSnapshot(
    UUID id,
    String nameTr,
    BigDecimal priceGross,
    List<String> imageUrls
) {}
