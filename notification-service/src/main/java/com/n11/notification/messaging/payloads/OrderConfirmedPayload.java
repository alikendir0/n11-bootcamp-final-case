package com.n11.notification.messaging.payloads;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Saga payload for {@code order.confirmed} events — matches order-confirmed.schema.json 1:1.
 * Top-level record (NOT inner class) so NotificationService and NotificationTemplates can
 * import this in Plan 07-03 Task 2 before the OrderConfirmedConsumer (Task 3) exists.
 */
public record OrderConfirmedPayload(
    UUID orderId,
    UUID userId,
    BigDecimal totalAmount,
    List<Item> items
) {
    public record Item(UUID productId, int qty, BigDecimal unitPrice) {}
}
