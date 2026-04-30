package com.n11.payment.iyzico;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Neutral DTO namespace for Iyzico Checkout Form commands and results. */
public final class IyzicoCheckoutResult {

    private IyzicoCheckoutResult() {
    }

    public record CheckoutInitializationCommand(
        UUID orderId,
        String conversationId,
        BigDecimal amount,
        Instant createdAt,
        BuyerInput buyer,
        AddressInput shippingAddress,
        AddressInput billingAddress,
        List<BasketItemInput> items
    ) {
    }

    public record BuyerInput(
        String id,
        String name,
        String surname,
        String gsmNumber,
        String email,
        String identityNumber,
        String registrationAddress,
        String city,
        String country,
        String zipCode,
        String ip
    ) {
    }

    public record AddressInput(
        String contactName,
        String address,
        String city,
        String country,
        String zipCode
    ) {
    }

    public record BasketItemInput(
        String id,
        String name,
        String category1,
        String category2,
        String itemType,
        BigDecimal price
    ) {
    }

    public record InitializedCheckout(
        String token,
        String paymentPageUrl,
        String status,
        String errorCode,
        String errorMessage
    ) {
    }

    public record RetrievedCheckout(
        String token,
        String paymentId,
        String status,
        Integer fraudStatus,
        String errorCode,
        String errorMessage
    ) {
    }
}
