package com.n11.payment.iyzico;

import com.iyzipay.Options;
import com.iyzipay.model.Address;
import com.iyzipay.model.BasketItem;
import com.iyzipay.model.BasketItemType;
import com.iyzipay.model.Buyer;
import com.iyzipay.model.CheckoutForm;
import com.iyzipay.model.CheckoutFormInitialize;
import com.iyzipay.model.Currency;
import com.iyzipay.model.Locale;
import com.iyzipay.model.PaymentGroup;
import com.iyzipay.model.Status;
import com.iyzipay.request.CreateCheckoutFormInitializeRequest;
import com.iyzipay.request.RetrieveCheckoutFormRequest;
import com.n11.payment.order.PaymentInitializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Component
public class DefaultIyzicoCheckoutClient implements IyzicoCheckoutClient {

    private static final List<Integer> ENABLED_INSTALLMENTS = List.of(2, 3, 6, 9);
    private static final DateTimeFormatter IYZICO_DATE_FORMAT = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneOffset.UTC);

    private final IyzicoProperties properties;
    private final Options options;
    private final CheckoutInitializer initializer;
    private final CheckoutRetriever retriever;

    @Autowired
    public DefaultIyzicoCheckoutClient(IyzicoProperties properties, Options options) {
        this(properties, options, CheckoutFormInitialize::create, CheckoutForm::retrieve);
    }

    /** Convenience overload used by existing initialize-only tests (Plan 06-03). */
    DefaultIyzicoCheckoutClient(IyzicoProperties properties, Options options, CheckoutInitializer initializer) {
        this(properties, options, initializer, CheckoutForm::retrieve);
    }

    DefaultIyzicoCheckoutClient(IyzicoProperties properties, Options options,
                                CheckoutInitializer initializer, CheckoutRetriever retriever) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.options = Objects.requireNonNull(options, "options must not be null");
        this.initializer = Objects.requireNonNull(initializer, "initializer must not be null");
        this.retriever = Objects.requireNonNull(retriever, "retriever must not be null");
    }

    @Override
    public IyzicoCheckoutResult.InitializedCheckout initialize(
            IyzicoCheckoutResult.CheckoutInitializationCommand command) {
        CreateCheckoutFormInitializeRequest request = buildRequest(command, properties);
        CheckoutFormInitialize response = initializer.create(request, options);
        if (response == null) {
            throw new PaymentInitializationException("IYZICO_INITIALIZE_FAILED: empty response");
        }
        // Status check first — Iyzico returns no signature on auth/validation errors,
        // so verifySignature would always fail and mask the real error.
        if (!Status.SUCCESS.getValue().equals(response.getStatus())) {
            throw new PaymentInitializationException("IYZICO_INITIALIZE_FAILED: status=" + safe(response.getStatus())
                + " errorCode=" + safe(response.getErrorCode())
                + " errorMessage=" + safe(response.getErrorMessage()));
        }
        if (!response.verifySignature(options.getSecretKey())) {
            throw new PaymentInitializationException("IYZICO_SIGNATURE_INVALID: token=" + safe(response.getToken())
                + " conversationId=" + safe(response.getConversationId())
                + " signaturePresent=" + (response.getSignature() != null));
        }
        if (isBlank(response.getToken()) || isBlank(response.getPaymentPageUrl())) {
            throw new PaymentInitializationException("IYZICO_INITIALIZE_FAILED: missing token or paymentPageUrl");
        }
        return new IyzicoCheckoutResult.InitializedCheckout(
            response.getToken(),
            response.getPaymentPageUrl(),
            response.getStatus(),
            response.getErrorCode(),
            response.getErrorMessage());
    }

    @Override
    public IyzicoCheckoutResult.RetrievedCheckout retrieve(String token, String conversationId) {
        if (isBlank(token)) {
            throw new PaymentInitializationException("IYZICO_RETRIEVE_FAILED: token missing");
        }
        RetrieveCheckoutFormRequest request = new RetrieveCheckoutFormRequest();
        request.setLocale(Locale.TR.getValue());
        request.setConversationId(conversationId);
        request.setToken(token);

        CheckoutForm response;
        try {
            response = retriever.retrieve(request, options);
        } catch (RuntimeException ex) {
            throw new PaymentInitializationException("IYZICO_RETRIEVE_FAILED: " + ex.getClass().getSimpleName(), ex);
        }
        if (response == null) {
            throw new PaymentInitializationException("IYZICO_RETRIEVE_FAILED: empty response");
        }

        boolean apiSuccess = Status.SUCCESS.getValue().equals(response.getStatus());
        if (!apiSuccess) {
            // Iyzico returns status=failure for two distinct cases:
            //   (a) Transport/auth/schema failure — the payment was never processed.
            //       paymentStatus and paymentId are absent; the errorCode is an API-level
            //       code (10xxx). Surface as IYZICO_RETRIEVE_FAILED so the saga
            //       compensates with reason=UNKNOWN.
            //   (b) Domain payment failure — Iyzico processed the payment and the issuer
            //       (or 3DS step) rejected it. paymentStatus and/or paymentId are
            //       populated. Fall through; IyzicoCallbackOutcome.fromRetrieve will map
            //       the granular taxonomy (IYZICO_DECLINED, IYZICO_FRAUD_REVIEW,
            //       IYZICO_3DS_MDSTATUS_INVALID) downstream.
            boolean domainFailure = !isBlank(response.getPaymentStatus()) || !isBlank(response.getPaymentId());
            if (!domainFailure) {
                throw new PaymentInitializationException("IYZICO_RETRIEVE_FAILED: " + safe(response.getErrorCode()));
            }
            // Domain failure: skip signature verification — Iyzico does not sign decline
            // responses, and this is a server-to-server TLS hop so signature is
            // belt-and-braces, not the only line of defence.
        } else if (!response.verifySignature(options.getSecretKey())) {
            throw new PaymentInitializationException("IYZICO_SIGNATURE_INVALID: token=" + safe(response.getToken())
                + " conversationId=" + safe(response.getConversationId())
                + " signaturePresent=" + (response.getSignature() != null));
        }

        return new IyzicoCheckoutResult.RetrievedCheckout(
            response.getToken(),
            response.getPaymentId(),
            response.getPaymentStatus(),
            response.getFraudStatus(),
            response.getErrorCode(),
            response.getErrorMessage());
    }

    static CreateCheckoutFormInitializeRequest buildRequest(
            IyzicoCheckoutResult.CheckoutInitializationCommand command,
            IyzicoProperties properties) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(properties, "properties must not be null");

        List<BasketItem> basketItems = command.items().stream()
            .map(DefaultIyzicoCheckoutClient::toBasketItem)
            .toList();
        BigDecimal basketTotal = basketItems.stream()
            .map(BasketItem::getPrice)
            .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal requestedAmount = money(command.amount());
        if (basketTotal.compareTo(requestedAmount) != 0) {
            throw new PaymentInitializationException("BASKET_TOTAL_MISMATCH");
        }

        CreateCheckoutFormInitializeRequest request = new CreateCheckoutFormInitializeRequest();
        request.setLocale(Locale.TR.getValue());
        request.setConversationId(command.conversationId());
        request.setPrice(basketTotal);
        request.setPaidPrice(basketTotal);
        request.setCurrency(Currency.TRY.name());
        request.setBasketId(command.orderId().toString());
        request.setPaymentGroup(PaymentGroup.PRODUCT.name());
        request.setCallbackUrl(properties.callbackUrl());
        request.setEnabledInstallments(ENABLED_INSTALLMENTS);
        request.setDebitCardAllowed(Boolean.TRUE);
        request.setBuyer(toBuyer(command.buyer(), command.createdAt()));
        request.setShippingAddress(toAddress(command.shippingAddress()));
        request.setBillingAddress(toAddress(command.billingAddress()));
        request.setBasketItems(basketItems);
        return request;
    }

    private static Buyer toBuyer(IyzicoCheckoutResult.BuyerInput input, Instant createdAt) {
        Buyer buyer = new Buyer();
        buyer.setId(required(input.id(), "buyer.id"));
        buyer.setName(required(input.name(), "buyer.name"));
        buyer.setSurname(required(input.surname(), "buyer.surname"));
        buyer.setGsmNumber(normalizeTurkishGsm(input.gsmNumber()));
        buyer.setEmail(required(input.email(), "buyer.email"));
        buyer.setIdentityNumber(required(input.identityNumber(), "buyer.identityNumber"));
        buyer.setRegistrationAddress(required(input.registrationAddress(), "buyer.registrationAddress"));
        buyer.setCity(required(input.city(), "buyer.city"));
        buyer.setCountry(required(input.country(), "buyer.country"));
        buyer.setZipCode(defaultIfBlank(input.zipCode(), "34000"));
        buyer.setIp(defaultIfBlank(input.ip(), "127.0.0.1"));
        String formattedCreatedAt = IYZICO_DATE_FORMAT.format(createdAt == null ? Instant.now() : createdAt);
        buyer.setRegistrationDate(formattedCreatedAt);
        buyer.setLastLoginDate(formattedCreatedAt);
        return buyer;
    }

    private static Address toAddress(IyzicoCheckoutResult.AddressInput input) {
        Address address = new Address();
        address.setContactName(required(input.contactName(), "address.contactName"));
        address.setAddress(required(input.address(), "address.address"));
        address.setCity(required(input.city(), "address.city"));
        address.setCountry(required(input.country(), "address.country"));
        address.setZipCode(defaultIfBlank(input.zipCode(), "34000"));
        return address;
    }

    private static BasketItem toBasketItem(IyzicoCheckoutResult.BasketItemInput input) {
        BasketItem item = new BasketItem();
        item.setId(required(input.id(), "basketItem.id"));
        item.setName(required(input.name(), "basketItem.name"));
        item.setCategory1(defaultIfBlank(input.category1(), "General"));
        item.setCategory2(input.category2());
        item.setItemType(defaultIfBlank(input.itemType(), BasketItemType.PHYSICAL.name()));
        item.setPrice(money(input.price()));
        return item;
    }

    static BigDecimal money(BigDecimal value) {
        if (value == null) {
            throw new PaymentInitializationException("MONEY_VALUE_MISSING");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    static String normalizeTurkishGsm(String raw) {
        String digits = required(raw, "buyer.gsmNumber").replaceAll("\\D", "");
        if (digits.length() == 10) {
            return "+90" + digits;
        }
        if (digits.length() == 11 && digits.startsWith("0")) {
            return "+90" + digits.substring(1);
        }
        if ((digits.length() == 11 || digits.length() == 12) && digits.startsWith("90")) {
            return "+" + digits;
        }
        throw new PaymentInitializationException("BUYER_GSM_INVALID");
    }

    private static String required(String value, String field) {
        if (isBlank(value)) {
            throw new PaymentInitializationException(field + " is required");
        }
        return value.trim();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String safe(String value) {
        return isBlank(value) ? "unknown" : value;
    }

    @FunctionalInterface
    interface CheckoutInitializer {
        CheckoutFormInitialize create(CreateCheckoutFormInitializeRequest request, Options options);
    }

    @FunctionalInterface
    interface CheckoutRetriever {
        CheckoutForm retrieve(RetrieveCheckoutFormRequest request, Options options);
    }
}
