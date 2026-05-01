package com.n11.search;

import com.n11.ai.port.EmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * D-09 v1 skeleton. The EmbeddingProvider injection point exists; v2 (AI-V2-01)
 * will fill in the body with: embed query text -> pgvector cosine search ->
 * return product IDs.
 *
 * The ONLY purpose of this class in v1 is to be the second consumer of the
 * ai-port EmbeddingProvider — alongside ai-service's GeminiEmbeddingAdapter.
 * Two consumers + zero google-genai imports in search-service is the SOLID
 * demonstration QUAL-08 codifies.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final EmbeddingProvider embeddings;

    public SearchService(EmbeddingProvider embeddings) {
        this.embeddings = embeddings;
        log.info("search-service: SearchService wired with EmbeddingProvider impl = {}",
                 embeddings.getClass().getName());
    }

    // v2: public List<UUID> search(String q) { float[] qv = embeddings.embed(q, 768); ... }
}
