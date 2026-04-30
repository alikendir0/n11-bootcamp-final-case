package com.n11.identity.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import com.n11.identity.user.User;
import com.n11.identity.user.UserRegistrationOutboxPublisher;
import com.n11.outbox.OutboxEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Real (DB-backed) implementation of UserRegistrationOutboxPublisher (D-12).
 * Inserts an outbox row in the SAME @Transactional boundary as the {@code users}
 * INSERT — caller (UserService.register) is the @Transactional boundary owner.
 *
 * <p>Bean name: {@code outboxBackedUserRegistrationOutboxPublisher} (lowerCamelCase
 * derivation of class name). The NoOp publisher's @ConditionalOnMissingBean
 * checks for this exact name and steps aside when this bean is present.
 *
 * <p>Envelope construction follows saga-contracts.md §1: eventId is fresh UUID,
 * correlationId equals eventId for a saga-initiating event (no causation).
 */
@Component
public class OutboxBackedUserRegistrationOutboxPublisher implements UserRegistrationOutboxPublisher {

    private static final String AGGREGATE     = "identity";
    private static final String EVENT_TYPE    = "user.registered";
    private static final int    EVENT_VERSION = 1;
    private static final String PRODUCER      = "identity-service";

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxBackedUserRegistrationOutboxPublisher(OutboxRepository outboxRepository,
                                                       ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishRegistered(User user) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        UserRegisteredPayload payload = new UserRegisteredPayload(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                occurredAt
        );

        // Envelope record: (eventId, eventType, eventVersion, occurredAt, correlationId, causationId, producer, payload)
        // occurredAt is Instant (not String) per Envelope.java record definition.
        // correlationId == eventId for saga-initiating events (no prior saga root).
        // causationId == null for first event in chain.
        Envelope envelope = new Envelope(
                eventId.toString(),
                EVENT_TYPE,
                EVENT_VERSION,
                occurredAt,               // Instant — matches Envelope record field type
                eventId.toString(),       // correlationId == eventId for saga-initiating events
                null,                     // causationId — null for first event in chain
                PRODUCER,
                objectMapper.valueToTree(payload)
        );

        String envelopeJson;
        try {
            envelopeJson = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize user.registered envelope", e);
        }

        OutboxEvent row = new OutboxEvent(
                eventId,
                AGGREGATE,
                EVENT_TYPE,
                envelopeJson,
                occurredAt
        );
        outboxRepository.save(row);
    }
}
