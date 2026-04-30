package com.n11.notification.messaging.payloads;

import java.util.UUID;

/**
 * Saga payload for {@code order.cancelled} events — matches order-cancelled.schema.json 1:1.
 * Reason is one of: OUT_OF_STOCK | PAYMENT_DECLINED | USER_CANCELLED | PAYMENT_TIMEOUT.
 */
public record OrderCancelledPayload(UUID orderId, UUID userId, String reason) {}
