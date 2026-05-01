-- V1__init_ai.sql
-- Schema 'ai' is created by infra/postgres/init.sh (Plan 01-03); Flyway create-schemas: false.
-- Search path is set to 'ai, public' via the ai_user role (init.sh line 115).

CREATE TABLE ai_conversations (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID,                                            -- NULL for guest sessions (D-02 / D-03)
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_activity_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    seen_ids_json      TEXT,                                            -- D-08 Pitfall #10 seenIds (JSON array)
    metadata_json      TEXT
);

CREATE INDEX idx_ai_conversations_user_id ON ai_conversations (user_id);

CREATE TABLE messages (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id     UUID         NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
    role                VARCHAR(20)  NOT NULL,                           -- 'USER' | 'ASSISTANT' | 'TOOL'
    content             TEXT,                                            -- text body (null for pure tool messages)
    tool_call_json      TEXT,                                            -- JSON array of ToolCallRequest (ASSISTANT)
    tool_result_json    TEXT,                                            -- JSON array of ToolCallResult (TOOL)
    sequence_no         INT          NOT NULL,                           -- ordering within conversation
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT messages_role_check CHECK (role IN ('USER', 'ASSISTANT', 'TOOL'))
);

CREATE INDEX idx_messages_conversation_id ON messages (conversation_id);
CREATE INDEX idx_messages_conversation_sequence ON messages (conversation_id, sequence_no);
