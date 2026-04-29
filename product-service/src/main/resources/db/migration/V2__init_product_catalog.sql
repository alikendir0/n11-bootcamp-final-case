-- V2__init_product_catalog.sql
-- Categories + Products schema for the product-service. Runs inside `product` schema.
-- spring.flyway.default-schema=product + create-schemas=false (init.sh owns schema creation).
--
-- Locked decisions (per orchestrator + RESEARCH.md Open Questions):
--   * image_urls = TEXT[] (Hibernate 6.6 @JdbcTypeCode(SqlTypes.ARRAY) on String[])
--   * kdv_rate = NUMERIC(5,2) NOT NULL DEFAULT 20.00 (per-product VAT, default 20% standard rate, LOC-01)
--   * seller_name = TEXT NOT NULL DEFAULT 'n11 Pazaryeri' (single denormalized column; multi-vendor v2)
--   * pg_trgm GIN index on lower(name_tr) (PROD-04, Turkish-friendly ILIKE)

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE categories (
    id         UUID  PRIMARY KEY DEFAULT gen_random_uuid(),
    slug       TEXT  NOT NULL UNIQUE,
    name_tr    TEXT  NOT NULL,
    parent_id  UUID  REFERENCES categories(id),
    sort_order INT   NOT NULL DEFAULT 0
);

CREATE TABLE products (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    sku             TEXT          NOT NULL UNIQUE,
    name_tr         TEXT          NOT NULL,
    description_tr  TEXT,
    price_gross     NUMERIC(12,2) NOT NULL CHECK (price_gross >= 0),
    kdv_rate        NUMERIC(5,2)  NOT NULL DEFAULT 20.00 CHECK (kdv_rate >= 0 AND kdv_rate <= 100),
    category_id     UUID          REFERENCES categories(id),
    image_urls      TEXT[]        NOT NULL DEFAULT '{}',
    seller_name     TEXT          NOT NULL DEFAULT 'n11 Pazaryeri',
    slug            TEXT          NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- GIN trigram index on lower(name_tr) — uses pg_trgm gin_trgm_ops operator class.
-- This index is what makes `WHERE lower(name_tr) ILIKE '%foo%'` use an index scan
-- instead of a sequential scan (PROD-04 — the demo would be horrendously slow on
-- >=50 rows without it; index becomes essential at 10k+ rows).
CREATE INDEX idx_products_name_lower_trgm
    ON products USING GIN (lower(name_tr) gin_trgm_ops);

-- B-tree indexes for sort + filter:
CREATE INDEX idx_products_price       ON products (price_gross);
CREATE INDEX idx_products_created_at  ON products (created_at DESC);
CREATE INDEX idx_products_category_id ON products (category_id);
