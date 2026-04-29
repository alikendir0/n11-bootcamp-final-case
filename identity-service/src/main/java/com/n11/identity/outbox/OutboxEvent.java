package com.n11.identity.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps to the {@code outbox} table (V4__init_outbox.sql per
 * saga-contracts.md §5.1). The {@code payload} column holds the FULL
 * saga envelope JSON (envelope wrapping the per-event payload).
 *
 * <p>{@code aggregate} is the aggregate root name (here, "identity") —
 * used as the routing-exchange root: aggregate + ".tx" = "identity.tx".
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
