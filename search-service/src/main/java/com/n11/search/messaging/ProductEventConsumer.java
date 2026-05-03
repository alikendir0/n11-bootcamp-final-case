package com.n11.search.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.ai.port.EmbeddingProvider;
import com.n11.events.Envelope;
import com.n11.events.product.ProductDeletedPayload;
import com.n11.events.product.ProductPayload;
import com.n11.search.repository.ProductEmbedding;
import com.n11.search.repository.ProductEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class ProductEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductEventConsumer.class);

    private final ProductEmbeddingRepository repository;
    private final ProcessedEventRepository processedEventRepository;
    private final EmbeddingProvider embeddingProvider;
    private final ObjectMapper objectMapper;

    public ProductEventConsumer(ProductEmbeddingRepository repository,
                                ProcessedEventRepository processedEventRepository,
                                EmbeddingProvider embeddingProvider,
                                ObjectMapper objectMapper) {
        this.repository = repository;
        this.processedEventRepository = processedEventRepository;
        this.embeddingProvider = embeddingProvider;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = SearchRabbitConfig.QUEUE_PRODUCT_EVENTS)
    @Transactional
    public void consume(String envelopeJson) {
        try {
            Envelope envelope = objectMapper.readValue(envelopeJson, Envelope.class);
            UUID eventId = UUID.fromString(envelope.eventId());
            
            if (processedEventRepository.existsById(eventId)) {
                log.info("search-service: event {} already processed, skipping", eventId);
                return;
            }

            String eventType = envelope.eventType();
            log.info("search-service: consuming event {} (type: {})", eventId, eventType);

            if ("product.created".equals(eventType) || "product.updated".equals(eventType)) {
                ProductPayload payload = objectMapper.treeToValue(envelope.payload(), ProductPayload.class);
                handleUpsert(payload);
            } else if ("product.deleted".equals(eventType)) {
                ProductDeletedPayload payload = objectMapper.treeToValue(envelope.payload(), ProductDeletedPayload.class);
                handleDelete(payload);
            } else {
                log.warn("search-service: ignored unknown event type {}", eventType);
            }
            
            processedEventRepository.save(new ProcessedEvent(eventId, "search-service", eventType));
        } catch (Exception e) {
            log.error("search-service: failed to process product event: {}", e.getMessage(), e);
            throw new RuntimeException("Re-throwing to trigger AMQP retry", e);
        }
    }

    private void handleUpsert(ProductPayload payload) {
        String textToEmbed = String.format("Product: %s. Description: %s", 
                payload.nameTr(), 
                payload.descriptionTr() != null ? payload.descriptionTr() : "");
        
        log.debug("search-service: generating embedding for product {}", payload.productId());
        float[] embedding = embeddingProvider.embed(textToEmbed, 768);
        
        ProductEmbedding entity = new ProductEmbedding(
                payload.productId(),
                embedding,
                payload.nameTr()
        );
        repository.save(entity);
        log.info("search-service: indexed product {}", payload.productId());
    }

    private void handleDelete(ProductDeletedPayload payload) {
        repository.deleteById(payload.productId());
        log.info("search-service: removed product {}", payload.productId());
    }
}
