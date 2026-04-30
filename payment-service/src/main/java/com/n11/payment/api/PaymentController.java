package com.n11.payment.api;

import com.n11.payment.payment.Payment;
import com.n11.payment.payment.PaymentRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    private final PaymentRepository paymentRepository;

    public PaymentController(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
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
     * Request body for {@code POST /payments/checkout} ensure/fetch. Only the
     * {@code orderId} is needed — userId arrives via gateway-injected {@code X-User-Id}
     * but this controller does not need it because the lookup is by orderId.
     */
    public record EnsureCheckoutRequest(@NotNull UUID orderId) {
    }
}
