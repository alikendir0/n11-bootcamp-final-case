-- V3__iyzico_checkout_fields.sql
-- Phase 6 Iyzico Checkout Form durable state and lifecycle statuses.

ALTER TABLE payments
    ADD COLUMN iyzico_token VARCHAR(255) UNIQUE,
    ADD COLUMN payment_page_url TEXT,
    ADD COLUMN failure_reason VARCHAR(255),
    ADD COLUMN failure_code VARCHAR(128),
    ADD COLUMN expires_at TIMESTAMPTZ,
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN failed_at TIMESTAMPTZ;

ALTER TABLE payments
    DROP CONSTRAINT IF EXISTS payments_status_check;

ALTER TABLE payments
    ADD CONSTRAINT payments_status_check
    CHECK (status IN ('PENDING_INITIALIZATION','PENDING','COMPLETED','FAILED','TIMED_OUT'));

CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payments (order_id);
CREATE INDEX IF NOT EXISTS idx_payments_iyzico_token ON payments (iyzico_token);
CREATE INDEX IF NOT EXISTS idx_payments_status_expires_at ON payments (status, expires_at);
