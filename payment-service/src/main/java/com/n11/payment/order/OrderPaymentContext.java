package com.n11.payment.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderPaymentContext(
        UUID orderId,
        UUID userId,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt,
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
