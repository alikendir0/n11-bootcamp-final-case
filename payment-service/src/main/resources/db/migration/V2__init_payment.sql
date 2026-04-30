-- V2__init_payment.sql
-- Phase 5 D-06 payment-service skeleton schema. NO Iyzico fields exposed yet (Phase 6 owns).

CREATE TABLE payments (
    id                UUID            PRIMARY KEY,
    order_id          UUID            NOT NULL,
    amount            NUMERIC(12, 2)  NOT NULL,
    currency          CHAR(3)         NOT NULL CHECK (currency = 'TRY'),
    status            VARCHAR(32)     NOT NULL CHECK (status IN ('PENDING','COMPLETED','FAILED')),
    iyzico_payment_id VARCHAR(255)    NULL,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_order_id ON payments (order_id);

CREATE TABLE outbox (
    id          UUID         PRIMARY KEY,
    aggregate   TEXT         NOT NULL,
    event_type  TEXT         NOT NULL,
    payload     JSONB        NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ  NULL
);
CREATE INDEX outbox_unsent_idx ON outbox (occurred_at) WHERE sent_at IS NULL;
