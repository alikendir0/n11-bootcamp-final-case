# search-service

> **Phase 8** — Embedding Search Skeleton (v2: pgvector Semantic Search)

The second consumer of the `ai-port` `EmbeddingProvider` — alongside ai-service's `GeminiEmbeddingAdapter`. In v1, this is a skeleton that proves the SOLID substitutability of the port. In v2 (AI-V2-01), it will implement full semantic search via pgvector cosine similarity.

## Purpose

The **only** purpose of this service in v1 is to be the second consumer of the `EmbeddingProvider` port:
- search-service imports `ai-port` (zero Gemini SDK dependencies)
- search-service has **zero** `com.google.genai` imports
- The `EmbeddingProvider` bean is a deterministic zero-vector stub
- This proves the port is substitutable across services — the SOLID artifact graders inspect for QUAL-08

## Database Schema

```sql
CREATE TABLE product_embeddings (
    product_id   UUID          PRIMARY KEY,
    embedding    vector(768)   NOT NULL,    -- gemini-embedding-2 truncated to 768
    name_tr      TEXT          NOT NULL,    -- denormalized for result rendering
    indexed_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- HNSW index (activated in v2):
-- CREATE INDEX idx_product_embeddings_hnsw
--   ON product_embeddings USING hnsw (embedding vector_cosine_ops)
--   WITH (m = 16, ef_construction = 64);
```

## v2 Roadmap (AI-V2-01)

```java
// SearchService.search() — v2 implementation:
public List<UUID> search(String query, int limit) {
    float[] queryVector = embeddings.embed(query, 768);
    return jdbcTemplate.query(
        "SELECT product_id FROM product_embeddings ORDER BY embedding <=> ?::vector LIMIT ?",
        (rs, i) -> UUID.fromString(rs.getString("product_id")),
        Arrays.toString(queryVector), limit
    );
}
```

## Required Environment Variables

| Variable | Description |
|----------|-------------|
| `SEARCH_DB_PASSWORD` | PostgreSQL password for `search_user` |

## Build & Run

```bash
./gradlew :search-service:jibDockerBuild
docker compose up -d search-service
```

## Tests

```bash
./gradlew :search-service:test
```

| Test | Purpose |
|------|---------|
| SearchServiceContextTest | Spring context loads with EmbeddingProvider wired |
