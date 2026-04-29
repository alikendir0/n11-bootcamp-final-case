package com.n11.inventory.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps to the {@code outbox} table (V2__init_inventory.sql).
 * The {@code payload} column holds the FULL saga envelope JSON.
 *
 * <p>{@code aggregate} is the aggregate root name ("inventory") —
 * used as the routing-exchange root: aggregate + ".tx" = "inventory.tx".
 * OutboxPoller uses this formula to route to the correct exchange.
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
