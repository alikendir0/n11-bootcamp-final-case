package com.n11.cart.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateCartItemRequest(
    @Min(1) @Max(99) int qty
) {}
