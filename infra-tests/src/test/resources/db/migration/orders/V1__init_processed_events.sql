-- V1__init_processed_events.sql (infra-tests orders copy for PaymentFailureCompensationE2ETest)
-- Mirrors order-service/src/main/resources/db/migration/V1__init_processed_events.sql.

CREATE TABLE processed_events (
    event_id      UUID         PRIMARY KEY,
    consumer      VARCHAR(64)  NOT NULL,
    event_type    VARCHAR(128) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_processed_events_consumer_processed_at
    ON processed_events (consumer, processed_at);
