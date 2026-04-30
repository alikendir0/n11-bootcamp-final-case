package com.n11.payment.api;

/**
 * Form-encoded body Iyzico Checkout Form posts to {@code /payments/iyzico/callback}
 * when the buyer completes the hosted form (sandbox + prod).
 *
 * <p>This record documents the wire shape; the controller binds the field via
 * {@code @RequestParam("token")} directly to avoid Spring MVC form-record gotchas.
 * Iyzico's callback contract is very narrow — only the {@code token} field is
 * required, and the merchant must call {@code CheckoutForm.retrieve(token)}
 * server-side to obtain the verified payment status.
 *
 * <p>The S2S webhook (separate JSON-bodied endpoint) is out of scope for Phase 6.
 *
 * @see <a href="https://docs.iyzico.com/en/payment-methods/checkoutform/cf-implementation/cf-retrieve.md">Iyzico Checkout Form Retrieve</a>
 */
public record IyzicoCallbackRequest(String token) {
}
