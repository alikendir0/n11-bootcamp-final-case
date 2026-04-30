package com.n11.ai.port;

/**
 * Provider-agnostic embedding port (D-01).
 *
 * Implementations (Phase 8): GeminiEmbeddingAdapter (production).
 * Consumed by ai-service (chat) and search-service (D-09 skeleton).
 */
public interface EmbeddingProvider {

    /**
     * @param text       text to embed
     * @param outputDims target dimensions (e.g., 768 for pgvector index)
     * @return float[] of length outputDims
     */
    float[] embed(String text, int outputDims);
}
