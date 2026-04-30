# Phase 8: AI Port + Adapter + Agent Toolset - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in `08-CONTEXT.md` — this log preserves the alternatives considered.

**Date:** 2026-05-01
**Phase:** 08-ai-port-adapter-agent-toolset
**Areas discussed:** Conversation state store, Guest vs authed chat, Gemini function-calling mode, search-service skeleton scope

---

## Conversation State Store

### Q1: Where does the conversation state live?

| Option | Description | Selected |
|--------|-------------|----------|
| Postgres ai_conversations + messages | Per-service schema convention; trivial CRUD via JPA; zero new infra; replay is a SQL query. Aligns with PROJECT.md "single Postgres host". | ✓ |
| Redis (chat-idiomatic) | Idiomatic for chat sessions; sub-ms reads. Cost: new container, new client (Lettuce), new failure mode (volatile by default — needs RDB/AOF for AI-09 persistence). Over-engineering for one demo user. | |
| Hybrid — Postgres durable + Redis hot cache | Premature optimization for 6-day demo with single concurrent user. | |

**User's choice:** Postgres ai_conversations + messages
**Notes:** Resolves CLAUDE.md / PROJECT.md open question "Conversation state store (Redis vs Postgres)".

### Q2: How long do conversations stick around?

| Option | Description | Selected |
|--------|-------------|----------|
| Forever for authed users; ephemeral for guests | Authed: row keyed by user_id, no TTL — surfaces in future history view (mirrors Siparişlerim). Guests: in-memory only, no DB row. Cleanly separates concerns. | ✓ |
| TTL 30 days for everyone | Adds scheduled cleanup job. Demo never hits it; pure ops scaffolding. | |
| Forever for both | Persist guest sessions too via cookie session ID. Adds guest session management complexity for marginal demo benefit. | |

**User's choice:** Forever for authed users; ephemeral for guests
**Notes:** Sets up D-03 guest session model in the next area.

---

## Guest vs Authed Chat

### Q1: Guest session model when chat is opened anonymously?

| Option | Description | Selected |
|--------|-------------|----------|
| In-memory map keyed by client conversationId | Frontend generates UUID on chat-open, persists in localStorage, sends with every request. ai-service keeps Map<conversationId, Conversation> with 1h idle TTL. Survives page refresh, dies on service restart. | ✓ |
| Anonymous JWT minted by gateway | Gateway issues short-lived "guest:<uuid>" JWT. Pro: unified auth. Con: identity-service grows guest-issuance path; AUTH-* never asked for it. | |
| Browser fingerprint cookie | HttpOnly cookie on first request. Cross-origin cookie behavior is fragile across SPA + gateway origin. | |

**User's choice:** In-memory map keyed by client conversationId
**Notes:** Simplest path; no anonymous JWT minting; aligns with D-02 (ephemeral for guests).

### Q2: What happens when a guest invokes a mutating tool?

| Option | Description | Selected |
|--------|-------------|----------|
| Tool returns 401-flavored error; model surfaces "Lütfen giriş yapın" | ToolDispatcher checks tool.requiresAuth(); guests get ToolResult.Err("AUTH_REQUIRED"). Gemini surfaces in Turkish. Read tools (search/get/list_categories) work. Pitfall #10 still runs. | ✓ |
| Hide mutating tools from guest's tool list | Trim tool list passed to Gemini based on auth state. Pro: model can't propose what it can't do. Con: model also can't say "log in to add to cart" because it has no concept those tools exist. | |
| Force login at chat-open | Bubble in Phase 11 prompts login. Read-only chat for guests gone. Allowed by spec but limits demo. | |

**User's choice:** Tool returns 401-flavored error; model surfaces "Lütfen giriş yapın"
**Notes:** Lets the assistant suggest login as a follow-up action — better UX than hiding tools.

### Q3: Internal hops auth posture (ai-service → cart-service / order-service / etc.)?

| Option | Description | Selected |
|--------|-------------|----------|
| Internal direct REST trusting gateway-pattern X-User-Id headers | WebClient/RestClient + Eureka discovery + forwarded X-User-Id/X-User-Roles headers ai-service receives from gateway. Same pattern Phase 6 payment-service used to call order-service /internal/orders. | ✓ |
| Round-trip back through the gateway | Tools call /api/v1/cart/items via gateway, requiring ai-service to keep raw Authorization. Violates strip-at-edge contract. | |
| ai-service mints a fresh internal JWT per request | New cryptographic surface; new key to manage; not how any other service in this codebase calls anything. | |

**User's choice:** Internal direct REST trusting gateway-pattern X-User-Id headers
**Notes:** Honors CLAUDE.md "JWT validated only at the gateway".

---

## Gemini Function-Calling Mode

### Q1: Manual loop vs Automatic Function Calling (AFC)?

| Option | Description | Selected |
|--------|-------------|----------|
| Manual loop | ai-service drives: messages → Gemini → text-or-tool-call → ToolDispatcher validates + calls backing service → tool-result message → loop until text. Full control over SSE interleaving + Pitfall #10 + correlation IDs + EchoChatProvider port simplicity. | ✓ |
| Automatic Function Calling (AFC) | Pass tool callables to SDK; SDK invokes them and loops internally. Less code. SDK calls tools synchronously — no SSE indicator possible during tool work (AI-08 violation). EchoChatProvider port has to mirror AFC semantics or diverge — leaky. | |

**User's choice:** Manual loop
**Notes:** AI-08 streaming requirement and EchoChatProvider port simplicity drive the decision.

### Q2: SSE wire format?

| Option | Description | Selected |
|--------|-------------|----------|
| Typed SSE events: delta / tool_call / tool_result / done / error | event: + JSON data: lines. Frontend wires single EventSource, switches on event type. Idiomatic SSE; matches OpenAI/Anthropic streaming shapes. | ✓ |
| Unified data-only stream with type discriminator | All events are data: {"type":...,"payload":...}. Slightly less idiomatic but matches Vercel AI SDK shape. | |

**User's choice:** Typed SSE events
**Notes:** Idiomatic, easy to extend, clean Phase 11 frontend integration.

### Q3: Pitfall #10 ID validation depth?

| Option | Description | Selected |
|--------|-------------|----------|
| Conversation-scoped provenance + repo lookup | Provenance: any productId/orderId arg must have appeared in prior tool result of THIS conversation; rejects "prod-12345" hallucinations. Repo lookup: validates IDs against backing service to catch deleted-but-seen-earlier IDs. Belt-and-braces. | ✓ |
| Repo lookup only | Skip provenance; rely on 404 → "not found" translation. Burns one tool round-trip per hallucination. | |
| Conversation provenance only | Track seen IDs but skip live lookup. Stale IDs slip through. | |

**User's choice:** Conversation-scoped provenance + repo lookup
**Notes:** Pitfall #10 is HIGH severity — both checks are cheap.

---

## search-service Skeleton Scope

### Q1: How thin is the v1 skeleton?

| Option | Description | Selected |
|--------|-------------|----------|
| True skeleton: module + Flyway pgvector + EmbeddingProvider injected, no /search endpoint | Just enough to prove EmbeddingProvider port works on a second consumer (alongside ai-service) — the SOLID demonstration. v1 search continues to come from product-service ILIKE. v2 wires the query path. | ✓ |
| Thin proxy: GET /search?q= forwards to product-service ILIKE | Real /api/v1/search/** route + gateway entry. No 404 on /api/v1/search; api-contracts §1 "v1: ILIKE on product-service" reads literally. Throwaway code v2 will rip out. | |
| Full v1 semantic search | Embed all seed products at startup; pgvector cosine search. AI-V2-01/02 explicitly defer to v2. | |

**User's choice:** True skeleton
**Notes:** SOLID demonstration is the priority; v1 search via product-service stays.

### Q2: Where do EmbeddingProvider calls happen for search_products tool in v1?

| Option | Description | Selected |
|--------|-------------|----------|
| search_products tool calls product-service ILIKE only | v1 backing is product-service GET /products?q=. EmbeddingProvider wired but unused at runtime. AI-V2-02 activates in v2 by swapping the tool's backing service. Tool interface stays stable. | ✓ |
| search_products embeds query, calls search-service pgvector | Defies AI-V2-01/02 v2 deferral. | |

**User's choice:** search_products tool calls product-service ILIKE only
**Notes:** v1→v2 transition is purely additive — agent toolset interface stays stable.

---

## Claude's Discretion

The user explicitly deferred these to planner/researcher discretion (recorded in CONTEXT.md `<decisions>` § Claude's Discretion):

- Gemini model identifier verification at impl time (Pitfall #1).
- Embedding dimension choice (768 vs 3072 — STACK.md recommends 768).
- HikariCP pool sizing for ai-service (~3) and search-service (~2) with Pitfall #8 budget math.
- Turkish system prompt template wording.
- Conversation idle TTL exact value (D-03 says 1 hour as starting point).
- Optional `/chat/conversations/{id}/claim` guest→authed migration endpoint.
- ToolRegistry / Spring config concrete shape inside agent-toolset module.
- ai-service Testcontainers test posture (EchoChatProvider for tests; one optional smoke test gated behind GEMINI_API_KEY).

## Deferred Ideas

(Carried forward in CONTEXT.md `<deferred>` for downstream agents.)

- `/chat/conversations/{id}/claim` guest→authed migration endpoint (skip unless trivially cheap).
- Gemini API quota / paid tier (Pitfall #9 — runbook documentation only).
- Spring AI 1.2 swap if released during build week (one-line port-preserving switch).
- Real semantic search (AI-V2-01 / AI-V2-02 — v2).
- Conversational PDP summaries (AI-V2-03), compare-products (AI-V2-04), cart-aware suggestions (AI-V2-05) — all v2.
- Streaming chat UX with mid-stream cancel (FE-V2-01) — Phase 11 + v2.
- mcp-server `MCP_API_KEY` auth bridge — Phase 9.
- `/api/v1/search/**` live endpoint — v2 (AI-V2-01).
