package com.n11.cart.cart.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CartLineView(
    UUID productId,
    String nameSnapshot,
    String imageUrlSnapshot,
    int qty,
    BigDecimal unitPriceSnapshot,
    BigDecimal lineTotal
) {}
