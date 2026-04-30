-- V1__init_processed_events.sql (infra-tests inventory copy for PaymentFailureCompensationE2ETest)
-- Mirrors inventory-service/src/main/resources/db/migration/V1__init_processed_events.sql
-- so the @SpringBootTest context booting InventoryCompensationTestConfig has the
-- idempotency inbox table available before the @RabbitListener fires.

CREATE TABLE processed_events (
    event_id      UUID         PRIMARY KEY,
    consumer      VARCHAR(64)  NOT NULL,
    event_type    VARCHAR(128) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_processed_events_consumer_processed_at
    ON processed_events (consumer, processed_at);
