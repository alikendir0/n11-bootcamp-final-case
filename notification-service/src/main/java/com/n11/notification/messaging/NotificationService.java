package com.n11.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.n11.events.Envelope;
import com.n11.notification.domain.Notification;
import com.n11.notification.messaging.payloads.OrderCancelledPayload;
import com.n11.notification.messaging.payloads.OrderConfirmedPayload;
import com.n11.notification.messaging.payloads.PaymentFailedPayload;
import com.n11.notification.messaging.payloads.UserRegisteredPayload;
import com.n11.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional gatekeeper for all 4 notification event types.
 *
 * <p>Each transactional handler method follows the invariant sequence:
 * <ol>
 *   <li>Idempotency check via processedEventRepository — FIRST statement
 *       (CLAUDE.md Rule #3, saga-contracts.md §5.2)</li>
 *   <li>Notifications audit row INSERT ({@code notifications} table — ARCHITECTURE.md §2.10 + §6 with
 *       correlation_id, payload_json JSONB blob)</li>
 *   <li>Processed events row INSERT ({@code processed_events} table — idempotency inbox)</li>
 *   <li>Structured INFO log line (NOTIF-02 contract: keys {@code notification.sent},
 *       {@code recipient}, {@code subject}, {@code correlationId}, {@code eventType}, {@code channel})</li>
 * </ol>
 *
 * <p>This class is NOT a {@code @RabbitListener} — it is the delegate called by the 4
 * consumer classes. The listener/delegate split is mandatory because Transactional
 * on a {@code @RabbitListener} method is bypassed by Spring AMQP's container thread
 * (Plan 04-02 STATE.md lesson).
 */
@Service
public class NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);
    private static final String CHANNEL = "EMAIL";
    private static final String STATUS_SENT = "SENT";

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public NotificationService(ProcessedEventRepository processedEventRepository,
                               NotificationRepository notificationRepository,
                               ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles {@code order.confirmed} events.
     * Idempotency check is FIRST (CLAUDE.md Rule #3). Audit row uses payload.userId() as recipient.
     */
    @Transactional
    public void handleOrderConfirmed(UUID eventId, Envelope envelope, OrderConfirmedPayload payload) {
        if (processedEventRepository.existsById(eventId)) {
            LOG.debug("notification.service: duplicate event {}, skipping", eventId);
            return;
        }
        String subject = NotificationTemplates.SUBJECT_ORDER_CONFIRMED;
        String bodyTr  = NotificationTemplates.bodyOrderConfirmed(payload);
        UUID correlationId = resolveCorrelationId(envelope, eventId);
        String payloadJson = renderPayloadJson(subject, bodyTr, envelope, payload);
        Notification n = new Notification(
            UUID.randomUUID(), payload.userId(), correlationId,
            CHANNEL, "order.confirmed", payloadJson, STATUS_SENT, Instant.now());
        notificationRepository.save(n);
        processedEventRepository.save(
            new ProcessedEvent(eventId, "OrderConfirmedConsumer", envelope.eventType()));
        LOG.info("notification.sent recipient={} subject=\"{}\" correlationId={} eventType=order.confirmed channel={}",
            payload.userId(), subject, envelope.correlationId(), CHANNEL);
    }

    /**
     * Handles {@code order.cancelled} events.
     * Idempotency check is FIRST (CLAUDE.md Rule #3). Audit row uses payload.userId() as recipient.
     */
    @Transactional
    public void handleOrderCancelled(UUID eventId, Envelope envelope, OrderCancelledPayload payload) {
        if (processedEventRepository.existsById(eventId)) {
            LOG.debug("notification.service: duplicate event {}, skipping", eventId);
            return;
        }
        String subject = NotificationTemplates.SUBJECT_ORDER_CANCELLED;
        String bodyTr  = NotificationTemplates.bodyOrderCancelled(payload);
        UUID correlationId = resolveCorrelationId(envelope, eventId);
        String payloadJson = renderPayloadJson(subject, bodyTr, envelope, payload);
        Notification n = new Notification(
            UUID.randomUUID(), payload.userId(), correlationId,
            CHANNEL, "order.cancelled", payloadJson, STATUS_SENT, Instant.now());
        notificationRepository.save(n);
        processedEventRepository.save(
            new ProcessedEvent(eventId, "OrderCancelledConsumer", envelope.eventType()));
        LOG.info("notification.sent recipient={} subject=\"{}\" correlationId={} eventType=order.cancelled channel={}",
            payload.userId(), subject, envelope.correlationId(), CHANNEL);
    }

    /**
     * Handles {@code payment.failed} events.
     *
     * <p>Note: payment-failed.schema.json has NO userId field. We store {@code payload.orderId()}
     * in the {@code user_id} column so smoke runbook (Step 8) and tests can locate the audit row
     * deterministically. The {@code user_id} column is NULLABLE per ARCHITECTURE.md §2.10 — but
     * populating it with the orderId is preferable for query continuity over leaving NULL.
     * Documented in Plan 07-04 PaymentFailedConsumerIdempotencyTest comment.
     */
    @Transactional
    public void handlePaymentFailed(UUID eventId, Envelope envelope, PaymentFailedPayload payload) {
        if (processedEventRepository.existsById(eventId)) {
            LOG.debug("notification.service: duplicate event {}, skipping", eventId);
            return;
        }
        String subject = NotificationTemplates.SUBJECT_PAYMENT_FAILED;
        String bodyTr  = NotificationTemplates.bodyPaymentFailed(payload);
        UUID correlationId = resolveCorrelationId(envelope, eventId);
        String payloadJson = renderPayloadJson(subject, bodyTr, envelope, payload);
        // Use orderId in user_id position — see Javadoc above for rationale
        Notification n = new Notification(
            UUID.randomUUID(), payload.orderId(), correlationId,
            CHANNEL, "payment.failed", payloadJson, STATUS_SENT, Instant.now());
        notificationRepository.save(n);
        processedEventRepository.save(
            new ProcessedEvent(eventId, "PaymentFailedConsumer", envelope.eventType()));
        LOG.info("notification.sent recipient={} subject=\"{}\" correlationId={} eventType=payment.failed channel={}",
            payload.orderId(), subject, envelope.correlationId(), CHANNEL);
    }

    /**
     * Handles {@code user.registered} events.
     * Idempotency check is FIRST (CLAUDE.md Rule #3). Audit row uses payload.userId() as recipient.
     */
    @Transactional
    public void handleUserRegistered(UUID eventId, Envelope envelope, UserRegisteredPayload payload) {
        if (processedEventRepository.existsById(eventId)) {
            LOG.debug("notification.service: duplicate event {}, skipping", eventId);
            return;
        }
        String subject = NotificationTemplates.SUBJECT_USER_REGISTERED;
        String bodyTr  = NotificationTemplates.bodyUserRegistered(payload);
        UUID correlationId = resolveCorrelationId(envelope, eventId);
        String payloadJson = renderPayloadJson(subject, bodyTr, envelope, payload);
        Notification n = new Notification(
            UUID.randomUUID(), payload.userId(), correlationId,
            CHANNEL, "user.registered", payloadJson, STATUS_SENT, Instant.now());
        notificationRepository.save(n);
        processedEventRepository.save(
            new ProcessedEvent(eventId, "UserRegisteredConsumer", envelope.eventType()));
        LOG.info("notification.sent recipient={} subject=\"{}\" correlationId={} eventType=user.registered channel={}",
            payload.userId(), subject, envelope.correlationId(), CHANNEL);
    }

    // ---- Private helpers ----

    /**
     * Resolves the correlation ID from the envelope, falling back to the event ID when
     * the envelope's correlationId is missing or invalid.
     *
     * <p>The {@code correlation_id} column is NOT NULL (ARCHITECTURE.md §2.10 + saga-contracts.md §6);
     * using eventId as fallback ensures the audit row can always be persisted while still
     * logging a WARN for operator visibility (NOTIF-02 contract).
     */
    private UUID resolveCorrelationId(Envelope envelope, UUID eventId) {
        UUID parsed = parseUuidOrNull(envelope.correlationId());
        if (parsed != null) return parsed;
        LOG.warn("notification.service: missing/invalid correlationId '{}' for event {} — using eventId fallback",
            envelope.correlationId(), eventId);
        return eventId;
    }

    private static UUID parseUuidOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Assembles a stable JSONB blob with keys: {@code subject}, {@code bodyTurkish},
     * {@code eventEnvelope}, {@code eventPayload}.
     *
     * <p>The blob is stored in {@code notifications.payload_json} for audit/forensics.
     * It is never read back into a typed POJO by application code.
     */
    private String renderPayloadJson(String subject, String bodyTurkish,
                                     Envelope envelope, Object eventPayload) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("subject", subject);
            node.put("bodyTurkish", bodyTurkish);
            node.set("eventEnvelope", objectMapper.valueToTree(envelope));
            node.set("eventPayload", objectMapper.valueToTree(eventPayload));
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to assemble payload_json for event " + envelope.eventId(), e);
        }
    }
}
