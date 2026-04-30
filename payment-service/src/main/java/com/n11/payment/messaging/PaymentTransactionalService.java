package com.n11.payment.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import com.n11.payment.iyzico.IyzicoCheckoutResult;
import com.n11.payment.order.OrderPaymentContext;
import com.n11.payment.outbox.PaymentOutboxRepository;
import com.n11.payment.payment.Payment;
import com.n11.payment.payment.PaymentRepository;
import com.n11.payment.payment.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * @Transactional persist + outbox-write for payment.completed.
 *
 * <p>Separate bean from {@link PaymentSagaService} so the AOP proxy is honored when invoked
 * cross-bean (Spring's @Transactional requires proxy invocation — self-invocation bypasses it).
 *
 * <p>Idempotency: processed_events check INSIDE @Transactional ensures redelivery → single
 * payments row + single outbox row (ARCH-07, CLAUDE.md Rule #3).
 *
 * <p>Amount sourced from {@code payload.totalAmount()} (W4 closure — never hardcoded).
 */
@Service
public class PaymentTransactionalService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentTransactionalService.class);
    private final ProcessedEventRepository processedEventsRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PaymentTransactionalService(ProcessedEventRepository processedEventsRepository,
                                       PaymentRepository paymentRepository,
                                       PaymentOutboxRepository outboxRepository,
                                       ObjectMapper objectMapper) {
        this.processedEventsRepository = processedEventsRepository;
        this.paymentRepository = paymentRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public boolean hasProcessedEventOrRecordActiveCheckout(UUID eventId, Envelope envelope, UUID orderId) {
        if (processedEventsRepository.existsById(eventId)) {
            LOG.debug("payment.saga: duplicate event {}, skipping", eventId);
            return true;
        }
        var active = paymentRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(orderId, PaymentStatus.PENDING);
        if (active.isPresent()) {
            processedEventsRepository.save(new ProcessedEvent(eventId, "StockReservedConsumer", envelope.eventType()));
            LOG.info("payment.saga: reused active pending checkout for order {} on event {}", orderId, eventId);
            return true;
        }
        return false;
    }

    @Transactional
    public void persistPendingCheckout(UUID eventId, Envelope envelope,
                                       StockReservedConsumer.StockReservedPayload payload,
                                       OrderPaymentContext context,
                                       IyzicoCheckoutResult.InitializedCheckout checkout,
                                       Instant expiresAt) {
        if (processedEventsRepository.existsById(eventId)) {
            LOG.debug("payment.saga: duplicate event {}, skipping pending persist", eventId);
            return;
        }
        var active = paymentRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(payload.orderId(), PaymentStatus.PENDING);
        if (active.isPresent()) {
            processedEventsRepository.save(new ProcessedEvent(eventId, "StockReservedConsumer", envelope.eventType()));
            return;
        }
        UUID paymentId = UUID.randomUUID();
        if (context.totalAmount() == null) {
            throw new IllegalStateException(
                "stock.reserved.totalAmount missing for event " + eventId
                + " — inventory-service must populate it from order.created");
        }

        Payment payment = new Payment(paymentId, payload.orderId(), context.totalAmount(), context.currency(),
                PaymentStatus.PENDING_INITIALIZATION, null);
        payment.markPending(checkout.token(), checkout.paymentPageUrl(), expiresAt);
        paymentRepository.save(payment);

        processedEventsRepository.save(
                new ProcessedEvent(eventId, "StockReservedConsumer", envelope.eventType()));

        LOG.info("payment.saga: persisted pending checkout {} for order {} amount={}", paymentId, payload.orderId(), context.totalAmount());
    }

    public record PaymentCompletedPayload(
            UUID orderId,
            UUID paymentId,
            String iyzicoPaymentId,
            BigDecimal amount,
            String currency) {}
}
