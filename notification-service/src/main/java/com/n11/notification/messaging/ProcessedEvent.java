package com.n11.notification.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code processed_events} table (V1 migration) for notification-service.
 *
 * <p>Idempotency inbox: before processing any incoming event, each saga consumer
 * checks {@code processedEventRepository.existsById(eventId)}. If the row exists,
 * the event has already been processed and the consumer returns immediately.
 *
 * <p>Written in the SAME {@code @Transactional} method as the notifications audit row —
 * if any save throws, the entire transaction rolls back (CLAUDE.md Rule #3).
 *
 * <p>{@code @Entity(name = "NotificationProcessedEvent")} disambiguates from
 * {@code CartProcessedEvent} and {@code PaymentProcessedEvent} in infra-tests classpath
 * (Plan 05-04 STATE.md lesson — Pitfall #3).
 */
@Entity(name = "NotificationProcessedEvent")
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

    public UUID getEventId()        { return eventId; }
    public String getConsumer()     { return consumer; }
    public String getEventType()    { return eventType; }
    public Instant getProcessedAt() { return processedAt; }
}
