package com.n11.payment.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import com.n11.outbox.OutboxEvent;
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
    private static final String MOCK_PAYMENT_PREFIX = "mock-";

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
    public void persistAndPublish(UUID eventId, Envelope envelope,
                                  StockReservedConsumer.StockReservedPayload payload) {
        if (processedEventsRepository.existsById(eventId)) {
            LOG.debug("payment.saga: duplicate event {}, skipping", eventId);
            return;
        }

        UUID paymentId = UUID.randomUUID();
        String iyzicoPaymentId = MOCK_PAYMENT_PREFIX + paymentId;
        BigDecimal amount = payload.totalAmount();   // W4 closure — real order amount
        if (amount == null) {
            throw new IllegalStateException(
                "stock.reserved.totalAmount missing for event " + eventId
                + " — inventory-service must populate it from order.created");
        }

        paymentRepository.save(new Payment(paymentId, payload.orderId(), amount, "TRY",
                PaymentStatus.COMPLETED, iyzicoPaymentId));

        // Outbox: payment.completed envelope per payment-completed.schema.json
        UUID outEventId = UUID.randomUUID();
        PaymentCompletedPayload outPayload = new PaymentCompletedPayload(
                payload.orderId(), paymentId, iyzicoPaymentId, amount, "TRY");
        Envelope outEnv = new Envelope(
                outEventId.toString(),
                "payment.completed",
                1,
                Instant.now(),
                envelope.correlationId(),
                envelope.eventId(),
                "payment-service",
                objectMapper.valueToTree(outPayload)
        );
        try {
            String json = objectMapper.writeValueAsString(outEnv);
            outboxRepository.save(new OutboxEvent(outEventId, "payments", "payment.completed", json, Instant.now()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize payment.completed envelope", e);
        }

        processedEventsRepository.save(
                new ProcessedEvent(eventId, "StockReservedConsumer", envelope.eventType()));

        LOG.info("payment.saga: persisted payment {} for order {} amount={}", paymentId, payload.orderId(), amount);
    }

    public record PaymentCompletedPayload(
            UUID orderId,
            UUID paymentId,
            String iyzicoPaymentId,
            BigDecimal amount,
            String currency) {}
}
