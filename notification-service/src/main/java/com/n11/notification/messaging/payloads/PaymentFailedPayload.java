package com.n11.notification.messaging.payloads;

import java.util.UUID;

/**
 * Saga payload for {@code payment.failed} events — matches payment-failed.schema.json 1:1.
 * Reason is one of: DECLINED | TIMEOUT | FRAUD | INSUFFICIENT_FUNDS | UNKNOWN.
 * errorCode is nullable.
 * NOTE: schema does NOT include userId — see NotificationService.handlePaymentFailed
 * for how user_id column is populated for this event type (orderId substitution for
 * query continuity since user_id is NULLABLE per ARCHITECTURE.md §2.10).
 */
public record PaymentFailedPayload(UUID orderId, UUID paymentId, String reason, String errorCode) {}
