package com.n11.payment.api;

import com.n11.payment.iyzico.IyzicoCheckoutResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Maps Iyzico retrieve responses to the granular failure taxonomy that lands in
 * {@code payment.failure_code} and the saga's {@code payment.failed} payload.
 *
 * <p>The boundary discovered during the live PAY-07 smoke run:
 * Iyzico returns {@code status=failure} for declined cards but populates
 * {@code paymentStatus} and {@code paymentId}. {@link DefaultIyzicoCheckoutClient}
 * lets such responses through; this mapper then produces the right granular code
 * ({@code IYZICO_DECLINED}, {@code IYZICO_FRAUD_REVIEW},
 * {@code IYZICO_3DS_MDSTATUS_INVALID}) instead of the catch-all
 * {@code IYZICO_RETRIEVE_FAILED}.
 */
class IyzicoCallbackOutcomeTest {

    @Test
    void successfulPaymentMapsToCompleted() {
        var retrieved = retrieved("SUCCESS", "iyz-1", 1, null);
        var outcome = IyzicoCallbackOutcome.fromRetrieve(retrieved);

        assertThat(outcome.isCompleted()).isTrue();
        assertThat(outcome.iyzicoPaymentId()).isEqualTo("iyz-1");
        assertThat(outcome.errorCode()).isNull();
    }

    @Test
    void declinedPaymentStatusFailureMapsToIyzicoDeclined() {
        // The exact case live smoke produced for card 4111 1111 1111 1129.
        var retrieved = retrieved("FAILURE", "iyz-2", 1, "10051");
        var outcome = IyzicoCallbackOutcome.fromRetrieve(retrieved);

        assertThat(outcome.isCompleted()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("IYZICO_DECLINED");
        assertThat(outcome.reason()).isEqualTo("DECLINED");
        assertThat(outcome.iyzicoPaymentId()).isEqualTo("iyz-2");
    }

    @Test
    void fraudFlagMapsToFraudReviewBeforeDeclineFallback() {
        // fraudStatus < 0 takes precedence over the generic decline branch.
        var retrieved = retrieved("FAILURE", "iyz-3", -1, "fraud-flag");
        var outcome = IyzicoCallbackOutcome.fromRetrieve(retrieved);

        assertThat(outcome.errorCode()).isEqualTo("IYZICO_FRAUD_REVIEW");
        assertThat(outcome.reason()).isEqualTo("FRAUD");
    }

    @Test
    void mdStatusVariantInPaymentStatusMapsToMdStatusInvalid() {
        // Sandbox cards 4131…1117 (mdStatus=0) and 4141…1115 (mdStatus=4) surface
        // here: paymentStatus contains MD_STATUS / MDSTATUS textually.
        var retrieved = retrieved("MDSTATUS_INVALID", "iyz-4", 1, "mdstatus-edge");
        var outcome = IyzicoCallbackOutcome.fromRetrieve(retrieved);

        assertThat(outcome.errorCode()).isEqualTo("IYZICO_3DS_MDSTATUS_INVALID");
        assertThat(outcome.reason()).isEqualTo("DECLINED");
    }

    @Test
    void nullResponseMapsToRetrieveFailed() {
        var outcome = IyzicoCallbackOutcome.fromRetrieve(null);

        assertThat(outcome.errorCode()).contains("IYZICO_RETRIEVE_FAILED");
        assertThat(outcome.reason()).isEqualTo("UNKNOWN");
    }

    @Test
    void missingPaymentStatusMapsToRetrieveFailed() {
        var retrieved = retrieved(null, "iyz-5", 1, null);
        var outcome = IyzicoCallbackOutcome.fromRetrieve(retrieved);

        assertThat(outcome.errorCode()).contains("IYZICO_RETRIEVE_FAILED");
    }

    private static IyzicoCheckoutResult.RetrievedCheckout retrieved(
            String paymentStatus, String paymentId, Integer fraudStatus, String errorCode) {
        return new IyzicoCheckoutResult.RetrievedCheckout(
            "tok", paymentId, paymentStatus, fraudStatus, errorCode, null);
    }
}
