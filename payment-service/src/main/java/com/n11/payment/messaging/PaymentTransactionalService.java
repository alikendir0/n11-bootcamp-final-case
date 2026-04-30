package com.n11.payment.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import com.n11.outbox.OutboxEvent;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Transactional persistence boundary for payment saga side effects.
 *
 * <p>Separate bean from {@link PaymentSagaService} so the AOP proxy is honored when invoked
 * cross-bean (Spring's @Transactional requires proxy invocation — self-invocation bypasses it).
 *
 * <p>Idempotency: processed_events check INSIDE @Transactional ensures redelivery → single
 * active checkout row (ARCH-07, CLAUDE.md Rule #3).
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

    /**
     * Mark a PENDING payment COMPLETED from a verified Iyzico callback. Idempotent —
     * if the payment is already terminal, no second outbox event is written.
     *
     * @return {@code true} if the payment was transitioned (one outbox row written);
     *         {@code false} if the row was already terminal (no-op).
     */
    @Transactional
    public boolean completeFromCallback(UUID paymentId, String iyzicoPaymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() ->
            new IllegalStateException("payment.callback: payment row missing for " + paymentId));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            LOG.info("payment.callback: payment {} already terminal ({}), skipping completion", paymentId, payment.getStatus());
            return false;
        }
        payment.markCompleted(iyzicoPaymentId);
        paymentRepository.save(payment);
        publishPaymentCompleted(payment, iyzicoPaymentId);
        LOG.info("payment.callback: payment {} completed (iyzicoPaymentId={})", paymentId, iyzicoPaymentId);
        return true;
    }

    /**
     * Mark a PENDING payment FAILED from a verified Iyzico callback. Idempotent —
     * if the payment is already terminal, no second outbox event is written.
     *
     * <p>{@code reason} must be a value from the {@code payment.failed} schema enum
     * ({@code DECLINED}, {@code FRAUD}, {@code TIMEOUT}, {@code INSUFFICIENT_FUNDS},
     * {@code UNKNOWN}). The granular Iyzico taxonomy code (e.g. {@code IYZICO_DECLINED},
     * {@code IYZICO_3DS_MDSTATUS_INVALID}) goes into the {@code errorCode} payload field
     * and the row's {@code failure_code} column.
     *
     * @return {@code true} if the payment was transitioned (one outbox row written);
     *         {@code false} if the row was already terminal (no-op).
     */
    @Transactional
    public boolean failFromCallback(UUID paymentId, String reason, String errorCode) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() ->
            new IllegalStateException("payment.callback: payment row missing for " + paymentId));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            LOG.info("payment.callback: payment {} already terminal ({}), skipping failure", paymentId, payment.getStatus());
            return false;
        }
        payment.markFailed(reason, errorCode);
        paymentRepository.save(payment);
        publishPaymentFailed(payment, reason, errorCode);
        LOG.info("payment.callback: payment {} failed reason={} errorCode={}", paymentId, reason, errorCode);
        return true;
    }

    private void publishPaymentCompleted(Payment payment, String iyzicoPaymentId) {
        UUID eventId = UUID.randomUUID();
        // Saga schema requires (orderId, paymentId, iyzicoPaymentId, amount, currency).
        // BigDecimal serialization without trailing scale noise: keep amount.scale=2.
        BigDecimal amount = payment.getAmount();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", payment.getOrderId().toString());
        payload.put("paymentId", payment.getId().toString());
        payload.put("iyzicoPaymentId", iyzicoPaymentId);
        payload.put("amount", amount);
        payload.put("currency", payment.getCurrency());

        Envelope envelope = new Envelope(
            eventId.toString(), "payment.completed", 1, Instant.now(),
            payment.getOrderId().toString(),
            null,
            "payment-service",
            objectMapper.valueToTree(payload));
        try {
            String json = objectMapper.writeValueAsString(envelope);
            outboxRepository.save(new OutboxEvent(eventId, "payments", "payment.completed", json, Instant.now()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize payment.completed envelope", e);
        }
    }

    private void publishPaymentFailed(Payment payment, String reason, String errorCode) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", payment.getOrderId().toString());
        payload.put("paymentId", payment.getId().toString());
        payload.put("reason", reason);
        payload.put("errorCode", errorCode);

        Envelope envelope = new Envelope(
            eventId.toString(), "payment.failed", 1, Instant.now(),
            payment.getOrderId().toString(),
            null,
            "payment-service",
            objectMapper.valueToTree(payload));
        try {
            String json = objectMapper.writeValueAsString(envelope);
            outboxRepository.save(new OutboxEvent(eventId, "payments", "payment.failed", json, Instant.now()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize payment.failed envelope", e);
        }
    }

}
