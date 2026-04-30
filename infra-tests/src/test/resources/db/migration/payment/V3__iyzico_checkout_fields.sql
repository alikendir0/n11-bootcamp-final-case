-- V3__iyzico_checkout_fields.sql (infra-tests copy)
-- Mirrors payment-service/src/main/resources/db/migration/V3__iyzico_checkout_fields.sql
-- so the @SpringBootTest context loaded by Phase 6 E2E tests boots against the same
-- Iyzico-aware payment schema as production. Without this, JPA mapping
-- references (iyzico_token, payment_page_url, expires_at, etc.) fail at runtime.

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
