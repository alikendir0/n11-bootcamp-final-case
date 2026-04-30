-- V2__init_orders.sql
-- Phase 5 D-04 + D-05 + D-08 + CD-01 + CD-02 schema for order-service.
-- 5 tables: orders, order_items, order_shipping_addresses, order_idempotency_keys, outbox.

CREATE TABLE orders (
    id              UUID         PRIMARY KEY,
    user_id         UUID         NOT NULL,
    status          VARCHAR(32)  NOT NULL CHECK (status IN ('PENDING','STOCK_RESERVED','PAID','CONFIRMED','STOCK_FAILED','PAYMENT_FAILED','CANCELLED')),
    total_amount    NUMERIC(12, 2) NOT NULL,
    currency        CHAR(3)      NOT NULL CHECK (currency = 'TRY'),
    correlation_id  UUID         NOT NULL,
    idempotency_key UUID         NOT NULL,
    cancel_reason   VARCHAR(64)  NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);

CREATE TABLE order_items (
    order_id        UUID            NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      UUID            NOT NULL,
    name_snapshot   TEXT            NOT NULL,
    qty             INTEGER         NOT NULL CHECK (qty > 0),
    unit_price      NUMERIC(10, 2)  NOT NULL CHECK (unit_price >= 0),
    PRIMARY KEY (order_id, product_id)
);

CREATE TABLE order_shipping_addresses (
    order_id        UUID            PRIMARY KEY REFERENCES orders(id) ON DELETE CASCADE,
    recipient_name  TEXT            NOT NULL,
    phone           TEXT            NOT NULL,
    il              TEXT            NOT NULL,
    ilce            TEXT            NOT NULL,
    mahalle         TEXT            NOT NULL,
    street_line     TEXT            NOT NULL,
    postal_code     TEXT            NULL,
    title           TEXT            NULL
);

CREATE TABLE order_idempotency_keys (
    idempotency_key UUID         NOT NULL,
    user_id         UUID         NOT NULL,
    order_id        UUID         NOT NULL REFERENCES orders(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (idempotency_key, user_id)
);

CREATE TABLE outbox (
    id          UUID         PRIMARY KEY,
    aggregate   TEXT         NOT NULL,
    event_type  TEXT         NOT NULL,
    payload     JSONB        NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ  NULL
);
CREATE INDEX outbox_unsent_idx ON outbox (occurred_at) WHERE sent_at IS NULL;
