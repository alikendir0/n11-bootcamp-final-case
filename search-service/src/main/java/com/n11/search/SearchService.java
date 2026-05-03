package com.n11.search;

import com.n11.ai.port.EmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * AI-V2-01 semantic search implementation.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final EmbeddingProvider embeddings;
    private final JdbcTemplate jdbcTemplate;

    public SearchService(EmbeddingProvider embeddings, JdbcTemplate jdbcTemplate) {
        this.embeddings = embeddings;
        this.jdbcTemplate = jdbcTemplate;
        log.info("search-service: SearchService wired with EmbeddingProvider impl = {}",
                 embeddings.getClass().getName());
    }

    /**
     * Semantic search via pgvector cosine similarity (<=>).
     * @param q query text
     * @param limit max results
     * @return List of product IDs sorted by relevance
     */
    public List<UUID> search(String q, int limit) {
        log.info("search-service: performing semantic search for: '{}' (limit: {})", q, limit);
        
        float[] queryVector = embeddings.embed(q, 768);
        
        // Use pgvector <=> operator (cosine distance)
        String sql = "SELECT product_id FROM product_embeddings ORDER BY embedding <=> ?::vector LIMIT ?";
        
        return jdbcTemplate.query(
                sql,
                (rs, i) -> UUID.fromString(rs.getString("product_id")),
                Arrays.toString(queryVector),
                limit
        );
    }
}
