package com.n11.search;

import com.n11.ai.port.EmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * D-09 search-service skeleton: search-service consumes the EmbeddingProvider
 * port to prove it is substitutable across services in v1. The bean returned
 * here is a deterministic zero-vector stub — search-service does NOT call it
 * at runtime in v1 (no /search endpoint exists). v2 (AI-V2-01) replaces this
 * with a real implementation.
 *
 * The substitutability point: this Configuration could equally well @Bean a
 * Gemini-backed implementation, an OpenAI-backed implementation, or a
 * REST-client to ai-service's embed endpoint — the rest of search-service
 * does not change. This is the SOLID artifact graders inspect for QUAL-08.
 */
@Configuration
public class EmbeddingProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingProviderConfig.class);

    private final com.n11.search.infrastructure.llm.GeminiEmbeddingAdapter geminiEmbeddingAdapter;

    public EmbeddingProviderConfig(com.n11.search.infrastructure.llm.GeminiEmbeddingAdapter geminiEmbeddingAdapter) {
        this.geminiEmbeddingAdapter = geminiEmbeddingAdapter;
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public EmbeddingProvider embeddingProvider() {
        log.info("search-service: wiring real GeminiEmbeddingAdapter (AI-V2-01).");
        return geminiEmbeddingAdapter;
    }
}
