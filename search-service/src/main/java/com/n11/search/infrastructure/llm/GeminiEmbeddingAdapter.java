package com.n11.search.infrastructure.llm;

import com.google.genai.Client;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.n11.ai.port.EmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * EmbeddingProvider implementation for search-service — calls gemini-embedding-2 via google-genai.
 * Direct integration as requested by user.
 */
@Component("geminiEmbeddingAdapter")
public class GeminiEmbeddingAdapter implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingAdapter.class);

    private final Client client;
    private final String embeddingModel;

    public GeminiEmbeddingAdapter(
            @Value("${ai.gemini.model.embedding:gemini-embedding-2}") String embeddingModel,
            @Value("${ai.gemini.api-key:}") String apiKey) {
        this.embeddingModel = embeddingModel;
        log.info("search-service: initializing Gemini client with model {} and apiKey length {}", embeddingModel, apiKey != null ? apiKey.length() : 0);
        if (apiKey == null || apiKey.isBlank()) {
            this.client = Client.builder().build();
        } else {
            this.client = Client.builder().apiKey(apiKey).build();
        }
    }

    @Override
    public float[] embed(String text, int outputDims) {
        String prefixed = "task: retrieval document | content: " + (text == null ? "" : text);
        EmbedContentConfig cfg = EmbedContentConfig.builder()
            .outputDimensionality(outputDims)
            .build();
        try {
            EmbedContentResponse response = client.models.embedContent(embeddingModel, prefixed, cfg);
            List<ContentEmbedding> embeddings = response.embeddings().orElse(List.of());
            if (embeddings.isEmpty()) {
                throw new RuntimeException("Gemini returned empty embeddings list");
            }
            List<Float> values = embeddings.get(0).values().orElse(List.of());
            float[] out = new float[values.size()];
            for (int i = 0; i < values.size(); i++) out[i] = values.get(i);
            return out;
        } catch (Exception e) {
            log.error("search-service: Gemini embed call failed: {}", e.getMessage());
            throw new RuntimeException("Gemini embedding upstream error", e);
        }
    }
}
