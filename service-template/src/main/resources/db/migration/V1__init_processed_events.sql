-- V1__init_processed_events.sql
-- Saga consumer idempotency inbox per ARCHITECTURE.md §3.5.
--
-- Every saga consumer (Phase 5+) writes to this table the FIRST time it sees an event ID:
--   INSERT INTO processed_events (event_id, consumer, event_type) VALUES (?, ?, ?) ON CONFLICT DO NOTHING;
-- and skips processing if the row already exists. RabbitMQ delivers at-least-once → duplicates WILL arrive.
--
-- This migration runs against the SERVICE'S OWN schema (not public) — guaranteed by Flyway's
-- spring.flyway.default-schema = ${flyway.schema} config in service-template.yml.
-- Each business service overrides ${flyway.schema} in its <svc>-service.yml.
--
-- NO `CREATE SCHEMA` — schemas are owned by 01-03's init.sh. Flyway's create-schemas:false ensures
-- Flyway never tries to create one (would fail with "permission denied" since per-service users
-- lack CREATE on public).

CREATE TABLE processed_events (
    event_id      UUID         PRIMARY KEY,
    consumer      VARCHAR(64)  NOT NULL,
    event_type    VARCHAR(128) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_processed_events_consumer_processed_at
    ON processed_events (consumer, processed_at);
