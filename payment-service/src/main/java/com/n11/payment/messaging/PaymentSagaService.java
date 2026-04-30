package com.n11.payment.messaging;

import com.n11.events.Envelope;
import com.n11.payment.iyzico.IyzicoCheckoutClient;
import com.n11.payment.iyzico.IyzicoCheckoutResult;
import com.n11.payment.iyzico.IyzicoProperties;
import com.n11.payment.order.OrderPaymentContext;
import com.n11.payment.order.OrderPaymentContextClient;
import com.n11.payment.order.PaymentInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment saga orchestration: consume stock.reserved, fetch the order payment snapshot, initialize
 * Iyzico Checkout Form, then persist a reusable PENDING checkout link.
 *
 * <p>NOT @Transactional — external REST/SDK calls happen outside the database transaction and the
 * transactional side effects are delegated to {@link PaymentTransactionalService}.
 */
@Service
public class PaymentSagaService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentSagaService.class);

    private final PaymentTransactionalService paymentTransactionalService;
    private final OrderPaymentContextClient orderPaymentContextClient;
    private final IyzicoCheckoutClient iyzicoCheckoutClient;
    private final IyzicoProperties iyzicoProperties;
    private final long mockDelayMs;

    public PaymentSagaService(PaymentTransactionalService paymentTransactionalService,
                              OrderPaymentContextClient orderPaymentContextClient,
                              IyzicoCheckoutClient iyzicoCheckoutClient,
                              IyzicoProperties iyzicoProperties,
                               @Value("${mock.payment.delay-ms:100}") long mockDelayMs) {
        this.paymentTransactionalService = paymentTransactionalService;
        this.orderPaymentContextClient = orderPaymentContextClient;
        this.iyzicoCheckoutClient = iyzicoCheckoutClient;
        this.iyzicoProperties = iyzicoProperties;
        this.mockDelayMs = mockDelayMs;
    }

    public void processStockReserved(UUID eventId, Envelope envelope,
                                     StockReservedConsumer.StockReservedPayload payload) {
        // Mock delay BEFORE @Transactional opens (Pitfall 1: don't sleep inside tx).
        if (mockDelayMs > 0) {
            try {
                Thread.sleep(mockDelayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        LOG.debug("payment.saga: processing stock.reserved event {} for order {}", eventId, payload.orderId());
        if (paymentTransactionalService.hasProcessedEventOrRecordActiveCheckout(eventId, envelope, payload.orderId())) {
            return;
        }
        OrderPaymentContext context = orderPaymentContextClient.getPaymentContext(payload.orderId());
        IyzicoCheckoutResult.InitializedCheckout checkout = iyzicoCheckoutClient.initialize(toCommand(context));
        paymentTransactionalService.persistPendingCheckout(
            eventId, envelope, payload, context, checkout, Instant.now().plusSeconds(iyzicoProperties.timeoutMinutes() * 60));
    }

    private IyzicoCheckoutResult.CheckoutInitializationCommand toCommand(OrderPaymentContext context) {
        OrderPaymentContext.ShippingAddress shipping = context.shippingAddress();
        String fullName = defaultIfBlank(shipping.recipientName(), "N11 Müşteri");
        String name = firstName(fullName);
        String surname = surname(fullName);
        String addressLine = addressLine(shipping);
        var buyer = new IyzicoCheckoutResult.BuyerInput(
            context.userId().toString(),
            name,
            surname,
            shipping.phone(),
            context.userId() + "@buyer.example.com",
            iyzicoProperties.demoBuyerIdentityNumber(),
            addressLine,
            shipping.il(),
            "Turkey",
            defaultIfBlank(shipping.postalCode(), "34000"),
            "127.0.0.1");
        var address = new IyzicoCheckoutResult.AddressInput(
            fullName,
            addressLine,
            shipping.il(),
            "Turkey",
            defaultIfBlank(shipping.postalCode(), "34000"));
        var items = context.items().stream()
            .map(item -> new IyzicoCheckoutResult.BasketItemInput(
                item.productId().toString(),
                item.nameSnapshot(),
                "General",
                null,
                "PHYSICAL",
                item.unitPrice().multiply(BigDecimal.valueOf(item.qty()))))
            .toList();
        return new IyzicoCheckoutResult.CheckoutInitializationCommand(
            context.orderId(),
            context.orderId().toString(),
            context.totalAmount(),
            context.createdAt(),
            buyer,
            address,
            address,
            items);
    }

    private static String addressLine(OrderPaymentContext.ShippingAddress shipping) {
        return String.join(" ",
            defaultIfBlank(shipping.mahalle(), "Mahalle"),
            defaultIfBlank(shipping.streetLine(), "Adres"))
            + ", " + defaultIfBlank(shipping.ilce(), "İlçe") + "/" + defaultIfBlank(shipping.il(), "İstanbul");
    }

    private static String firstName(String fullName) {
        int idx = fullName.trim().lastIndexOf(' ');
        return idx <= 0 ? fullName.trim() : fullName.trim().substring(0, idx);
    }

    private static String surname(String fullName) {
        int idx = fullName.trim().lastIndexOf(' ');
        return idx <= 0 ? "Müşteri" : defaultIfBlank(fullName.trim().substring(idx + 1), "Müşteri");
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
