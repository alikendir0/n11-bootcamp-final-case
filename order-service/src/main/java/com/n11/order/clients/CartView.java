package com.n11.order.clients;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartView(UUID userId, List<Line> items, Instant updatedAt) {
    public record Line(UUID productId, String nameSnapshot, String imageUrlSnapshot,
                       int qty, BigDecimal unitPriceSnapshot, BigDecimal lineTotal) {}
}
