# Phase 8: AI Port + Adapter + Agent Toolset - Context

**Gathered:** 2026-05-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Build the SOLID grading wedge of the project. Phase 8 owns:

1. **`ai-port` Gradle module** — zero-dependency, neutral DTOs only (`ChatMessage`, `ToolCall`, `ToolResult`). No Gemini SDK types leak. CI guard: `gradle :ai-port:dependencies` shows zero `com.google.genai` artifacts.
2. **`GeminiChatAdapter` + `GeminiEmbeddingAdapter`** — implement the ports via `google-genai 1.51.0`. Sole importers of `com.google.genai.*` in the codebase.
3. **`EchoChatProvider`** — trivial second `ChatProvider` adapter. Setting `ai.provider=echo` substitutes it; chat assistant still answers (echoing the prompt). This is the SOLID artifact graders inspect.
4. **`agent-toolset` shared Gradle module** — single source of truth for the 10 canonical tools (`search_products`, `get_product`, `list_categories`, `add_to_cart`, `view_cart`, `update_cart_item`, `remove_from_cart`, `create_order`, `get_payment_link`, `get_order_status`). Imported by ai-service in this phase; by mcp-server in Phase 9.
5. **`ai-service`** — REST + SSE chat endpoint, Turkish system prompt, manual function-calling loop, Pitfall #10 ID validation, conversation persistence, gateway-routed under `/api/v1/chat/**` (with explicit SSE route at `/api/v1/chat/stream/**` honoring the `response-timeout: -1` caveat from api-contracts §6).
6. **`search-service` skeleton** — Gradle module + Spring Boot app + Flyway pgvector schema + `EmbeddingProvider` injected (unused at runtime). The second consumer of the AI port that proves substitutability across services. v2 wires the query path.

Phase 8 does **not** own: the floating chat bubble UI (Phase 11), the MCP server (Phase 9), real semantic search (deferred to v2 per AI-V2-01/02), Cloudflare Tunnel hardening (Phase 11), Gemini API quota provisioning beyond a working sandbox key.

</domain>

<decisions>
## Implementation Decisions

### Conversation State Store

- **D-01:** Conversation state lives in **Postgres** (`ai_conversations` + `messages` tables) inside ai-service's per-service schema. JPA + Flyway, same convention as every other service. No new infra (no Redis container). Resolves PROJECT.md / CLAUDE.md open question "Conversation state store (Redis vs Postgres)".
- **D-02:** Retention = **forever for authed users** (rows keyed by `user_id`, no TTL — surfaced in a future "Yapay Zeka Asistanı" history view, mirroring the "Siparişlerim" model). **Ephemeral for guests** (in-memory only; no DB row). No scheduled cleanup job in v1.

### Guest vs Authed Chat

- **D-03:** Guest sessions live in an **in-memory `Map<conversationId, Conversation>` with a 1-hour idle TTL** inside ai-service. Frontend generates a UUID `conversationId` on first chat-open, persists it in `localStorage`, and sends it with every request. Survives page refresh (frontend remembers the ID), is wiped on ai-service restart — acceptable for a 6-day demo. No anonymous JWT minted. No HttpOnly cookie. The DB tables (D-01) are written only when the request carries an authed `X-User-Id`. Optional `/chat/conversations/{id}/claim` endpoint for guest→authed migration on login is **planner discretion** (skip unless cheap to add).
- **D-04:** Mutating tools enforce auth via `tool.requiresAuth()`. ToolDispatcher checks the conversation's auth state before invoking; for guests, mutating tools (`add_to_cart`, `update_cart_item`, `remove_from_cart`, `create_order`, `get_payment_link`, `get_order_status`) return `ToolResult.Err("AUTH_REQUIRED", "...")`. Gemini sees this in its tool-result message and replies in Turkish — e.g., "Sepete eklemek için giriş yapman gerekiyor." Read tools (`search_products`, `get_product`, `list_categories`, `view_cart` for empty/guest, ...) work for everyone. **Tools are NOT trimmed from the schema list** — Gemini sees all 10 tools and learns from `AUTH_REQUIRED` errors that some require login; this is what lets the assistant suggest "log in first" instead of pretending those tools don't exist.
- **D-05:** Internal hops from ai-service to cart-service / order-service / product-service / payment-service use **direct REST + Eureka discovery + forwarded `X-User-Id`/`X-User-Roles` headers** that ai-service receives from the gateway. No JWT minting, no extra gateway round-trip, no double-validation. Same pattern Phase 6 payment-service used to call order-service `/internal/orders` (CONTEXT 06 §code_context). Honors CLAUDE.md "JWT validated only at the gateway" + the strip-at-edge contract.

### Gemini Function-Calling Mode

- **D-06:** **Manual function-calling loop.** ai-service drives every step: send messages → Gemini returns text-or-tool-call → if tool-call, ToolDispatcher applies D-08 validation + invokes backing service via D-05 path → append `tool` role message with the result → send the updated history back → loop until Gemini returns text. Reasons:
  - Full control over the SSE stream's interleaving of `delta` (Gemini text tokens) and `tool_call`/`tool_result` events (D-07 + AI-08 requirement).
  - Pitfall #10 validation lives in ToolDispatcher centrally, not duplicated inside each tool's callable.
  - Correlation-ID propagation via MDC stays explicit (ARCH-08).
  - The `EchoChatProvider` port contract stays simple: it just echoes the latest user message back; it doesn't have to mock SDK-internal AFC machinery (which would leak through the port and defeat Pitfall #7).
- **D-07:** SSE wire format is **typed events**:
  - `event: delta` `data: {"text": "...", "conversationId": "..."}` — Gemini token chunk.
  - `event: tool_call` `data: {"name": "search_products", "argsJson": "...", "callId": "..."}` — fired before tool execution; Phase 11 frontend renders "araç çalıştırılıyor: ürün aranıyor...".
  - `event: tool_result` `data: {"callId": "...", "ok": true|false, "summary": "..."}` — fired after tool returns.
  - `event: done` `data: {"conversationId": "...", "messageId": "...", "finalText": "..."}` — terminal event with the assembled assistant message.
  - `event: error` `data: {"code": "...", "messageTr": "..."}` — terminal failure.
  Frontend wires a single `EventSource`, switches on `event.type`. Final assistant text is also persisted (per D-01) before the `done` event fires.
- **D-08:** Pitfall #10 mitigation depth is **belt-and-braces — conversation-scoped ID provenance + repo lookup**:
  - **Provenance check:** ToolDispatcher tracks `seenIds` in conversation state. Any `productId` / `orderId` / `cartItemId` argument must have appeared in a prior tool result of this conversation. Hallucinated IDs return `ToolResult.Err("UNKNOWN_ID", "productId 'prod-12345' was not in any prior tool result")` — Gemini self-corrects.
  - **Live lookup:** Even validated IDs are checked against the actual repo via the backing service before mutating. Catches IDs that existed earlier in the conversation but were since deleted (rare in demo but cheap to enforce).
  - The system prompt includes the Pitfall #10 rule explicitly: "Asla ürün ID'sini uydurma. Önce `search_products` ile ara, oradaki ID'yi kullan."

### search-service Skeleton

- **D-09:** **True skeleton** — Phase 8 ships search-service as a Gradle module + Spring Boot app + Flyway V1 (`product_embeddings(product_id PK, embedding vector(768), name_tr, indexed_at)` — pgvector schema, distinct DB user with role-deny per ARCH-09) + Eureka client + `EmbeddingProvider` `@Autowired` into a `SearchService` bean with a TODO body. **No `/search` REST endpoint, no gateway route, no Springdoc page in v1.** Phase 8's reason for shipping search-service at all is to be the **second consumer of the AI port**, alongside ai-service — proving the port's substitutability across two services in v1 (the SOLID demonstration). v2 (AI-V2-01 / AI-V2-02) wires the query path, embeds seed products, exposes `/search?q=`. api-contracts §1 row for `/api/v1/search/**` is annotated "v2 — see AI-V2-01" rather than going live in v1.
- **D-10:** The `search_products` agent tool in v1 backs onto **product-service `GET /products?q=` (ILIKE+GIN from Phase 4 / PROD-04)**, not search-service, not Gemini embedding. `EmbeddingProvider` is wired into search-service but never invoked at runtime in v1. AI-V2-02 (semantic search as the tool's primary mode) activates in v2 by changing the tool's backing service from product-service to search-service — the agent toolset interface stays stable across the v1→v2 transition.

### Claude's Discretion

The following are deferred to the planner / researcher and do not require user input:

- **Gemini model identifier verification** — researcher fetches `https://ai.google.dev/gemini-api/docs/models` at impl time; pins `ai.gemini.model.chat=gemini-3-flash-preview` (or whatever is current) with `gemini-2.5-flash` as documented fallback. ai-service logs the resolved model on startup (Pitfall #1 mitigation).
- **Embedding dimension** — `gemini-embedding-2` default is 3072; STACK.md recommends 768 for pgvector index size. Planner picks; the chosen value lives in one config property and one Flyway migration column.
- **HikariCP pool sizing for ai-service / search-service** — apply Pitfall #8 budget math (services × pool ≤ pg.max_connections × 0.8). ai-service ~3, search-service ~2 are reasonable starting points.
- **System prompt template wording** — researcher drafts the Turkish system prompt enforcing `dil: tr-TR`, the Pitfall #10 ID rule, and the tool-use posture. Tool **names** in English (Gemini handles fine); tool **descriptions** in Turkish per Pitfall #20.
- **Conversation idle TTL exact value** — D-03 says "1 hour" as a starting point; planner can adjust 30–120 min based on Gemini cost / demo behavior.
- **Optional `/chat/conversations/{id}/claim`** — guest→authed migration endpoint (D-03). Skip unless trivially cheap.
- **Where `ToolRegistry` lives** — agent-toolset module exposes `AgentTool` interface + 10 implementations as Spring `@Component`s. ai-service auto-discovers them; mcp-server (Phase 9) re-uses the same beans via `@Tool` annotation. Concrete Spring config and module boundaries are planner shape.
- **Testcontainers test posture for ai-service** — google-genai calls go through the port, so ai-service's idempotency/integration tests use `EchoChatProvider` (not real Gemini). One smoke test against real Gemini gated behind a `GEMINI_API_KEY` env var, skip-if-absent — same pattern as the deferred payment-service test in `.planning/phases/06-payment-iyzico/deferred-items.md` D-06-01.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Scope and Requirements

- `.planning/ROADMAP.md` — Phase 8 entry: goal, dependencies, success criteria (5/5), risks, research need.
- `.planning/REQUIREMENTS.md` — AI-01 through AI-15 (in this phase: AI-01..10, AI-14, AI-15) and QUAL-08; AI-11..13 are Phase 9 (mcp-server) but referenced for hand-off shape.
- `.planning/PROJECT.md` — "Provider-agnostic LLM abstraction" Key Decision; "Chat assistant + MCP server share one toolset" Key Decision; Out-of-Scope row "Multiple LLM providers in v1"; Open Question "Conversation state store" (resolved in this CONTEXT D-01).
- `.planning/STATE.md` — handoff from Phase 7; Blockers/Concerns row "Gemini 3 Flash model identifier".
- `CLAUDE.md` — Non-negotiable Rules #1 (zero-dep ai-port), #2 (one agent toolset two consumers), #4 (verify SDK docs); AI library split (google-genai vs Spring AI MCP); Gemini model fallback chain.

### Architecture and Contracts

- `.planning/research/ARCHITECTURE.md` §2.11 (search-service contract), §2.12 (ai-service contract), §6.1 (ai-service hexagonal layout — domain/application/infrastructure/interfaces), §6.2 (Shared Agent Toolset — `AgentTool` interface, `ToolContext`, `ToolResult`, the 10 tools, dispatch flow). Especially the JSON shape of `AgentTool` and the dispatch loop.
- `.planning/api-contracts.md` §1 (ai-service + search-service REST surface; "v2: pgvector" annotation), §2 (gateway routing for `/api/v1/chat/**` and `/api/v1/search/**`), §3 (public allowlist — `POST /api/v1/chat/**` is public), §4 (Authorization-strip + X-User-Id/X-User-Roles injection), §5 (correlation ID propagation), **§6 (SSE caveat — `metadata.response-timeout: -1`, no body filters; commented-out anchor in `config-server/src/main/resources/config/api-gateway.yml` to be activated this phase)**, §7 (RFC-7807 problem+json shape).
- `.planning/saga-contracts.md` — ai-service has no AMQP consumer in v1 (no events relevant to chat); included for reference if planner needs the event envelope shape for any out-of-scope notification idea.

### Research and Pitfalls

- `.planning/research/STACK.md` — "AI Stack" section: `google-genai 1.51.0` confirmed; Spring AI MCP starter `1.1.5` Phase 9 only; Gemini 3 Flash Preview model ID and fallback ladder; `gemini-embedding-2` model ID + 768-dim recommendation for pgvector. **"What NOT to Use" section** — Spring AI's `VertexAiGeminiChatModel` is explicitly skipped because 1.1.5 doesn't list `gemini-3-flash-preview`.
- `.planning/research/PITFALLS.md` — **Pitfall 1** (Gemini model identifier verification gate), **Pitfall 7** (leaky `ChatProvider` abstraction — the entire grading thesis; D-06 manual loop + EchoChatProvider together prevent), **Pitfall 8** (Postgres pool budget — adding 2 services means revisiting pool sizes), **Pitfall 9** (Gemini free-tier 10 RPM rate limit during demo), **Pitfall 10** (hallucinated tool args — D-08 mitigation), **Pitfall 19** (chat streaming UI freeze — D-07 typed SSE events keep TTFT low), **Pitfall 20** (Turkish prompt drift — system prompt + tool descriptions in TR), **Pitfall 22** (Testcontainers budget — EchoChatProvider for ai-service tests).
- `.planning/research/SUMMARY.md` — top pitfalls and stack synthesis if planner needs a quick recap.
- `.planning/research/FEATURES.md` — feature-level decomposition.

### Prior-Phase Context

- `.planning/phases/01-foundations-day-1-contracts/01-CONTEXT.md` — service-template scaffolding, common-error / common-logging / common-events / common-outbox modules, `spring.config.import` posture, Eureka client retry config in service-template/application.yml, Jib pre-build pattern (`./gradlew :<svc>:jibDockerBuild` before `docker compose up -d`).
- `.planning/phases/05-cart-order-skeleton/05-CONTEXT.md` — common-outbox shared module pattern (relevant only as a template if ai-service ever needs to publish events; not in v1 scope), `@RabbitListener` + `@Transactional` split (only relevant if ai-service ever consumes events), Idempotency-Key dedup posture (relevant for `create_order` tool replay safety).
- `.planning/phases/06-payment-iyzico/06-CONTEXT.md` — D-05 (internal REST hop pattern, X-User-Id forwarding) is the analog this phase reuses for ai-service tool dispatch.
- `.planning/intel/n11-recon.md` — captured Turkish phrase table (644 phrases) used for grounded vocabulary in the system prompt; phrase #644 "Yapay Zeka Alışveriş Asistanı" is the Phase 11 chat label; recon flagged "no in-storefront chat panel observed" so Phase 11 invents the bubble UX (this phase delivers the backend that bubble talks to).

### External SDK Documentation (verify-before-implement, CLAUDE.md Rule #4)

- `https://ai.google.dev/gemini-api/docs/models` — verify `gemini-3-flash-preview` is current; pin or fall back.
- `https://ai.google.dev/gemini-api/docs/text-generation` — code samples for chat + streaming + function-calling.
- `https://ai.google.dev/gemini-api/docs/function-calling` — function-calling response shape, manual loop pattern, automatic function calling option (we choose manual per D-06).
- `https://ai.google.dev/gemini-api/docs/embeddings` — `gemini-embedding-2` API surface, output-dimension config.
- `https://github.com/googleapis/java-genai` — google-genai 1.51.0 README, generateContent / generateContentStream signatures, function-calling Java samples.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`service-template/skeleton/`** — Gradle archetype used by every Boot service in this repo (cart-service, order-service, payment-service, notification-service all clone it). Use the same pattern for ai-service and search-service: `plugins { id("org.springframework.boot"); id("com.google.cloud.tools.jib") }`, `:common-error` + `:common-logging` + `:common-events` deps, Springdoc 2.8.17, Flyway 12.5.0, `application.yml` with `spring.config.import=optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}`. ai-service does NOT need `:common-outbox` (no AMQP publishing). search-service does NOT need it either.
- **`api-gateway` SSE anchor** — `config-server/src/main/resources/config/api-gateway.yml` has the commented-out `ai-chat-stream` route block from Phase 1 (api-contracts §6). This phase activates it: uncomment + set `metadata.response-timeout: -1`, `connect-timeout: 5000`, `PreserveHostHeader=true`. Verify a 5-minute idle SSE stream is not closed by the gateway.
- **`payment-service` internal-REST client pattern** — Phase 6 wired `OrderInternalClient` to call order-service's `/internal/orders/{id}/payment-context` via Eureka discovery + WebClient + X-User-Id forwarding. ai-service's tool dispatch reuses this pattern: one client per backing service (`ProductClient`, `CartClient`, `OrderClient`, `PaymentClient`), each with the same constructor + header-forwarding helper. Use Spring's `RestClient` (synchronous) — ai-service is itself reactive only at the SSE boundary; the manual loop (D-06) is easier to reason about with blocking I/O for tool calls.
- **`identity-service` `/.well-known/jwks.json` + `JwtTimestampValidator(30s)`** — already in place from Phase 3. ai-service does not need to validate JWTs itself (gateway does that); ai-service trusts X-User-Id headers per D-05. Mentioned only because Phase 9 mcp-server **will** need its own auth bridge — out of scope this phase.
- **`common-events` JSON schemas** — not used by Phase 8 (no events published or consumed). Listed for completeness.
- **`common-error` `ProblemDetailControllerAdvice` + `ApiErrorCode`** — ai-service's REST + SSE error responses use this. New error codes for AI-specific failures (`UPSTREAM_LLM_ERROR`, `TOOL_VALIDATION_FAILED`, `RATE_LIMITED`) are added to `ApiErrorCode` enum.
- **`common-logging` MDC propagation + `correlationId` MDC key** — ai-service correlationId is generated at the SSE-stream boundary (or copied from `X-Correlation-Id` if the gateway already injected it per api-contracts §5) and threaded through every tool dispatch. Tool clients copy it into outbound `X-Correlation-Id`.
- **`product-service` `GET /products?q=` (ILIKE+GIN)** — Phase 4 PROD-04 deliverable, used as the v1 backing for the `search_products` tool per D-10.
- **`cart-service` REST surface** — Phase 5 endpoints `/cart`, `POST /cart/items`, `PUT /cart/items/{productId}`, `DELETE /cart/items/{productId}` are exactly the contracts the four cart tools (`view_cart`, `add_to_cart`, `update_cart_item`, `remove_from_cart`) wrap.
- **`order-service` `POST /orders` + Idempotency-Key dedup** — Phase 5 (CONTEXT 05). The `create_order` tool MUST send a UUID `Idempotency-Key` header on every call to make the tool's replay safe (Gemini retry, transient failure during the manual loop).
- **`payment-service` payment-link endpoint** — Phase 6 D-06 (clients fetch the persisted `paymentPageUrl`). The `get_payment_link` tool calls this exact endpoint; Phase 6 already designed it as the agent's source of truth.

### Established Patterns

- **`@Entity(name=...)` + `@RestController(beanName)` + `@Component(beanName)` disambiguation** required for shared classnames once ai-service / search-service join the infra-tests classpath. Plan 05-04 lesson — apply pre-emptively.
- **Flyway subdirectory `classpath:db/migration/<schema>/`** for any service whose migrations are loaded by `infra-tests`. ai-service and search-service follow the cart-service / payment-service convention.
- **`@RabbitListener` is irrelevant this phase** (no AMQP consumers in ai-service or search-service v1). The Plan 04-02 `@Transactional` split lesson applies if a future phase wires events.
- **`AcknowledgeMode.AUTO` + Message-only listener parameter** — also irrelevant this phase (D-10 ArchUnit gate from Plan 05-01 still passes because no listeners are added).
- **Springdoc per-service** — ai-service exposes `/swagger-ui.html` per QUAL-01. search-service does NOT in v1 (D-09: no REST endpoints).
- **Bean disambiguation for multi-service test classpath** — when ai-service joins infra-tests, planner must apply Plan 05-04 lesson (`PaymentServiceTestConfig` excludeFilters analog → `AiServiceTestConfig`).
- **Distinct DB users + role-deny matrix** — ai-service uses a new schema `ai_db` and DB user `ai_user`; search-service uses `search_db` and `search_user`. Both added to `infra/postgres/init.sh` (Plan 01-03 pattern).

### Integration Points

- **Gateway route activation** — `config-server/src/main/resources/config/api-gateway.yml` gets the live `ai-chat-stream` route + a non-streaming `ai-chat` route + a `search` route stub (commented-out, activated in v2). Springdoc aggregator gets ai-service entry; search-service entry stays commented.
- **`docker-compose.yml`** — ai-service + search-service entries added. `depends_on: postgres, rabbitmq, eureka-server, config-server, identity-service` (so X-User-Id mesh is in place when ai-service starts). Stale-Jib-image hazard from Plan 01-06 applies: editing `config-server/.../application.yml` for new services requires `:config-server:jibDockerBuild` before `docker compose up -d`.
- **`PUBLIC_BASE_URL`** — already set for payment-service callbacks. ai-service does NOT need it in v1 (no public webhooks).
- **`infra/postgres/init.sh`** — extended with two new schemas (`ai_db`, `search_db`) + two new DB users + role-deny matrix updates (10×9 → 12×11 grid).
- **`infra-tests`** — extended with ai-service + search-service `@SpringBootApplication` classpath entries. Bean disambiguation applied per Plan 05-04 pattern. ai-service tests use `EchoChatProvider`; search-service tests are minimal (skeleton).
- **Phase 9 hand-off** — `agent-toolset` module structure must be designed so mcp-server can import it directly and re-expose the same `AgentTool` beans through Spring AI MCP starter's `@Tool`-annotated methods. This means tool implementations cannot depend on ai-service-specific beans (e.g., conversation state); ToolContext (`userId`, `correlationId`, `authToken-or-null`, `seenIds`) is the only context the tools see. Pitfall #10 provenance check (D-08) lives in ai-service's ToolDispatcher, not in the tool implementations themselves — keeps the tools reusable in mcp-server (which has its own ToolDispatcher with the same provenance discipline).

</code_context>

<specifics>
## Specific Ideas

- **The SOLID demonstration moment graders look for:** flip `ai.provider=echo` in `config-server/src/main/resources/config/ai-service.yml`, restart ai-service, hit the chat — assistant echoes back the user prompt. Recommend documenting this swap in the README's demo-script section so graders find it without the candidate having to point it out (Phase 11 deliverable, but Phase 8 leaves the runbook stub).
- **Pitfall #1 model probe** — ai-service `ApplicationReadyEvent` listener runs a single `models.get("gemini-3-flash-preview")` call on startup. If 404, log a clear warning and fall back to `gemini-2.5-flash` automatically. First log line on boot includes the resolved model identifier and provider class.
- **Pitfall #9 demo budget** — free-tier Gemini Flash is 10 RPM. Document a "demo budget" line in the runbook: avoid running embedding pre-warm before the chat demo, watch for `429 RESOURCE_EXHAUSTED`, fallback model `gemini-2.5-flash` shares the same quota bucket. Real mitigation is paid tier (out of scope for the bootcamp), but the runbook line shapes demo behavior.
- **Tool dispatch correlation IDs** — every tool call's outbound HTTP request must carry the conversation's `correlationId` in `X-Correlation-Id`. `grep -r "correlationId=<uuid>"` across all 13 service log files traces a chat-driven order from chat → cart → order → inventory → payment → notification. Worth a 30-second demo moment.

</specifics>

<deferred>
## Deferred Ideas

- **`/chat/conversations/{id}/claim` guest→authed migration endpoint** — D-03 says optional. Skip unless trivially cheap (≤10 LOC). If skipped, frontend just generates a fresh `conversationId` after login and the guest history is discarded.
- **Gemini API quota / paid tier** — Pitfall #9; out of scope for v1.
- **Spring AI swap** — if Spring AI 1.2 ships during build week with `gemini-3-flash-preview` listed, swap GeminiChatAdapter to use Spring AI's `VertexAiGeminiChatModel` (one-line dependency switch, port stays the same). LOW likelihood per STACK.md; tracked as an audit-trail note only.
- **Real semantic search (AI-V2-01 / AI-V2-02)** — search-service v2 work. D-09 keeps the skeleton in place so v2 is purely additive (Flyway V2 + new endpoint + tool backing swap from product-service to search-service).
- **Conversational PDP summaries (AI-V2-03), compare-products (AI-V2-04), cart-aware suggestions (AI-V2-05)** — all v2.
- **Streaming chat UX with mid-stream cancel (FE-V2-01)** — Phase 11 frontend, marked v2.
- **mcp-server (Phase 9) auth bridge `MCP_API_KEY` → identity-service `/agents/exchange`** — Phase 9 owns the implementation; Phase 8 only ensures `agent-toolset` is shaped so mcp-server can import without modifications.
- **Conversation TTL cleanup job for guest sessions** — guest TTL is in-process Map eviction (D-03). No DB cleanup job needed since guests don't persist.
- **`/api/v1/search/**` v1 endpoint** — D-09 defers to v2. api-contracts §1 row stays annotated "v2 — see AI-V2-01"; the gateway route stays commented.

</deferred>

---

*Phase: 08-ai-port-adapter-agent-toolset*
*Context gathered: 2026-05-01*
