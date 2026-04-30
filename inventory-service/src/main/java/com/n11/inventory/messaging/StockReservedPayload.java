package com.n11.inventory.messaging;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Outbound stock.reserved payload (W4 — totalAmount added to schema).
 *
 * <p>Inventory-service forwards order.created.totalAmount verbatim so that payment-service
 * (Phase 5 mock skeleton AND Phase 6 real Iyzico) can charge the correct amount.
 * Saga-contracts/stock-reserved.schema.json requires this field (minimum: 0).
 */
public record StockReservedPayload(
        UUID orderId,
        UUID reservationId,
        List<Item> reservedItems,
        BigDecimal totalAmount) {

    public record Item(UUID productId, int qty) {}
}
