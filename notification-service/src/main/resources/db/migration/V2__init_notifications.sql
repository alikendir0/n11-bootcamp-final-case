-- V2__init_notifications.sql
-- Audit log for mock email notifications (NOTIF-02), per ARCHITECTURE.md §2.10.
-- Pure append-only; no FK to any other service schema (schema boundary enforcement).
-- The Turkish subject + body live INSIDE payload_json (JSONB) under keys "subject" and "bodyTurkish";
-- structured INFO log line (NOTIF-02) is a separate concern and is NOT a DB read.

CREATE TABLE notifications (
    id              UUID          PRIMARY KEY,
    user_id         UUID,                                        -- nullable: future user.registered emission may not yet exist (identity-service may not wire outbox); keep nullable for non-user events
    correlation_id  UUID          NOT NULL,                      -- saga trace (saga-contracts.md §6)
    channel         TEXT          NOT NULL,                      -- 'EMAIL' for v1 (logging-only stub — actual SMTP wiring deferred)
    type            TEXT          NOT NULL,                      -- event_type: 'order.confirmed' | 'order.cancelled' | 'payment.failed' | 'user.registered'
    payload_json    JSONB         NOT NULL,                      -- structured envelope: {subject, bodyTurkish, eventEnvelope, ...}
    status          TEXT          NOT NULL DEFAULT 'SENT',       -- 'SENT' | 'FAILED' (logged-only stub: always SENT for v1)
    sent_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_correlation_id ON notifications (correlation_id);
CREATE INDEX idx_notifications_user_id        ON notifications (user_id);
