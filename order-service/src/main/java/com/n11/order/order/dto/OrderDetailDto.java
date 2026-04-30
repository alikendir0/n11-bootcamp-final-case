package com.n11.order.order.dto;

import com.n11.order.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderDetailDto(
    UUID id, UUID userId, OrderStatus status, BigDecimal totalAmount, String currency,
    String cancelReason, Instant createdAt, Instant updatedAt,
    List<Line> items, ShippingAddress shippingAddress
) {
    public record Line(UUID productId, String nameSnapshot, int qty, BigDecimal unitPrice) {}
    public record ShippingAddress(String recipientName, String phone, String il, String ilce,
                                  String mahalle, String streetLine, String postalCode, String title) {}
}
