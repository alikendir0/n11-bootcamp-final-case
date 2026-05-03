package com.n11.search.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.n11.ai.port.EmbeddingProvider;
import com.n11.events.Envelope;
import com.n11.events.product.ProductDeletedPayload;
import com.n11.events.product.ProductPayload;
import com.n11.search.repository.ProductEmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductEventConsumerTest {

    private ProductEmbeddingRepository repository;
    private ProcessedEventRepository processedEventRepository;
    private EmbeddingProvider embeddingProvider;
    private ObjectMapper objectMapper;
    private ProductEventConsumer consumer;

    @BeforeEach
    void setUp() {
        repository = mock(ProductEmbeddingRepository.class);
        processedEventRepository = mock(ProcessedEventRepository.class);
        embeddingProvider = mock(EmbeddingProvider.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new ProductEventConsumer(repository, processedEventRepository, embeddingProvider, objectMapper);
    }

    @Test
    void consumes_product_created_and_stores_embedding() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        ProductPayload payload = new ProductPayload(productId, "SKU-1", "Telefon", "Akıllı Telefon", 10000.0, UUID.randomUUID());
        ObjectNode payloadNode = objectMapper.valueToTree(payload);

        Envelope envelope = new Envelope(
                eventId.toString(),
                "product.created",
                1,
                Instant.now(),
                eventId.toString(),
                null,
                "product-service",
                payloadNode
        );

        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(embeddingProvider.embed(anyString(), eq(768))).thenReturn(new float[]{0.1f, 0.2f});

        consumer.consume(objectMapper.writeValueAsString(envelope));

        verify(embeddingProvider).embed("Product: Telefon. Description: Akıllı Telefon", 768);
        verify(repository).save(argThat(entity -> entity.getProductId().equals(productId) && entity.getNameTr().equals("Telefon")));
        verify(processedEventRepository).save(argThat(event -> event.getEventId().equals(eventId)));
    }

    @Test
    void ignores_already_processed_event() throws Exception {
        UUID eventId = UUID.randomUUID();

        Envelope envelope = new Envelope(
                eventId.toString(),
                "product.created",
                1,
                Instant.now(),
                eventId.toString(),
                null,
                "product-service",
                objectMapper.createObjectNode()
        );

        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        consumer.consume(objectMapper.writeValueAsString(envelope));

        verifyNoInteractions(embeddingProvider);
        verifyNoInteractions(repository);
    }

    @Test
    void consumes_product_deleted_and_removes_embedding() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        ProductDeletedPayload payload = new ProductDeletedPayload(productId);
        ObjectNode payloadNode = objectMapper.valueToTree(payload);

        Envelope envelope = new Envelope(
                eventId.toString(),
                "product.deleted",
                1,
                Instant.now(),
                eventId.toString(),
                null,
                "product-service",
                payloadNode
        );

        when(processedEventRepository.existsById(eventId)).thenReturn(false);

        consumer.consume(objectMapper.writeValueAsString(envelope));

        verify(repository).deleteById(productId);
        verifyNoInteractions(embeddingProvider);
    }
}
