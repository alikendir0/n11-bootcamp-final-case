package com.n11.payment.api;

import com.n11.payment.iyzico.IyzicoCheckoutResult;

/**
 * Translates an Iyzico {@code CheckoutForm.retrieve} response into a normalised
 * callback outcome that the saga's terminal handlers consume.
 *
 * <p>Two responsibilities:
 * <ul>
 *   <li>Decide whether the callback is a success ({@link Kind#COMPLETED})
 *       or one of several failure kinds that drive the {@code payment.failed}
 *       compensation path.</li>
 *   <li>Map the granular {@code IYZICO_*} taxonomy to the {@code payment.failed}
 *       schema's enum: {@code reason ∈ {DECLINED, FRAUD, INSUFFICIENT_FUNDS,
 *       TIMEOUT, UNKNOWN}}. The granular code lands in {@code errorCode}
 *       (and the row's {@code failure_code} column for ops).</li>
 * </ul>
 *
 * <p>Sandbox cards {@code 4131 1111 1111 1117} (mdStatus=0) and
 * {@code 4141 1111 1111 1115} (mdStatus=4) surface as
 * {@code IYZICO_3DS_MDSTATUS_INVALID}.
 *
 * <p>This is package-private because it is an implementation detail of the
 * payment-service callback flow; downstream consumers see the saga schema only.
 */
final class IyzicoCallbackOutcome {

    enum Kind {
        COMPLETED,
        DECLINED,
        FRAUD_REVIEW,
        MD_STATUS_INVALID,
        RETRIEVE_FAILED
    }

    private final Kind kind;
    private final String iyzicoPaymentId;
    private final String reason;
    private final String errorCode;

    private IyzicoCallbackOutcome(Kind kind, String iyzicoPaymentId, String reason, String errorCode) {
        this.kind = kind;
        this.iyzicoPaymentId = iyzicoPaymentId;
        this.reason = reason;
        this.errorCode = errorCode;
    }

    Kind kind() { return kind; }
    String iyzicoPaymentId() { return iyzicoPaymentId; }
    /** Schema-aligned reason for {@code payment.failed} payload (DECLINED/FRAUD/UNKNOWN). */
    String reason() { return reason; }
    /** Granular Iyzico taxonomy code (IYZICO_DECLINED, IYZICO_FRAUD_REVIEW, etc.). */
    String errorCode() { return errorCode; }

    boolean isCompleted() { return kind == Kind.COMPLETED; }

    /** Adapter-level retrieve failure (no SDK response, transport failure, signature). */
    static IyzicoCallbackOutcome retrieveFailed(String detail) {
        return new IyzicoCallbackOutcome(Kind.RETRIEVE_FAILED, null, "UNKNOWN",
            "IYZICO_RETRIEVE_FAILED" + (detail == null || detail.isBlank() ? "" : ": " + detail));
    }

    /**
     * Classify a successful retrieve response. {@code paymentStatus != "SUCCESS"} is the
     * universal failure signal even when the SDK reports HTTP-level success.
     */
    static IyzicoCallbackOutcome fromRetrieve(IyzicoCheckoutResult.RetrievedCheckout retrieve) {
        if (retrieve == null) {
            return retrieveFailed("null response");
        }
        String paymentStatus = retrieve.status();
        if (paymentStatus == null) {
            return retrieveFailed("missing paymentStatus");
        }
        if ("SUCCESS".equalsIgnoreCase(paymentStatus)) {
            return new IyzicoCallbackOutcome(Kind.COMPLETED, retrieve.paymentId(), null, null);
        }
        // mdStatus surfaces as a textual paymentStatus on sandbox 4131/4141 cards.
        if (paymentStatus.toUpperCase().contains("MD_STATUS") || paymentStatus.toUpperCase().contains("MDSTATUS")) {
            return new IyzicoCallbackOutcome(
                Kind.MD_STATUS_INVALID, retrieve.paymentId(), "DECLINED", "IYZICO_3DS_MDSTATUS_INVALID");
        }
        Integer fraud = retrieve.fraudStatus();
        if (fraud != null && fraud < 0) {
            return new IyzicoCallbackOutcome(
                Kind.FRAUD_REVIEW, retrieve.paymentId(), "FRAUD", "IYZICO_FRAUD_REVIEW");
        }
        return new IyzicoCallbackOutcome(
            Kind.DECLINED, retrieve.paymentId(), "DECLINED", "IYZICO_DECLINED");
    }
}
