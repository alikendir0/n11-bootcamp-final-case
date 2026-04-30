package com.n11.payment.iyzico;

/**
 * Narrow infrastructure boundary for Iyzico Checkout Form.
 *
 * <p>The rest of payment-service depends on neutral command/result records only. Concrete SDK
 * mapping, signature verification, and SDK-only embedded-form HTML stay behind the adapter
 * implemented in a later plan.</p>
 */
public interface IyzicoCheckoutClient {

    IyzicoCheckoutResult.InitializedCheckout initialize(
        IyzicoCheckoutResult.CheckoutInitializationCommand command);

    IyzicoCheckoutResult.RetrievedCheckout retrieve(String token, String conversationId);
}
