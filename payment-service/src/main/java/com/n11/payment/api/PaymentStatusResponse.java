package com.n11.payment.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Public payment status payload returned by {@link PaymentController}.
 *
 * <p>Per Phase 6 D-07: only neutral fields are exposed. The Iyzico SDK's embedded form
 * HTML is never returned — clients receive the {@code paymentPageUrl} only and redirect
 * the buyer to Iyzico's hosted page.
 *
 * <p>Statuses:
 * <ul>
 *   <li>{@code PENDING_INITIALIZATION} — no payment row yet (202 from controller).</li>
 *   <li>{@code PENDING} — Iyzico Checkout Form initialized; {@code paymentPageUrl} populated.</li>
 *   <li>{@code COMPLETED} — terminal success (after callback retrieve).</li>
 *   <li>{@code FAILED} / {@code TIMED_OUT} — terminal failure; {@code failureReason} populated.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PaymentStatusResponse(
    UUID orderId,
    String status,
    String paymentPageUrl,
    String failureReason,
    Instant updatedAt
) {
}
