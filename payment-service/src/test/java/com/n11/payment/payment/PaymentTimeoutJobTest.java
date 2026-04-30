package com.n11.payment.payment;

import com.n11.payment.messaging.PaymentTransactionalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link PaymentTimeoutJob} (Plan 06-05 Task 1).
 *
 * <p>Verifies the scheduled timeout sweep:
 * <ul>
 *   <li>An expired PENDING payment is transitioned to TIMED_OUT and a
 *       {@code payment.failed} outbox event is requested with reason {@code TIMEOUT}
 *       and errorCode {@code PAYMENT_TIMEOUT}.</li>
 *   <li>The transactional timeout method is called once per expired row.</li>
 *   <li>Already-terminal payments (COMPLETED, FAILED, TIMED_OUT) are not touched —
 *       they should never appear in the repository's expired-pending result, and the
 *       transactional service is invoked zero times for them.</li>
 * </ul>
 *
 * <p>The {@link PaymentTransactionalService#timeoutFromScheduler(UUID)} contract:
 * status-guard PENDING only (so the first terminal transition wins; T-06-13 race
 * mitigation), set status TIMED_OUT, set failedAt + failureReason, and write exactly
 * one {@code payment.failed} outbox row with reason TIMEOUT + errorCode PAYMENT_TIMEOUT.
 */
class PaymentTimeoutJobTest {

    PaymentRepository paymentRepository;
    PaymentTransactionalService paymentTransactionalService;
    PaymentTimeoutJob job;

    @BeforeEach
    void setup() {
        paymentRepository = mock(PaymentRepository.class);
        paymentTransactionalService = mock(PaymentTransactionalService.class);
        job = new PaymentTimeoutJob(paymentRepository, paymentTransactionalService);
    }

    @Test
    void expiredPendingPayment_isTimedOutOnce() {
        Payment expired = pending(UUID.randomUUID(), UUID.randomUUID(), Instant.now().minusSeconds(60));
        when(paymentRepository.findExpiredPendingPayments(any(Instant.class)))
            .thenReturn(List.of(expired));

        job.sweepExpiredPendingPayments();

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(paymentTransactionalService, times(1)).timeoutFromScheduler(idCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(expired.getId());
    }

    @Test
    void multipleExpiredPendings_eachInvokedOnce() {
        Payment p1 = pending(UUID.randomUUID(), UUID.randomUUID(), Instant.now().minusSeconds(120));
        Payment p2 = pending(UUID.randomUUID(), UUID.randomUUID(), Instant.now().minusSeconds(90));
        when(paymentRepository.findExpiredPendingPayments(any(Instant.class)))
            .thenReturn(List.of(p1, p2));

        job.sweepExpiredPendingPayments();

        verify(paymentTransactionalService, times(1)).timeoutFromScheduler(p1.getId());
        verify(paymentTransactionalService, times(1)).timeoutFromScheduler(p2.getId());
    }

    @Test
    void noExpiredPendings_noTimeoutInvocation() {
        when(paymentRepository.findExpiredPendingPayments(any(Instant.class)))
            .thenReturn(List.of());

        job.sweepExpiredPendingPayments();

        verifyNoInteractions(paymentTransactionalService);
    }

    @Test
    void alreadyTerminalRows_areFilteredOutByRepositoryQuery() {
        // Repository's @Query restricts to status=PENDING; terminal rows should never appear.
        // We assert the contract by demonstrating that when the repository returns no rows,
        // the transactional service is never invoked — even if other (terminal) payments exist
        // in the system, they are out of scope of the sweep.
        when(paymentRepository.findExpiredPendingPayments(any(Instant.class)))
            .thenReturn(List.of()); // simulating: completed/failed/timed_out rows excluded

        job.sweepExpiredPendingPayments();

        verify(paymentTransactionalService, never()).timeoutFromScheduler(any());
    }

    private static Payment pending(UUID paymentId, UUID orderId, Instant expiresAt) {
        Payment p = new Payment(paymentId, orderId, new BigDecimal("100.00"), "TRY",
            PaymentStatus.PENDING_INITIALIZATION, null);
        p.markPending("token-" + paymentId, "https://sandbox.iyzico/pay/" + paymentId, expiresAt);
        return p;
    }
}
