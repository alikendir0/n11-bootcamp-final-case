# Phase 9: MCP Server - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in 09-CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-01
**Phase:** 09-mcp-server
**Areas discussed:** Transport posture & tunnel exposure; Auth bridge: API_KEY → JWT exchange; Tool registration with Spring AI; Pitfall #10 provenance posture

---

## Transport posture & tunnel exposure

### Q1: Packaging for stdio + HTTP

| Option | Description | Selected |
|--------|-------------|----------|
| One Boot app, both transports active | Same JAR/Jib image; `MCP_TRANSPORT` env switch flips stdio vs HTTP | ✓ |
| HTTP only in compose; stdio via separate launcher script | Compose runs HTTP only; `scripts/mcp-stdio.sh` launches JAR locally | |
| Split into two Spring profiles | `--spring.profiles.active=stdio` vs `=http`, mutually exclusive | |

**User's choice:** One Boot app, both transports active (Recommended).

### Q2: HTTP variant

| Option | Description | Selected |
|--------|-------------|----------|
| Streamable HTTP only | Current MCP standard (2025-06-18); SSE deprecated | ✓ |
| Both Streamable HTTP and deprecated SSE | Belt-and-braces for unknown-vintage grader client | |
| Whatever Spring AI starter exposes by default | Defer to researcher's read of starter defaults | |

**User's choice:** Streamable HTTP only.

### Q3: Tunnel exposure (PROJECT.md Open Question 2)

| Option | Description | Selected |
|--------|-------------|----------|
| Share the gateway tunnel under `/mcp/**` | Reuses Cloudflare Tunnel; one demo URL, one auth chain | ✓ |
| Dedicated tunnel for mcp-server | Direct tunnel to mcp-server port, separate auth | |
| HTTP transport local-only, stdio is the demo | Skip public exposure | |

**User's choice:** Share the gateway tunnel under `/mcp/**`. **Resolves PROJECT.md Open Question 2.**

### Q4: Claude Desktop demo path

| Option | Description | Selected |
|--------|-------------|----------|
| `docker run -i` against the published Jib image | Same artifact as compose; no separate JAR | ✓ |
| `java -jar` against a downloadable boot-jar | DEV-03 publishes JAR alongside the image | |
| Skip Claude Desktop; use `npx @modelcontextprotocol/inspector` | Simpler grader workflow, less wow | |

**User's choice:** `docker run -i` against the published Jib image.

---

## Auth bridge: API_KEY → JWT exchange

### Q1: Where do API keys live?

| Option | Description | Selected |
|--------|-------------|----------|
| DB table `agent_api_keys` in identity-service schema | Persisted hash, rotation/revocation/audit support | ✓ |
| Single key from env var, no DB row | Simplest, no rotation/audit | |
| Two keys (read-only, mutating), env-only | Demonstrates scoping without DB | |

**User's choice:** DB table `agent_api_keys` in identity-service schema.

### Q2: JWT identity carried by `/agents/exchange`

| Option | Description | Selected |
|--------|-------------|----------|
| Real `user_id` from `agent_api_keys.user_id` | JWT.sub = bound user; chat-cart-shared analog | ✓ |
| Synthetic agent identity (ROLE_AGENT only) | Cleaner audit, more plumbing | |
| Real `user_id` + ROLE_AGENT extra claim | Hybrid: filterable in logs | |

**User's choice:** Real `user_id` from `agent_api_keys.user_id`.

### Q3: When does mcp-server call `/agents/exchange`?

| Option | Description | Selected |
|--------|-------------|----------|
| Cache JWT in mcp-server, refresh ~5 min before exp | Single in-flight JWT, low overhead | ✓ |
| Per-tool-invocation exchange (no cache) | Stateless but 2x round trips | |
| Per-MCP-session exchange | Middle ground; more code | |

**User's choice:** Cache JWT in mcp-server, refresh ~5 min before exp.

### Q4: How is `MCP_API_KEY` delivered to mcp-server?

| Option | Description | Selected |
|--------|-------------|----------|
| Env var at process launch | `docker run -e MCP_API_KEY=...`; never on the wire | ✓ |
| MCP `initialize` `_meta` field | Per-session key delivery, leaves the wire | |
| HTTP request header `X-MCP-Api-Key` (HTTP only) | Per-request key, mixed posture for stdio | |

**User's choice:** Env var at process launch.

---

## Tool registration with Spring AI

### Q1: Tool wiring mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| Adapter at startup wraps each AgentTool as ToolCallback | Zero `@McpTool` annotations; one source of truth | ✓ |
| 10 thin `@McpTool` methods that delegate to ToolRegistry | Closer to STACK.md sample but duplicates names | |
| Generic `@McpTool` dispatcher class | One MCP tool that routes; breaks SC-1 | |

**User's choice:** Adapter at startup wraps each AgentTool as ToolCallback.

### Q2: ToolContext construction per MCP call

| Option | Description | Selected |
|--------|-------------|----------|
| `userId` from cached JWT.sub; `correlationId` per request; `seenIds` empty | Tools see real user, behave as authenticated | ✓ |
| `userId` from MCP `_meta.userId`; per-request override | Multi-user agents, more plumbing | |
| `userId` always null (treat as guest) | Defeats SC-3 mutating-tool path | |

**User's choice:** `userId` from cached JWT.sub; `correlationId` per request; `seenIds = Set.of()`.

### Q3: Tool dispatch routing — gateway vs Eureka

| Option | Description | Selected |
|--------|-------------|----------|
| Direct via Eureka (`lb://`) — same as ai-service | Reuses Phase 8 ToolHttpClients, full DRY | ✓ |
| Through the gateway with `Authorization: Bearer <JWT>` | Verbatim SC-3 wording; extra hop, divergent code | |

**User's choice:** Direct via Eureka — same as ai-service. (D-11 documents the SC-3 wording reconciliation.)

### Q4: DRY proof for SC-1

| Option | Description | Selected |
|--------|-------------|----------|
| infra-tests integration test asserts equality | ToolRegistry beans vs mcp-server's ToolCallback list | ✓ |
| ArchUnit gate: zero AgentTool impls in mcp-server | Static check for SC-4 | |
| Both — ArchUnit gate + integration test | Belt-and-braces | |

**User's choice:** infra-tests integration test asserts equality. (D-12; ArchUnit deferred — planner may add as low-cost belt-and-braces.)

---

## Pitfall #10 provenance posture

### Q1: seenIds posture for MCP

| Option | Description | Selected |
|--------|-------------|----------|
| Live-lookup only (no seenIds in mcp-server) | Backing services are the authoritative ID gate | ✓ |
| Per-MCP-session seenIds | Self-defeating: same LLM populates and queries the Set | |
| Per-API-key seenIds with TTL | Privacy/cross-session leak concerns | |

**User's choice:** Live-lookup only.

### Q2: Do current AgentTool implementations require non-empty `seenIds`?

| Option | Description | Selected |
|--------|-------------|----------|
| No — informational only inside the tool | seenIds check lives in ai-service ToolDispatcher | |
| Yes — some tools call `ctx.seenIds()` | Tools would need provenance check moved to wrapper | |
| Unsure — leave as research item for the planner | Defer verification; CONTEXT.md notes deliverable | ✓ |

**User's choice:** Unsure — leave as research item for the planner. (Captured as D-15.)

### Q3: How are backing-service errors surfaced to the calling agent?

| Option | Description | Selected |
|--------|-------------|----------|
| Map to `ToolResult.Err` with semantic code | LLM sees clean error, self-corrects | ✓ |
| Throw exception; let Spring AI starter convert | Less explicit; framework behavior unverified | |
| Defer to research | Researcher confirms Spring AI 1.1.5 ToolResult error semantics | |

**User's choice:** Map to `ToolResult.Err` with semantic code.

---

## Claude's Discretion

The user explicitly deferred these to the researcher / planner:
- Spring AI `ToolCallback` vs `ToolDefinition` API surface (verify against current Spring AI 1.1.5 docs).
- Whether `/mcp/**` route on the gateway needs `metadata.response-timeout: -1`.
- Hash algorithm specifics for `agent_api_keys.api_key_hash` (SHA-256 default; HMAC + pepper allowed if planner prefers).
- MCP capability negotiation flags advertised by mcp-server.
- `MCP_EAGER_EXCHANGE` switch (lazy default).
- Springdoc surface for mcp-server (skip likely).
- Bean disambiguation in `infra-tests` (Plan 05-04 lessons).
- `/agents/exchange` request body shape (JSON body vs `X-MCP-Api-Key` header).
- D-15 verification result.

## Deferred Ideas

- Per-agent role scoping (`ROLE_AGENT_READONLY`).
- Multi-key per mcp-server instance.
- MCP `resources` and `prompts` capabilities.
- HMAC + server-pepper key hashing.
- API key rotation endpoint (`POST /agents/{id}/rotate`).
- `ROLE_AGENT` audit claim.
- `MCP_EAGER_EXCHANGE` mode.
- Streamable HTTP keep-alive tuning.
