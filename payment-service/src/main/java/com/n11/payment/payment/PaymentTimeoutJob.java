package com.n11.payment.payment;

import com.n11.payment.messaging.PaymentTransactionalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled sweep of expired PENDING Iyzico checkout sessions (PAY-06 timeout compensation).
 *
 * <p>A buyer can abandon the hosted Iyzico Checkout Form (close the tab, lose connectivity,
 * walk away). Without a timeout sweep, the order's reserved stock would sit indefinitely
 * — denying inventory to other shoppers and leaving the saga in a stuck PENDING state.
 *
 * <p>Each sweep selects payments where {@code status = PENDING AND expires_at < now()}
 * (see {@link PaymentRepository#findExpiredPendingPayments(Instant)}) and delegates the
 * terminal transition to {@link PaymentTransactionalService#timeoutFromScheduler(java.util.UUID)},
 * which is status-guarded ({@code PENDING}-only) so a race with a late Iyzico callback
 * cannot double-emit (T-06-13 first-writer-wins).
 *
 * <p>Schedule cadence is configurable via {@code payment.timeout.scan-delay-ms}
 * (default {@code 60000} ms). The default is intentionally coarse: a one-minute lag
 * before stock is released is acceptable, and a faster cadence would burn DB cycles
 * for negligible UX gain.
 */
@Component
public class PaymentTimeoutJob {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentTimeoutJob.class);

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionalService paymentTransactionalService;

    public PaymentTimeoutJob(PaymentRepository paymentRepository,
                             PaymentTransactionalService paymentTransactionalService) {
        this.paymentRepository = paymentRepository;
        this.paymentTransactionalService = paymentTransactionalService;
    }

    /**
     * Sweep expired PENDING checkouts and transition each to TIMED_OUT.
     *
     * <p>One-shot per scheduled tick: if 50 rows are expired, 50 transactional method calls
     * fire (each with its own status-guarded transaction). This keeps the per-payment
     * transaction tiny and avoids long-running locks on the {@code payments} table.
     */
    @Scheduled(fixedDelayString = "${payment.timeout.scan-delay-ms:60000}",
               initialDelayString = "${payment.timeout.scan-delay-ms:60000}")
    public void sweepExpiredPendingPayments() {
        List<Payment> expired = paymentRepository.findExpiredPendingPayments(Instant.now());
        if (expired.isEmpty()) {
            LOG.trace("payment.timeout.sweep: no expired pending checkouts");
            return;
        }
        LOG.info("payment.timeout.sweep: {} expired pending checkouts", expired.size());
        for (Payment p : expired) {
            try {
                paymentTransactionalService.timeoutFromScheduler(p.getId());
            } catch (RuntimeException ex) {
                // Don't let one bad row break the whole sweep — log and move on.
                LOG.error("payment.timeout.sweep: failed to time out payment {}", p.getId(), ex);
            }
        }
    }
}
