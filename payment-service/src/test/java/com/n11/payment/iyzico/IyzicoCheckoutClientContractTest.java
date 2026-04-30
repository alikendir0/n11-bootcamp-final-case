package com.n11.payment.iyzico;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IyzicoCheckoutClientContractTest {

    @Test
    void checkoutClientExposesInitializeAndRetrieveWithoutDomainOrControllerTypes() throws NoSuchMethodException {
        Method[] methods = IyzicoCheckoutClient.class.getDeclaredMethods();

        assertThat(Arrays.stream(methods).map(Method::getName)).containsExactlyInAnyOrder("initialize", "retrieve");
        assertThat(IyzicoCheckoutClient.class.getMethod("initialize", IyzicoCheckoutResult.CheckoutInitializationCommand.class)
            .getReturnType()).isEqualTo(IyzicoCheckoutResult.InitializedCheckout.class);
        assertThat(IyzicoCheckoutClient.class.getMethod("retrieve", String.class, String.class)
            .getReturnType()).isEqualTo(IyzicoCheckoutResult.RetrievedCheckout.class);

        assertThat(Arrays.stream(methods)
            .flatMap(method -> Arrays.stream(method.getParameterTypes()))
            .map(Class::getName))
            .noneMatch(name -> name.contains(".payment.Payment") || name.contains("Controller") || name.contains("Dto"));
    }

    @Test
    void resultRecordsCarryNeutralCheckoutFieldsOnly() {
        var initialized = new IyzicoCheckoutResult.InitializedCheckout(
            "token", "https://sandbox.iyzico/payment/token", "success", null, null);
        var retrieved = new IyzicoCheckoutResult.RetrievedCheckout(
            "token", "provider-payment-id", "success", 1, null, null);

        assertThat(initialized.token()).isEqualTo("token");
        assertThat(initialized.paymentPageUrl()).startsWith("https://");
        assertThat(retrieved.paymentId()).isEqualTo("provider-payment-id");
        assertThat(retrieved.status()).isEqualTo("success");
        assertThat(retrieved.fraudStatus()).isEqualTo(1);
        assertThat(retrieved.errorCode()).isNull();
        assertThat(retrieved.errorMessage()).isNull();

        assertThat(Arrays.stream(IyzicoCheckoutResult.class.getDeclaredClasses())
            .flatMap(type -> Arrays.stream(type.getRecordComponents()))
            .map(component -> component.getName()))
            .doesNotContain("checkoutFormContent");
    }

    @Test
    void commandRecordContainsOrderBuyerAddressAndItemInputs() {
        var buyer = new IyzicoCheckoutResult.BuyerInput(
            "buyer-1", "Ali", "Veli", "+905350000000", "ali@example.com", "74300864791",
            "Adres", "Istanbul", "Turkey", "34000", "127.0.0.1");
        var address = new IyzicoCheckoutResult.AddressInput("Ali Veli", "Adres", "Istanbul", "Turkey", "34000");
        var item = new IyzicoCheckoutResult.BasketItemInput(
            "item-1", "Ürün", "Kategori", "Alt Kategori", "PHYSICAL", new BigDecimal("10.00"));
        var command = new IyzicoCheckoutResult.CheckoutInitializationCommand(
            UUID.randomUUID(), "conversation-1", new BigDecimal("10.00"), buyer, address, address, java.util.List.of(item));

        assertThat(command.amount()).isEqualByComparingTo("10.00");
        assertThat(command.buyer().email()).isEqualTo("ali@example.com");
        assertThat(command.items()).hasSize(1);
    }
}
