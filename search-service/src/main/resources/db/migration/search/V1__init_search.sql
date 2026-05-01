-- V1__init_search.sql
-- Schema 'search' is created by infra/postgres/init.sh; Flyway create-schemas: false.
-- pgvector 'vector' type available because init.sh runs CREATE EXTENSION IF NOT
-- EXISTS vector at superuser scope before service users connect.

CREATE TABLE product_embeddings (
    product_id   UUID          PRIMARY KEY,                              -- logical FK to product-service (no cross-schema FK)
    embedding    vector(768)   NOT NULL,                                 -- gemini-embedding-2 truncated to 768 (STACK.md)
    name_tr      TEXT          NOT NULL,                                 -- denormalized for v2 result rendering
    indexed_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- HNSW index activated in v2 when product embeddings are populated:
-- CREATE INDEX idx_product_embeddings_hnsw
--   ON product_embeddings USING hnsw (embedding vector_cosine_ops)
--   WITH (m = 16, ef_construction = 64);
