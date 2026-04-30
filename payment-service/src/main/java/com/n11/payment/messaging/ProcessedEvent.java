package com.n11.payment.messaging;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code payment.processed_events} table (V1 migration).
 *
 * <p>Idempotency inbox: before processing any incoming event, StockReservedConsumer
 * checks {@code processedEventsRepository.existsById(eventId)}. If the row exists,
 * the event has already been processed and the consumer skips.
 */
@Entity(name = "PaymentProcessedEvent")
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "consumer", nullable = false)
    private String consumer;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() { /* JPA */ }

    public ProcessedEvent(UUID eventId, String consumer, String eventType) {
        this.eventId = eventId;
        this.consumer = consumer;
        this.eventType = eventType;
        this.processedAt = Instant.now();
    }

    public UUID getEventId()         { return eventId; }
    public String getConsumer()      { return consumer; }
    public String getEventType()     { return eventType; }
    public Instant getProcessedAt()  { return processedAt; }
}
