-- R__seed_agent_api_keys.sql
-- Phase 9 — repeatable seed for the demo agent bound to the admin user.
--
-- The plaintext API key is NEVER written here (CLAUDE.md Rule #5 / QUAL-09).
-- Instead, ${mcpAgentSeedHash} is filled at runtime by AgentSeedRunner —
-- if the property resolves to the empty string, this migration is a no-op
-- (the placeholder substitutes to '' and the WHERE clause skips the row).
--
-- The runtime seed flow:
--   1. AgentSeedRunner runs once at first boot (when agent_api_keys is empty).
--   2. It generates a 32-byte URL-safe random plaintext key.
--   3. It hashes the plaintext via SHA-256 base16 and stores the hash row directly
--      via AgentApiKeyRepository (NOT through this migration).
--   4. It logs the plaintext ONCE so the operator can paste it into .env.
--
-- This R__ migration exists to support the OPTIONAL pre-baked-hash path
-- (an operator may pre-set MCP_AGENT_SEED_HASH for reproducible demos);
-- if MCP_AGENT_SEED_HASH is not set, the runner takes over.
INSERT INTO agent_api_keys (api_key_hash, agent_label, user_id)
SELECT '${mcpAgentSeedHash}', 'demo-agent', u.id
FROM users u
WHERE u.email = '${adminSeedEmail}'
  AND length('${mcpAgentSeedHash}') = 64
ON CONFLICT (api_key_hash) DO NOTHING;
