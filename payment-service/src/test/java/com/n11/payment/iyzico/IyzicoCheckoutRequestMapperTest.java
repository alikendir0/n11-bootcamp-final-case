package com.n11.payment.iyzico;

import com.iyzipay.Options;
import com.iyzipay.model.CheckoutFormInitialize;
import com.iyzipay.model.Currency;
import com.iyzipay.model.Locale;
import com.iyzipay.model.PaymentGroup;
import com.iyzipay.model.Status;
import com.iyzipay.request.CreateCheckoutFormInitializeRequest;
import com.n11.payment.order.PaymentInitializationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IyzicoCheckoutRequestMapperTest {

    private static final Instant CREATED_AT = Instant.parse("2026-04-30T10:15:30Z");
    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void generatedRequestUsesTurkishTryProductAndCallbackUrl() {
        CreateCheckoutFormInitializeRequest request = DefaultIyzicoCheckoutClient.buildRequest(command("10.00"), properties());

        assertThat(request.getLocale()).isEqualTo(Locale.TR.getValue());
        assertThat(request.getCurrency()).isEqualTo(Currency.TRY.name());
        assertThat(request.getPaymentGroup()).isEqualTo(PaymentGroup.PRODUCT.name());
        assertThat(request.getCallbackUrl()).isEqualTo("https://demo.example.com/api/v1/payments/iyzico/callback");
        assertThat(request.getConversationId()).isEqualTo(ORDER_ID.toString());
        assertThat(request.getBasketId()).isEqualTo(ORDER_ID.toString());
    }

    @Test
    void requestIncludesEnabledInstallmentsAndPhysicalBasketItems() {
        CreateCheckoutFormInitializeRequest request = DefaultIyzicoCheckoutClient.buildRequest(command("10.00"), properties());

        assertThat(request.getEnabledInstallments()).containsExactly(2, 3, 6, 9);
        assertThat(request.getDebitCardAllowed()).isTrue();
        assertThat(request.getBasketItems()).hasSize(2);
        assertThat(request.getBasketItems()).allSatisfy(item -> {
            assertThat(item.getItemType()).isEqualTo("PHYSICAL");
            assertThat(item.getCategory1()).isEqualTo("General");
        });
    }

    @Test
    void initializeVerifiesSignatureAndRejectsNonSuccessResponses() {
        Options options = new Options();
        options.setSecretKey("secret");
        AtomicReference<CreateCheckoutFormInitializeRequest> captured = new AtomicReference<>();
        DefaultIyzicoCheckoutClient client = new DefaultIyzicoCheckoutClient(properties(), options, (request, ignored) -> {
            captured.set(request);
            return checkoutResponse(Status.SUCCESS.getValue(), true, "token-1", "https://sandbox.iyzico/pay/token-1");
        });

        var result = client.initialize(command("10.00"));

        assertThat(captured.get()).isNotNull();
        assertThat(result.token()).isEqualTo("token-1");
        assertThat(result.paymentPageUrl()).isEqualTo("https://sandbox.iyzico/pay/token-1");

        DefaultIyzicoCheckoutClient badSignature = new DefaultIyzicoCheckoutClient(properties(), options,
            (request, ignored) -> checkoutResponse(Status.SUCCESS.getValue(), false, "token-1", "https://sandbox.iyzico/pay/token-1"));
        assertThatThrownBy(() -> badSignature.initialize(command("10.00")))
            .isInstanceOf(PaymentInitializationException.class)
            .hasMessageContaining("IYZICO_SIGNATURE_INVALID");

        DefaultIyzicoCheckoutClient failure = new DefaultIyzicoCheckoutClient(properties(), options,
            (request, ignored) -> checkoutResponse(Status.FAILURE.getValue(), true, null, null));
        assertThatThrownBy(() -> failure.initialize(command("10.00")))
            .isInstanceOf(PaymentInitializationException.class)
            .hasMessageContaining("IYZICO_INITIALIZE_FAILED");
    }

    @Test
    void basketSumEqualsRequestPriceAtScaleTwoAndRejectsMismatch() {
        CreateCheckoutFormInitializeRequest request = DefaultIyzicoCheckoutClient.buildRequest(command("10.00"), properties());

        BigDecimal basketSum = request.getBasketItems().stream()
            .map(item -> item.getPrice().setScale(2))
            .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        assertThat(request.getPrice()).isEqualByComparingTo("10.00");
        assertThat(basketSum).isEqualByComparingTo(request.getPrice());

        assertThatThrownBy(() -> DefaultIyzicoCheckoutClient.buildRequest(command("10.01"), properties()))
            .isInstanceOf(PaymentInitializationException.class)
            .hasMessageContaining("BASKET_TOTAL_MISMATCH");
    }

    @Test
    void everyBuyerFieldRequiredByOfficialSampleIsPopulated() {
        var buyer = DefaultIyzicoCheckoutClient.buildRequest(command("10.00"), properties()).getBuyer();

        assertThat(List.of(
            buyer.getId(), buyer.getName(), buyer.getSurname(), buyer.getGsmNumber(), buyer.getEmail(),
            buyer.getIdentityNumber(), buyer.getRegistrationAddress(), buyer.getCity(), buyer.getCountry(),
            buyer.getIp(), buyer.getZipCode(), buyer.getRegistrationDate(), buyer.getLastLoginDate()))
            .allSatisfy(value -> assertThat(value).isNotBlank());
        assertThat(buyer.getGsmNumber()).matches("^\\+90\\d{10}$");
        assertThat(buyer.getRegistrationDate()).isEqualTo("2026-04-30 10:15:30");
        assertThat(buyer.getLastLoginDate()).isEqualTo("2026-04-30 10:15:30");
    }

    private static IyzicoProperties properties() {
        return new IyzicoProperties("https://sandbox-api.iyzipay.com", "api", "secret", "https://demo.example.com", 15, "74300864791");
    }

    private static IyzicoCheckoutResult.CheckoutInitializationCommand command(String total) {
        var buyer = new IyzicoCheckoutResult.BuyerInput(
            "22222222-2222-2222-2222-222222222222", "Ayşe", "Yılmaz", "+90 (535) 000 00 00",
            "22222222-2222-2222-2222-222222222222@n11clone.local", "74300864791",
            "Merkez Mah. Test Sk. No:1, Kadıköy/İstanbul", "İstanbul", "Turkey", "34000", "127.0.0.1");
        var address = new IyzicoCheckoutResult.AddressInput(
            "Ayşe Yılmaz", "Merkez Mah. Test Sk. No:1, Kadıköy/İstanbul", "İstanbul", "Turkey", "34000");
        return new IyzicoCheckoutResult.CheckoutInitializationCommand(
            ORDER_ID,
            ORDER_ID.toString(),
            new BigDecimal(total),
            CREATED_AT,
            buyer,
            address,
            address,
            List.of(
                new IyzicoCheckoutResult.BasketItemInput(UUID.randomUUID().toString(), "Telefon", "General", null, "PHYSICAL", new BigDecimal("3.335")),
                new IyzicoCheckoutResult.BasketItemInput(UUID.randomUUID().toString(), "Kulaklık", "General", null, "PHYSICAL", new BigDecimal("6.665"))));
    }

    private static CheckoutFormInitialize checkoutResponse(String status, boolean validSignature, String token, String paymentPageUrl) {
        return new CheckoutFormInitialize() {
            @Override
            public boolean verifySignature(String secretKey) {
                return validSignature;
            }
        };
    }
}
