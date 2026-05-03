-- V2__add_hnsw_index.sql
-- Activates the HNSW index for product embeddings (AI-V2-01).
-- This speeds up semantic search at the cost of some memory and index build time.

CREATE INDEX idx_product_embeddings_hnsw
  ON product_embeddings USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 64);
