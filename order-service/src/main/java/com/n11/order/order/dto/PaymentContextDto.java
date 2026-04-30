package com.n11.order.order.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PaymentContextDto(
        UUID orderId,
        UUID userId,
        BigDecimal totalAmount,
        String currency,
        ShippingAddress shippingAddress,
        List<Item> items) {

    public record ShippingAddress(
            String recipientName,
            String phone,
            String il,
            String ilce,
            String mahalle,
            String streetLine,
            String postalCode,
            String title) {
    }

    public record Item(
            UUID productId,
            String nameSnapshot,
            int qty,
            BigDecimal unitPrice) {
    }
}
