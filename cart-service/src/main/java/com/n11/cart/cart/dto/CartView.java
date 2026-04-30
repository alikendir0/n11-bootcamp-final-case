package com.n11.cart.cart.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartView(
    UUID userId,
    List<CartLineView> items,
    Instant updatedAt
) {}
