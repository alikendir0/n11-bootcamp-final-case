package com.n11.payment.api;

import com.n11.payment.iyzico.IyzicoCheckoutClient;
import com.n11.payment.iyzico.IyzicoCheckoutResult;
import com.n11.payment.messaging.PaymentTransactionalService;
import com.n11.payment.order.PaymentInitializationException;
import com.n11.payment.payment.Payment;
import com.n11.payment.payment.PaymentRepository;
import com.n11.payment.payment.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/**
 * Public payment status / link API for clients (D-06, D-08).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /payments/{orderId}} — fetch payment status / hosted Checkout Form link.
 *       Returns 202 {@code PENDING_INITIALIZATION} while the saga is still initializing
 *       the Iyzico checkout (D-08); 200 with {@code paymentPageUrl} once ready.</li>
 *   <li>{@code POST /payments/checkout} — kept for {@code api-contracts.md} compatibility.
 *       It is an idempotent ensure/fetch operation only: it never creates a second
 *       independent checkout session. The actual checkout is initialized by the
 *       {@code stock.reserved} saga consumer (Plan 06-03 D-05).</li>
 * </ul>
 *
 * <p>The response payload never includes Iyzico's SDK embedded-form HTML
 * (D-07 — Phase 6 hosted-redirect only). Phase 10 may revisit embed vs redirect at the
 * UI layer.
 *
 * <p>Auth: gateway validates the JWT and injects {@code X-User-Id} per
 * {@code api-contracts.md} §4. This controller does not need the raw token —
 * lookup is by {@code orderId} only. The Iyzico callback path is exposed
 * separately by {@link PaymentController#iyzicoCallback(String)}.
 */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentRepository paymentRepository;
    private final IyzicoCheckoutClient iyzicoCheckoutClient;
    private final PaymentTransactionalService paymentTransactionalService;

    public PaymentController(PaymentRepository paymentRepository,
                             IyzicoCheckoutClient iyzicoCheckoutClient,
                             PaymentTransactionalService paymentTransactionalService) {
        this.paymentRepository = paymentRepository;
        this.iyzicoCheckoutClient = iyzicoCheckoutClient;
        this.paymentTransactionalService = paymentTransactionalService;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<PaymentStatusResponse> getPaymentForOrder(@PathVariable("orderId") UUID orderId) {
        return resolveStatus(orderId);
    }

    @PostMapping("/checkout")
    public ResponseEntity<PaymentStatusResponse> ensureCheckout(@RequestBody @NotNull EnsureCheckoutRequest body) {
        // Idempotent ensure/fetch — does not create a second checkout. The
        // saga (stock.reserved consumer) is the sole producer of new checkout sessions.
        return resolveStatus(body.orderId());
    }

    private ResponseEntity<PaymentStatusResponse> resolveStatus(UUID orderId) {
        Optional<Payment> latest = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId);
        if (latest.isEmpty()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                new PaymentStatusResponse(orderId, "PENDING_INITIALIZATION", null, null, null));
        }
        Payment payment = latest.get();
        return ResponseEntity.ok(new PaymentStatusResponse(
            payment.getOrderId(),
            payment.getStatus().name(),
            payment.getPaymentPageUrl(),
            payment.getFailureReason(),
            payment.getUpdatedAt()
        ));
    }

    /**
     * Iyzico Checkout Form callback (PAY-04, PAY-05). Public — gateway allowlist exempts
     * this path from JWT enforcement; authenticity is verified server-side via
     * {@code CheckoutForm.retrieve} + SDK signature, NOT by trusting the form body.
     *
     * <p>Wire shape: Iyzico's hosted page POSTs the buyer's browser back to the merchant
     * with {@code application/x-www-form-urlencoded} and a single {@code token} field.
     * The S2S webhook (separate JSON-bodied endpoint) is out of scope for Phase 6.
     *
     * <p>Response: 200 OK with a minimal {@code text/html} confirmation page so the
     * buyer's browser renders something readable on the hosted Iyzico flow's exit hop.
     * The page is intentionally generic — clients should poll {@code GET /payments/{orderId}}
     * for the canonical status.
     */
    @PostMapping(path = "/iyzico/callback", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
        produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> iyzicoCallback(@RequestParam("token") String token) {
        if (token == null || token.isBlank()) {
            LOG.warn("payment.callback: missing token");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.TEXT_HTML)
                .body(htmlPage("Eksik token", "Ödeme doğrulaması başarısız."));
        }

        Optional<Payment> opt = paymentRepository.findByIyzicoToken(token);
        if (opt.isEmpty()) {
            LOG.warn("payment.callback: unknown token={}", token);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.TEXT_HTML)
                .body(htmlPage("Bilinmeyen ödeme", "Bu ödeme oturumu bulunamadı."));
        }
        Payment payment = opt.get();

        // Already-terminal callback redelivery is a no-op (idempotent path; T-06-10 mitigation).
        if (payment.getStatus() != PaymentStatus.PENDING) {
            LOG.info("payment.callback: payment {} already {}, returning current status",
                payment.getId(), payment.getStatus());
            return successPageFor(payment);
        }

        IyzicoCallbackOutcome outcome;
        try {
            IyzicoCheckoutResult.RetrievedCheckout retrieve =
                iyzicoCheckoutClient.retrieve(token, payment.getOrderId().toString());
            outcome = IyzicoCallbackOutcome.fromRetrieve(retrieve);
        } catch (PaymentInitializationException ex) {
            LOG.warn("payment.callback: retrieve failed for payment {}: {}", payment.getId(), ex.getMessage());
            outcome = IyzicoCallbackOutcome.retrieveFailed(ex.getMessage());
        } catch (RuntimeException ex) {
            LOG.error("payment.callback: unexpected retrieve error for payment {}", payment.getId(), ex);
            outcome = IyzicoCallbackOutcome.retrieveFailed(ex.getClass().getSimpleName());
        }

        if (outcome.isCompleted()) {
            paymentTransactionalService.completeFromCallback(payment.getId(), outcome.iyzicoPaymentId());
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(htmlPage("Ödeme tamamlandı", "Siparişiniz onaylandı, teşekkürler."));
        }
        paymentTransactionalService.failFromCallback(payment.getId(), outcome.reason(), outcome.errorCode());
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(htmlPage("Ödeme başarısız", "Lütfen başka bir kart ile tekrar deneyin."));
    }

    private ResponseEntity<String> successPageFor(Payment payment) {
        String title = switch (payment.getStatus()) {
            case COMPLETED -> "Ödeme tamamlandı";
            case FAILED -> "Ödeme başarısız";
            case TIMED_OUT -> "Ödeme zaman aşımına uğradı";
            default -> "Ödeme durumu";
        };
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(htmlPage(title, "Sipariş #" + payment.getOrderId()));
    }

    private static String htmlPage(String title, String body) {
        // Minimal, escape-safe page (no buyer input is interpolated).
        return "<!doctype html><html lang=\"tr\"><head><meta charset=\"utf-8\"><title>"
            + title + "</title></head><body><h1>" + title + "</h1><p>" + body + "</p></body></html>";
    }

    /**
     * Request body for {@code POST /payments/checkout} ensure/fetch. Only the
     * {@code orderId} is needed — userId arrives via gateway-injected {@code X-User-Id}
     * but this controller does not need it because the lookup is by orderId.
     */
    public record EnsureCheckoutRequest(@NotNull UUID orderId) {
    }
}
