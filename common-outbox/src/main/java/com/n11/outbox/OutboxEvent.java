package com.n11.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared outbox event entity for all services using the transactional outbox pattern.
 *
 * <p>Maps to the {@code outbox} table (created per-service by each service's Flyway migration).
 * The {@code payload} column holds the FULL saga envelope JSON (saga-contracts.md §5.1).
 *
 * <p>{@code aggregate} is the aggregate root name — used as the routing-exchange root:
 * {@code aggregate + ".tx"} = the correct exchange (e.g. "inventory.tx", "orders.tx").
 *
 * <p>Field set is intentionally identical to the per-service entities in identity-service
 * and inventory-service so migration is a pure rename (CD-05). Per-service Flyway migrations
 * stay unchanged — common-outbox ships Java-only artifacts, no Flyway scripts.
 */
@Entity
@Table(name = "outbox")
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate", nullable = false)
    private String aggregate;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected OutboxEvent() { /* JPA */ }

    public OutboxEvent(UUID id, String aggregate, String eventType, String payload, Instant occurredAt) {
        this.id = id;
        this.aggregate = aggregate;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }

    public UUID getId()            { return id; }
    public String getAggregate()   { return aggregate; }
    public String getEventType()   { return eventType; }
    public String getPayload()     { return payload; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getSentAt()     { return sentAt; }

    public void markSent(Instant when) { this.sentAt = when; }
}
