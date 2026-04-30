package com.n11.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code notifications} audit table (V2 migration).
 *
 * <p>Extends ARCHITECTURE.md §2.10 (7-column base shape) with {@code correlation_id}
 * per saga-contracts.md §6 — 8 columns total:
 * <ol>
 *   <li>{@code id} — PK UUID</li>
 *   <li>{@code user_id} — NULLABLE (payment.failed has no userId in its schema; populated with orderId for query continuity)</li>
 *   <li>{@code correlation_id} — NOT NULL (saga trace; falls back to eventId when missing from envelope)</li>
 *   <li>{@code channel} — 'EMAIL' for v1 (logging-only stub)</li>
 *   <li>{@code type} — event_type string (Java field: {@code eventType} mapped via {@code @Column(name="type")})</li>
 *   <li>{@code payload_json} — JSONB blob: {subject, bodyTurkish, eventEnvelope, eventPayload}</li>
 *   <li>{@code status} — 'SENT' for v1</li>
 *   <li>{@code sent_at} — timestamp of audit row creation</li>
 * </ol>
 *
 * <p>{@code @Entity(name = "NotificationAudit")} disambiguates from any other
 * {@code Notification} entity on the infra-tests classpath (Plan 05-04 STATE.md lesson — Pitfall #3).
 *
 * <p>Pure value object after construction — no setters; protected no-arg ctor for JPA.
 */
@Entity(name = "NotificationAudit")
@Table(name = "notifications")
public class Notification {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;                                // NULLABLE per ARCHITECTURE.md §2.10

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "channel", nullable = false, length = 64)
    private String channel;

    @Column(name = "type", nullable = false, length = 128)
    private String eventType;                           // Java name 'eventType' → column 'type'

    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payloadJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status;                              // 'SENT' for v1

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    protected Notification() { /* JPA */ }

    public Notification(UUID id, UUID userId, UUID correlationId, String channel,
                        String eventType, String payloadJson, String status, Instant sentAt) {
        this.id = id;
        this.userId = userId;
        this.correlationId = correlationId;
        this.channel = channel;
        this.eventType = eventType;
        this.payloadJson = payloadJson;
        this.status = status;
        this.sentAt = sentAt;
    }

    public UUID getId()              { return id; }
    public UUID getUserId()          { return userId; }
    public UUID getCorrelationId()   { return correlationId; }
    public String getChannel()       { return channel; }
    public String getEventType()     { return eventType; }
    public String getPayloadJson()   { return payloadJson; }
    public String getStatus()        { return status; }
    public Instant getSentAt()       { return sentAt; }
}
