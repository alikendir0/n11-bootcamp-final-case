package com.n11.payment.iyzico;

import com.iyzipay.Options;
import com.iyzipay.model.CheckoutForm;
import com.iyzipay.model.Status;
import com.n11.payment.order.PaymentInitializationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultIyzicoCheckoutClient#retrieve(String, String)}.
 *
 * <p>Distinguishes two flavours of {@code status=failure} responses from Iyzico:
 * <ul>
 *   <li><strong>Transport / auth / schema failure</strong> — payment never processed,
 *       no {@code paymentStatus} or {@code paymentId}. Surfaced as
 *       {@code IYZICO_RETRIEVE_FAILED} so the saga compensates with {@code reason=UNKNOWN}.</li>
 *   <li><strong>Domain payment failure</strong> — Iyzico processed the payment and the
 *       issuer (or 3DS step) rejected it; {@code paymentStatus} and/or
 *       {@code paymentId} are populated. Flows through so that downstream
 *       {@code IyzicoCallbackOutcome.fromRetrieve} can map the granular taxonomy
 *       ({@code IYZICO_DECLINED}, {@code IYZICO_FRAUD_REVIEW},
 *       {@code IYZICO_3DS_MDSTATUS_INVALID}).</li>
 * </ul>
 */
class DefaultIyzicoCheckoutClientRetrieveTest {

    private static final String SECRET = "secret";
    private static final String CONVERSATION_ID = "11111111-1111-1111-1111-111111111111";
    private static final String TOKEN = "tok-123";

    @Test
    void apiSuccessWithValidSignatureReturnsRetrievedCheckout() {
        DefaultIyzicoCheckoutClient client = clientReturning(buildResponse(
            Status.SUCCESS.getValue(), true, "SUCCESS", "iyz-pay-1", null, null));

        var result = client.retrieve(TOKEN, CONVERSATION_ID);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.paymentId()).isEqualTo("iyz-pay-1");
    }

    @Test
    void apiSuccessWithInvalidSignatureRejectsAsSignatureInvalid() {
        DefaultIyzicoCheckoutClient client = clientReturning(buildResponse(
            Status.SUCCESS.getValue(), false, "SUCCESS", "iyz-pay-1", null, null));

        assertThatThrownBy(() -> client.retrieve(TOKEN, CONVERSATION_ID))
            .isInstanceOf(PaymentInitializationException.class)
            .hasMessageContaining("IYZICO_SIGNATURE_INVALID");
    }

    @Test
    void apiFailureWithoutPaymentInfoIsTreatedAsRetrieveFailed() {
        // Transport / auth failure: status=failure and no payment fields populated.
        DefaultIyzicoCheckoutClient client = clientReturning(buildResponse(
            Status.FAILURE.getValue(), false, null, null, "10001", "Invalid signature"));

        assertThatThrownBy(() -> client.retrieve(TOKEN, CONVERSATION_ID))
            .isInstanceOf(PaymentInitializationException.class)
            .hasMessageContaining("IYZICO_RETRIEVE_FAILED")
            .hasMessageContaining("10001");
    }

    @Test
    void apiFailureWithDomainPaymentFailureFlowsThroughAsDecline() {
        // Domain decline: status=failure but Iyzico processed and populated payment info.
        // Signature need not be valid; we trust the server-to-server TLS hop and let
        // IyzicoCallbackOutcome.fromRetrieve classify the granular failure downstream
        // (verified separately in com.n11.payment.api.IyzicoCallbackOutcomeTest).
        DefaultIyzicoCheckoutClient client = clientReturning(buildResponse(
            Status.FAILURE.getValue(), false, "FAILURE", "iyz-pay-2", "10051", "Insufficient funds"));

        var result = client.retrieve(TOKEN, CONVERSATION_ID);

        assertThat(result.status()).isEqualTo("FAILURE");
        assertThat(result.paymentId()).isEqualTo("iyz-pay-2");
        assertThat(result.errorCode()).isEqualTo("10051");
    }

    @Test
    void apiFailureWithOnlyPaymentIdStillFlowsThrough() {
        // Belt-and-braces: paymentStatus may be null but paymentId populated still
        // signals that Iyzico processed the request — treat as domain failure.
        DefaultIyzicoCheckoutClient client = clientReturning(buildResponse(
            Status.FAILURE.getValue(), false, null, "iyz-pay-3", "10054", "Card refused"));

        var result = client.retrieve(TOKEN, CONVERSATION_ID);

        assertThat(result.paymentId()).isEqualTo("iyz-pay-3");
        assertThat(result.errorCode()).isEqualTo("10054");
    }

    @Test
    void blankTokenThrowsBeforeAnyRetrieveCall() {
        DefaultIyzicoCheckoutClient client = new DefaultIyzicoCheckoutClient(properties(), options(),
            (req, opts) -> { throw new AssertionError("retriever must not be called for blank token"); },
            (req, opts) -> { throw new AssertionError("retriever must not be called for blank token"); });

        assertThatThrownBy(() -> client.retrieve("  ", CONVERSATION_ID))
            .isInstanceOf(PaymentInitializationException.class)
            .hasMessageContaining("IYZICO_RETRIEVE_FAILED")
            .hasMessageContaining("token missing");
    }

    private static DefaultIyzicoCheckoutClient clientReturning(CheckoutForm response) {
        return new DefaultIyzicoCheckoutClient(properties(), options(),
            (req, opts) -> { throw new AssertionError("initializer must not be called from retrieve test"); },
            (req, opts) -> response);
    }

    private static IyzicoProperties properties() {
        return new IyzicoProperties("https://sandbox-api.iyzipay.com", "api", SECRET,
            "https://demo.example.com", 15, "74300864791");
    }

    private static Options options() {
        Options options = new Options();
        options.setSecretKey(SECRET);
        return options;
    }

    private static CheckoutForm buildResponse(String status, boolean validSignature,
                                              String paymentStatus, String paymentId,
                                              String errorCode, String errorMessage) {
        CheckoutForm response = new CheckoutForm() {
            @Override
            public boolean verifySignature(String secretKey) {
                return validSignature;
            }
        };
        response.setStatus(status);
        response.setToken(TOKEN);
        response.setConversationId(CONVERSATION_ID);
        response.setPaymentStatus(paymentStatus);
        response.setPaymentId(paymentId);
        response.setErrorCode(errorCode);
        response.setErrorMessage(errorMessage);
        return response;
    }
}
