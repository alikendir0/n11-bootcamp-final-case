-- V2__init_inventory.sql -- inventory schema

CREATE TABLE stock (
    product_id          UUID        PRIMARY KEY,
    available_qty       INT         NOT NULL DEFAULT 0 CHECK (available_qty >= 0),
    reserved_qty        INT         NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
    low_stock_threshold INT         NOT NULL DEFAULT 5,
    version             BIGINT      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stock_reservations (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID        NOT NULL,
    product_id   UUID        NOT NULL REFERENCES stock(product_id),
    reserved_qty INT         NOT NULL CHECK (reserved_qty > 0),
    status       VARCHAR(20) NOT NULL DEFAULT 'RESERVED'
                             CHECK (status IN ('RESERVED', 'RELEASED', 'COMMITTED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (order_id, product_id)
);

CREATE TABLE outbox (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate   VARCHAR(64)  NOT NULL,
    event_type  VARCHAR(128) NOT NULL,
    payload     JSONB        NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ
);

CREATE INDEX outbox_unsent_idx ON outbox (occurred_at) WHERE sent_at IS NULL;
