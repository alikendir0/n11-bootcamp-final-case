package com.n11.ai.infrastructure.llm;

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
 * EmbeddingProvider implementation — calls gemini-embedding-2 via google-genai 1.52.0.
 *
 * <p>Uses the task prefix pattern (replaces deprecated taskType enum per RESEARCH
 * State of the Art): {@code "task: retrieval document | content: ..."}.
 *
 * <p>All com.google.genai imports are sealed inside infrastructure/llm/ (Pitfall #7).
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
        if (apiKey == null || apiKey.isBlank()) {
            this.client = Client.builder().apiKey("missing").build();
        } else {
            this.client = Client.builder().apiKey(apiKey).build();
        }
    }

    @Override
    public float[] embed(String text, int outputDims) {
        // gemini-embedding-2 task prefix (replaces deprecated taskType enum)
        String prefixed = "task: retrieval document | content: " + (text == null ? "" : text);
        EmbedContentConfig cfg = EmbedContentConfig.builder()
            .outputDimensionality(outputDims)
            .build();
        try {
            EmbedContentResponse response = client.models.embedContent(embeddingModel, prefixed, cfg);
            List<ContentEmbedding> embeddings = response.embeddings().orElse(List.of());
            if (embeddings.isEmpty()) {
                throw new GeminiUpstreamException("Gemini returned empty embeddings list", null);
            }
            List<Float> values = embeddings.get(0).values().orElse(List.of());
            float[] out = new float[values.size()];
            for (int i = 0; i < values.size(); i++) out[i] = values.get(i);
            return out;
        } catch (GeminiUpstreamException e) {
            throw e;
        } catch (Exception e) {
            log.error("ai-service: Gemini embed call failed: {}", e.getMessage());
            throw new GeminiUpstreamException("Gemini embedding upstream error", e);
        }
    }
}
