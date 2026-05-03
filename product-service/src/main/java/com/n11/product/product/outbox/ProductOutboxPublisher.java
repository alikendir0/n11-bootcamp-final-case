package com.n11.product.product.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.events.Envelope;
import com.n11.events.product.ProductDeletedPayload;
import com.n11.events.product.ProductPayload;
import com.n11.outbox.OutboxEvent;
import com.n11.product.product.Product;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class ProductOutboxPublisher {

    private static final String AGGREGATE = "products";
    private static final int EVENT_VERSION = 1;
    private static final String PRODUCER = "product-service";

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ProductOutboxPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publishCreated(Product product) {
        publish(product, "product.created");
    }

    public void publishUpdated(Product product) {
        publish(product, "product.updated");
    }

    public void publishDeleted(UUID productId) {
        publishDeletedEvent(productId, "product.deleted");
    }

    private void publish(Product product, String eventType) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        ProductPayload payload = new ProductPayload(
                product.getId(),
                product.getSku(),
                product.getNameTr(),
                product.getDescriptionTr(),
                product.getPriceGross().doubleValue(),
                product.getCategory() != null ? product.getCategory().getId() : null
        );

        saveToOutbox(eventId, eventType, occurredAt, payload);
    }

    private void publishDeletedEvent(UUID productId, String eventType) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        ProductDeletedPayload payload = new ProductDeletedPayload(productId);

        saveToOutbox(eventId, eventType, occurredAt, payload);
    }

    private void saveToOutbox(UUID eventId, String eventType, Instant occurredAt, Object payload) {
        Envelope envelope = new Envelope(
                eventId.toString(),
                eventType,
                EVENT_VERSION,
                occurredAt,
                eventId.toString(), // correlationId == eventId for saga roots
                null,
                PRODUCER,
                objectMapper.valueToTree(payload)
        );

        String envelopeJson;
        try {
            envelopeJson = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize " + eventType + " envelope", e);
        }

        OutboxEvent row = new OutboxEvent(
                eventId,
                AGGREGATE,
                eventType,
                envelopeJson,
                occurredAt
        );
        outboxRepository.save(row);
    }
}
