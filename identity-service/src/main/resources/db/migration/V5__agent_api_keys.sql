-- V5__agent_api_keys.sql
-- Phase 9 (D-05): DB-backed agent API keys. SHA-256 base16 of the plaintext
-- key is the PK. Plaintext is NEVER stored anywhere — only the hash, looked up
-- by exact equality. user_id binds the agent to a real user (D-06: agent's
-- actions are indistinguishable from that user's web actions in cart/order).
CREATE TABLE agent_api_keys (
    api_key_hash  CHAR(64)     PRIMARY KEY,                                   -- SHA-256 base16 lowercase
    agent_label   VARCHAR(100) NOT NULL,
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_used_at  TIMESTAMPTZ  NULL,
    revoked_at    TIMESTAMPTZ  NULL                                           -- NULL = active
);

CREATE INDEX agent_api_keys_user_idx ON agent_api_keys (user_id);
