# Phase 9: MCP Server - Context

**Gathered:** 2026-05-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Stand up the new `mcp-server` Spring Boot service (currently absent from `settings.gradle.kts`) using `spring-ai-starter-mcp-server-webmvc 1.1.5`. Re-expose the Phase 8 `agent-toolset` (10 `AgentTool` Spring beans) over the MCP wire protocol via:

1. **Streamable HTTP transport** (current MCP spec 2025-06-18 standard) — published under `/mcp/**` through the existing api-gateway tunnel for the network demo.
2. **stdio transport** — same Jib image, launched by Claude Desktop via `docker run -i` for the local-subprocess demo.

External agents authenticate via `MCP_API_KEY` (env var) → identity-service `POST /agents/exchange` → internal RS256 JWT (24h, `sub` = bound user_id, roles=[ROLE_USER]). mcp-server caches the JWT and refreshes ~5 min before expiry; every outbound tool-driven REST call carries `Authorization: Bearer <cached>` plus `X-User-Id` derived from JWT.sub. Tool dispatch reuses the existing `agent-toolset/http` clients (`CartToolClient`, `OrderToolClient`, `ProductToolClient`, `PaymentToolClient`) — direct via Eureka discovery, not gateway-relayed (matches Phase 8 D-05 / Phase 6 internal-REST pattern).

The DRY architecture (Pitfall #16, CLAUDE.md Rule #2) is enforced structurally: mcp-server contains **zero** local tool definitions. A startup adapter (`AgentToolMcpRegistration`) wraps every `AgentTool` bean as a Spring AI `ToolCallback` (name/description/schema sourced from the bean) and exposes the `List<ToolCallback>` for the Spring AI MCP starter to publish via `tools/list`. An `infra-tests` integration test asserts mcp-server's `tools/list` payload is identical to the names/schemas of the `ToolRegistry` beans (the SC-1 grading proof).

Phase 9 also adds:
- **identity-service `POST /agents/exchange`** — accepts `X-MCP-Api-Key` header (or body field), looks up `agent_api_keys.api_key_hash`, mints a JWT for the bound `user_id`. New Flyway migration creates the `agent_api_keys` table; seed inserts one demo agent.
- **api-gateway** — explicit `/mcp/**` route entry in `config-server/src/main/resources/config/api-gateway.yml`; public allowlist for nothing (every MCP request must already carry the exchanged JWT). The route's metadata likely needs `response-timeout: -1` because Streamable HTTP can hold a long stream open (verify against MCP spec).
- **docker-compose.yml** — `mcp-server` service entry (depends on identity-service, eureka-server, config-server), `MCP_API_KEY` and `MCP_TRANSPORT=http` env vars wired; gateway mesh connectivity confirmed.

Phase 9 does **not** own:
- The chat-bubble UI (Phase 11).
- Real semantic search (deferred to v2 per AI-V2-01/02).
- Multiple agent-key roles or scopes beyond ROLE_USER.
- Per-MCP-session state inside mcp-server (mcp-server stays stateless — see Pitfall #10 posture below).
- Cloudflare Tunnel setup itself (Phase 11 deploy phase owns tunnel config; Phase 9 only confirms the `/mcp/**` route is healthy on a tunneled gateway).

</domain>

<decisions>
## Implementation Decisions

### Transport Posture & Tunnel Exposure

- **D-01:** **One Boot app, both transports.** Same `mcp-server` Jib image runs Streamable HTTP by default (the docker-compose service); when Claude Desktop spawns the same image with `MCP_TRANSPORT=stdio`, Spring AI's stdio transport activates. One artifact, one `MCP_TRANSPORT` env switch — minimum surprise, satisfies SC-2 (both transports working). Compose service env: `MCP_TRANSPORT=http`. Claude Desktop config sets `MCP_TRANSPORT=stdio` via `docker run -e`.
- **D-02:** **Streamable HTTP only — no deprecated SSE.** MCP spec 2025-06-18 deprecated SSE in favor of Streamable HTTP. Spring AI 1.1.5's `spring-ai-starter-mcp-server-webmvc` exposes Streamable HTTP; we don't wire the SSE compatibility surface. Risk: an old MCP client can't connect — but the bootcamp grader will use a current MCP Inspector / Claude Desktop. Researcher confirms exact starter capability flags at impl time.
- **D-03:** **Share the gateway tunnel under `/mcp/**`.** Resolves PROJECT.md / CLAUDE.md Open Question 2. The Cloudflare Tunnel hostname Phase 6 sets up for Iyzico is reused: `https://<tunnel>/mcp/**` → api-gateway → mcp-server (Eureka). One demo URL, one auth chain, one tunnel to monitor. mcp-server itself stays inside the docker-compose mesh (no direct tunnel). api-contracts §2 already reserves `/mcp/**` → mcp-server; Phase 9 activates it.
- **D-04:** **Claude Desktop demo path uses `docker run -i` against the published Jib image.** README ships the exact `claude_desktop_config.json` snippet: `{"mcpServers": {"n11-storefront": {"command": "docker", "args": ["run", "-i", "--rm", "--network=host", "-e", "MCP_TRANSPORT=stdio", "-e", "MCP_API_KEY=...", "ghcr.io/.../mcp-server:<tag>"]}}}`. Same artifact compose uses; no separate boot-jar release. Grader needs Docker (already a bootcamp expectation per DEV-07).

### Auth Bridge: API_KEY → JWT Exchange

- **D-05:** **DB-backed API keys.** `agent_api_keys (api_key_hash CHAR(64) PK, agent_label VARCHAR, user_id UUID FK → users.id, created_at TIMESTAMPTZ, last_used_at TIMESTAMPTZ NULL, revoked_at TIMESTAMPTZ NULL)` — new Flyway migration in identity-service schema. Hash on rest (SHA-256 base16 — preferred over BCrypt since lookup is by exact hash equality, not password verification). Flyway repeatable migration seeds **one** demo agent record bound to a real demo user; the plaintext API key is logged once at first run and persisted to `.env` (NEVER committed — `MCP_API_KEY=...` lives in `.env.example` as a placeholder). Supports rotation/revocation/audit — the security signal a grader expects. Cross-cuts QUAL-09 (no secrets in source).
- **D-06:** **JWT subject = real `user_id`.** `/agents/exchange` mints a JWT with `sub` = `agent_api_keys.user_id`, `roles=[ROLE_USER]`, 24h exp, same RS256 signing path as `/auth/login` (reuse `JwtIssuerService`). No `ROLE_AGENT` scope claim in v1 — keeps the JWT shape identical to a human login JWT, so cart-service / order-service / payment-service treat the agent's actions exactly like a logged-in user's. **Demo payoff:** Claude Desktop places an order; the same order shows up in the user's `Siparişlerim` page (AI-15-style "shared cart" continuity, applied to MCP).
- **D-07:** **mcp-server caches the JWT; refreshes ~5 min before expiry.** Single `AgentJwtCache` `@Component`: lazily exchanges on first tool invocation (or eagerly at boot if `MCP_EAGER_EXCHANGE=true`); stores `(jwt, expiresAt)`; a scheduled refresh runs every minute and re-exchanges when `expiresAt - now < 5 min`. All outbound `RestClient` calls in `agent-toolset/http` get the cached `Authorization: Bearer <jwt>` injected via a `ClientHttpRequestInterceptor`. Survives identity-service restart with one retry. One key, one JWT in flight — minimal moving parts; respects Pitfall #9 (Gemini/identity rate budget — but here it's identity calls, not Gemini).
- **D-08:** **MCP_API_KEY delivered via env var at process launch — never on the wire.** Claude Desktop (`docker run -e MCP_API_KEY=...`); compose service (`environment: MCP_API_KEY=${MCP_API_KEY}` reading from `.env`); mcp-server reads at boot via `@Value("${mcp.api-key}")` and never re-reads. Single key per running mcp-server instance — acceptable for the bootcamp demo posture. Trade-off: cannot support multiple agents with different keys against the same compose service (out of scope).

### Tool Registration with Spring AI

- **D-09:** **Adapter at startup, not `@McpTool` annotations.** Single `@Configuration AgentToolMcpRegistration`: inject `ToolRegistry`, build a `List<ToolCallback>` by wrapping each `AgentTool` (name = `tool.name()`, description = `tool.descriptionTr()`, inputSchema = `tool.parametersJsonSchema()`, handler closure = `(toolContextLike, args) → tool.execute(buildToolContext(...), args)`). Expose as a Spring bean; Spring AI's MCP starter auto-publishes them via `tools/list`. **Zero `@McpTool` annotations** in the mcp-server module — the AgentTool bean is the single source of truth for name + description + schema. Pitfall #16 mitigation is structural: there is exactly one place where tool metadata is declared.
- **D-10:** **`ToolContext` constructed per MCP tool call.** `userId` = `JWT.sub` from the cached agent JWT (= bound real user_id from D-06). `correlationId` = inbound `X-Correlation-Id` header or generated UUID (mirrors Phase 1 common-logging filter). `seenIds` = `Set.of()` (empty — see D-13). MCP request → ToolCallback handler → build ToolContext → invoke `AgentTool.execute(ctx, args)`. Tools see exactly what ai-service tools see when an authenticated user is chatting; no behavior divergence at the AgentTool layer.
- **D-11:** **Tool dispatch goes direct via Eureka — same as ai-service.** Reuse the existing `agent-toolset/http` clients (`CartToolClient`, `OrderToolClient`, `ProductToolClient`, `PaymentToolClient`) unchanged. They already use `lb://` URIs (Phase 8 D-05) + X-User-Id forwarding. mcp-server adds one bean: a `ClientHttpRequestInterceptor` on the shared `RestClient` that injects `Authorization: Bearer <AgentJwtCache.jwt>`. Pure DRY: zero new HTTP-client classes in mcp-server. Note on the SC-3 wording ("propagates through the gateway like a normal user") — the JWT/X-User-Id propagation pattern matches a normal user's path, but the actual hops are internal mesh (the gateway is the trust edge, internal services trust the mesh per CLAUDE.md auth posture). The grader-visible behavior is identical: a tool-driven order is indistinguishable from a web-driven order in the DB.
- **D-12:** **DRY proof = `infra-tests` integration test.** New test class in `infra-tests/src/test/java/com/n11/infra/mcp/`: spins up mcp-server's Spring context, lists `ToolCallback` beans by name, asserts every name in `ToolRegistry.all()` appears exactly once and that the `inputSchema` JSON matches the corresponding `AgentTool.parametersJsonSchema()` byte-for-byte (after canonicalization). Plus a runtime sanity check: hit `/mcp/messages` with a JSON-RPC `tools/list` request, parse, count must equal 10, names must equal `ToolRegistry.all().stream().map(AgentTool::name).toSet()`. Apply `infra-tests` Plan 05-04 lessons: `McpServerTestConfig` with excludeFilters for other services' `@SpringBootApplication` classes; bean disambiguation if needed.

### Pitfall #10 Provenance Posture (MCP)

- **D-13:** **Live-lookup only — no `seenIds` tracking in mcp-server.** Drop the conversation-scoped provenance check (Phase 8 D-08) for the MCP surface. Rationale: the calling LLM (Claude Desktop, etc.) owns the conversation; mcp-server has no visibility into prior tool results that the LLM has stored on its side. Tracking seenIds inside mcp-server is self-defeating — the same agent we're trying to protect against is the one populating the Set. **Pitfall #10 still mitigated** via live lookup: every `productId` / `orderId` / `cartItemId` is verified against the backing service before mutating; 404 / 409 responses get mapped to `ToolResult.Err` with semantic codes. Backing services (cart-service, order-service, product-service, payment-service) are the authoritative ID gate.
- **D-14:** **Backing-service errors → `ToolResult.Err` with semantic codes.** Reuse the existing `ToolHttpException` catch in `agent-toolset/http`: HTTP 404 → `ToolResult.Err("NOT_FOUND", "<resource> not found")`; 409 → `ToolResult.Err("CONFLICT", "...")`; 401/403 → `ToolResult.Err("AUTH_REQUIRED", "...")`; 5xx / timeout → `ToolResult.Err("UPSTREAM_ERROR", "...")`. Spring AI's MCP starter serializes `ToolResult.Err` as a tool-response with the error message; the calling LLM sees it and self-corrects (e.g., re-runs `search_products`). Same pattern ai-service uses → cross-surface error parity.
- **D-15 (research item):** **Verify whether any of the 10 `AgentTool` implementations read `ctx.seenIds()` directly.** Phase 8 D-08 placed the seenIds check in ai-service's ToolDispatcher (BEFORE `execute`), so AgentTool implementations should NOT depend on seenIds being non-empty. The researcher confirms by `grep -r 'ctx.seenIds()\|context.seenIds()' agent-toolset/`. If any tool reads it, the planner moves the check to a wrapper layer that ai-service applies and mcp-server skips. **Expected outcome:** zero tool reads it; D-13 holds without code change. **If tools do read it:** Plan adds a `ProvenanceCheckingDispatcher` layer applied only by ai-service.

### Claude's Discretion

The following are deferred to the planner / researcher and do not require user input:

- **Spring AI `ToolCallback` vs `ToolDefinition` API surface** — researcher fetches `https://docs.spring.io/spring-ai/reference/api/tools.html` and `.../mcp/mcp-server-boot-starter-docs.html` to confirm the exact bean type Spring AI 1.1.5 expects. The adapter shape (D-09) is conceptual; the bean class name + collection type comes from current docs.
- **Streamable HTTP route metadata for the gateway** — `metadata.response-timeout: -1` may or may not be needed for `/mcp/**` depending on how long Streamable HTTP holds connections open. If it does, the route entry mirrors api-contracts §6 SSE pattern; if not, default config suffices. Researcher confirms; planner picks.
- **Hash algorithm for `agent_api_keys.api_key_hash`** — D-05 says SHA-256 base16. If the planner finds a stronger fit (e.g., HMAC-SHA-256 with a server pepper), they may swap; the column type stays CHAR(64).
- **MCP capability negotiation flags** — what tools/list, tools/call, resources, prompts capabilities the mcp-server advertises in `initialize` response. Default minimum: `tools` only (we don't expose resources or prompts in v1). Researcher confirms Spring AI starter defaults match.
- **`MCP_EAGER_EXCHANGE` switch** — whether mcp-server eagerly hits `/agents/exchange` on startup (faster first tool call, but identity-service must be up at boot) or lazily on first tool call (loose coupling, slightly slower first call). Planner picks; default lazy (fits Pitfall #4 startup-order discipline).
- **Springdoc surface for mcp-server** — likely none in v1 (MCP is JSON-RPC over a single endpoint, not REST). Skip Springdoc dep on this service unless it costs nothing.
- **Bean disambiguation in `infra-tests`** — apply Plan 05-04 lesson preemptively when mcp-server joins the multi-service classpath (`@Entity(name=...)`, `@RestController(beanName)`, `@Component(beanName)` for any clashing names).
- **`/agents/exchange` request body shape** — JSON body `{ "apiKey": "..." }` (preferred — easier curl demo) vs. `X-MCP-Api-Key` header (preferred — keeps key out of body logs). Planner picks; document in api-contracts §1 update.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Scope and Requirements

- `.planning/ROADMAP.md` — Phase 9 entry: goal, dependencies (Phase 8), success criteria 1–4 (DRY proof, dual-transport demo, mutating-tool e2e via auth bridge, zero local tool defs), risks (Pitfall #15 / #16 / #23-equivalent for auth bridge), HIGH research need.
- `.planning/REQUIREMENTS.md` — AI-11 (single agent-toolset), AI-12 (stdio + HTTP+SSE both wired), AI-13 (MCP_API_KEY → /agents/exchange → JWT). Phase 9 owns these three; AI-14/15 stay in Phase 8.
- `.planning/PROJECT.md` — Key Decision rows "Chat assistant + MCP server share one toolset" and "Provider-agnostic LLM abstraction"; Open Question 2 "MCP server tunnel exposure" — **resolved by D-03 (share gateway tunnel)**.
- `.planning/STATE.md` — handoff from Phase 8; Next: `/gsd-discuss-phase 9` then `/gsd-plan-phase 9`. Blockers/Concerns row "Iyzico webhook public reachability" is the parent of D-03's tunnel-share decision (same Cloudflare Tunnel).
- `CLAUDE.md` — Non-negotiable Rule #1 (zero-dep `ai-port`, no SDK leak — applies to mcp-server too: zero `org.springframework.ai.*` types in agent-toolset signatures); Rule #2 (one toolset, two consumers — D-09); Rule #4 (verify SDK docs); AI library split (Spring AI **only** for MCP wire protocol; never for Gemini chat).

### Architecture and Contracts

- `.planning/research/ARCHITECTURE.md` §2.13 (mcp-server bounded context — re-exposes agent-toolset via MCP, stateless, Eureka client), §6.2 (Shared Agent Toolset — `AgentTool` interface, dispatch flow, "@Tool annotation can additionally drive auto-registration in mcp-server"), §10 anti-pattern 4 (don't forward raw JWT downstream — applies: mcp-server forwards X-User-Id, NOT the JWT, to internal mesh services).
- `.planning/api-contracts.md` §1 identity-service row "POST /agents/exchange — API-key (Phase 9) — Exchange MCP_API_KEY for internal JWT"; §1 mcp-server section "POST /mcp/messages — API-key → exchanged JWT — MCP HTTP+SSE transport" (rewrite to "Streamable HTTP" per D-02; flag as a doc update for the planner); §2 routing row `/mcp/**` → mcp-server; §3 public allowlist (mcp-server adds NO entries — every MCP request must already carry the exchanged JWT after D-07; the gateway validates it like any other authed path); §4 auth strip + X-User-Id injection (applies); §5 correlation-ID (applies); §6 SSE caveat (verify Streamable HTTP needs the same `response-timeout: -1` override).
- `.planning/saga-contracts.md` — mcp-server has no AMQP consumer or publisher in v1; included only as cross-reference if a future tool needs to publish events.

### Research and Pitfalls

- `.planning/research/STACK.md` "MCP Server Implementation" — Spring AI MCP Server starter `1.1.5` confirmed (`org.springframework.ai:spring-ai-starter-mcp-server-webmvc`), MCP Java SDK `1.1.2` brought in transitively, MCP spec revision **2025-11-25** (note: D-02 cites 2025-06-18 as the deprecation source for SSE — researcher reconciles which spec rev is current and whether 2025-11-25 reaffirms the deprecation). Recommended pattern: `@McpTool`/`@McpToolParam` annotations; **D-09 deviates** — adapter approach is more DRY-correct for our case.
- `.planning/research/PITFALLS.md` — **Pitfall 15** (MCP transport mismatch — D-01 + D-02 mitigate), **Pitfall 16** (toolset duplicated — D-09 + D-12 mitigate structurally), **Pitfall 9** (Gemini RPM — N/A for mcp-server itself but the agent calling MCP may have its own quota), **Pitfall 18** (clock skew between identity and gateway — applies to mcp-server's cached JWT exchange too; the same `JwtTimestampValidator(30s)` posture from Phase 3 holds), **Pitfall 4** (startup order — mcp-server `depends_on` identity-service for `/agents/exchange` reachability; D-07 lazy-exchange mode tolerates identity-service flapping mid-demo).

### Prior-Phase Context

- `.planning/phases/01-foundations-day-1-contracts/01-CONTEXT.md` — service-template archetype (use it for mcp-server), `:common-error` + `:common-logging` deps (apply), `spring.config.import=optional:configserver:...` posture, Eureka client retry config, Jib pre-build pattern (`./gradlew :mcp-server:jibDockerBuild` before `docker compose up -d` — applies for any `application.yml` edit served from config-server).
- `.planning/phases/03-identity-gateway-auth/03-CONTEXT.md` — `JwtIssuerService` (reused for `/agents/exchange`), `JwtConfig` (RS256 keys), api-gateway `JwtTimestampValidator(30s)` + JWKS posture, `IdentitySecurityConfig` (extend with `/agents/exchange` public-but-API-key-validated route).
- `.planning/phases/06-payment-iyzico/06-CONTEXT.md` — D-05 internal-REST hop pattern (X-User-Id forwarding through `lb://` URIs) is the exact analog mcp-server tool dispatch reuses (via the existing `agent-toolset/http` clients).
- `.planning/phases/08-ai-port-adapter-agent-toolset/08-CONTEXT.md` — **the foundational doc**: D-04 (mutating tools enforce auth via `requiresAuth()`), D-05 (X-User-Id forwarding), D-06 (manual function-calling — applies to ai-service only, NOT mcp-server which is just a tool surface), D-08 (Pitfall #10 mitigation in ai-service ToolDispatcher — D-15 here verifies AgentTool implementations don't depend on it). The 10 tools, `ToolContext`, `ToolResult`, `ToolRegistry`, `ToolHttpClients` all defined here. Existing code at `agent-toolset/src/main/java/com/n11/agent/{AgentTool,ToolContext,ToolRegistry,ToolResult}.java` and `agent-toolset/src/main/java/com/n11/agent/tools/*.java` (10 implementations) and `agent-toolset/src/main/java/com/n11/agent/http/*.java` (4 clients).

### External SDK Documentation (verify-before-implement, CLAUDE.md Rule #4)

- `https://modelcontextprotocol.io/specification/2025-06-18/basic/transports` — Streamable HTTP is current; SSE deprecated. Confirms D-02.
- `https://modelcontextprotocol.io/specification` — latest spec revision (researcher reconciles 2025-06-18 vs STACK.md's stated 2025-11-25 — pick whichever is current at impl date).
- `https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html` — `@McpTool`/`@McpToolParam` annotations, WebMVC starter, Streamable HTTP + (deprecated) SSE transport modes, capability negotiation, `tools/list` payload shape, error mapping for `ToolResult`.
- `https://docs.spring.io/spring-ai/reference/api/tools.html` — `ToolCallback` / `ToolDefinition` API surface (D-09 adapter writes against this).
- `https://github.com/modelcontextprotocol/java-sdk` — `io.modelcontextprotocol.sdk:mcp:1.1.2` — pulled transitively by Spring AI; don't depend directly.
- `https://mcpcat.io/guides/comparing-stdio-sse-streamablehttp/` — practical decision guide (Pitfall #15 source).
- `https://github.com/modelcontextprotocol/inspector` — `npx @modelcontextprotocol/inspector` for the `tools/list` runtime check in D-12.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`agent-toolset/`** module — already in `settings.gradle.kts`; contains `AgentTool` interface, `ToolContext(userId, correlationId, seenIds)`, `ToolRegistry` (Spring `@Component` aggregator), `ToolResult` sealed interface, 10 `AgentTool` `@Component` implementations under `com/n11/agent/tools/`, and 4 HTTP clients under `com/n11/agent/http/` (`CartToolClient`, `OrderToolClient`, `PaymentToolClient`, `ProductToolClient`, plus `ToolHttpClients` autoconfig and `ToolHttpException`). Phase 9 imports this module verbatim — no edits to it. `ToolRegistry.all()` → 10 `AgentTool` beans (the truth source).
- **`service-template/skeleton/`** — Gradle archetype used by every Boot service. mcp-server clones it: `plugins { id("org.springframework.boot"); id("com.google.cloud.tools.jib") }`, Java 21 toolchain via root `subprojects { }`, `:common-error` + `:common-logging` deps, `application.yml` with `spring.config.import=optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}`. mcp-server does NOT need `:common-events` or `:common-outbox` (no AMQP). Adds `org.springframework.ai:spring-ai-starter-mcp-server-webmvc:1.1.5` and `:agent-toolset` deps.
- **`identity-service/src/main/java/com/n11/identity/auth/JwtIssuerService.java`** — already exists (Phase 3); produces RS256 JWT with `sub`, `iat`, `exp`, `roles`, `email`, `fullName` claims, 24h TTL. `/agents/exchange` calls `jwtIssuerService.issue(userId, email, fullName, List.of("ROLE_USER"))` — exactly the same payload shape as `/auth/login`.
- **`identity-service/src/main/java/com/n11/identity/auth/IdentitySecurityConfig.java`** — extend the public route list with `POST /agents/exchange` (the API-key check happens inside the controller, not via Spring Security — same shape as `/auth/login`).
- **`api-gateway/src/main/java/com/n11/gateway/SecurityConfig.java`** — already has the `permitAll() / authenticated()` matrix from Phase 3. Phase 9 verifies `/mcp/**` falls into the `.anyExchange().authenticated()` bucket (it should — no explicit allow). The gateway then validates the agent's exchanged JWT exactly like a normal user JWT.
- **`config-server/src/main/resources/config/api-gateway.yml`** — Phase 8 activated the SSE route for ai-chat; Phase 9 adds an explicit `/mcp/**` route entry if Streamable HTTP needs the `response-timeout: -1` override. If it doesn't, the discovery-locator default route (`lb://mcp-server`) suffices.
- **`agent-toolset/src/main/java/com/n11/agent/http/ToolHttpClients.java`** — central RestClient autoconfig for the 4 backing services. Phase 9 adds **one** `ClientHttpRequestInterceptor` to the shared `RestClient.Builder` (or wraps each client) that injects `Authorization: Bearer <AgentJwtCache.jwt>` for outbound calls when running inside mcp-server. Use `@ConditionalOnBean(AgentJwtCache.class)` so the interceptor only activates in mcp-server (not in ai-service, which uses a different auth path — direct X-User-Id forwarding from inbound request).
- **`infra-tests/`** module — already exists from Plan 05-04; mcp-server joins as a new `@SpringBootApplication` classpath entry. Plan 05-04 lessons apply (`McpServerTestConfig` with excludeFilters; bean disambiguation if needed).
- **`infra/postgres/init.sh`** — extend with no new schema (mcp-server is stateless — no DB). The `agent_api_keys` table lives in the **identity** schema, owned by `identity_user`.

### Established Patterns

- **Phase 8 D-05 internal REST hop** — mcp-server's tool dispatch follows the same X-User-Id forwarding pattern. The new wrinkle is that the X-User-Id is derived from the cached agent JWT (D-07/D-10), not from an inbound request header.
- **Phase 6 D-05 / `OrderInternalClient`** — same internal-REST shape; `ToolHttpClients` already mirrors it.
- **Plan 05-04 multi-service infra-tests** — apply `@Entity(name=...)`, `@RestController(beanName)`, `@Component(beanName)` disambiguation pre-emptively; use `excludeFilters` in test configs to prevent foreign `@SpringBootApplication`s from expanding entity scan.
- **Plan 05-04 Flyway subdirectory** — mcp-server has no Flyway migrations (no DB), so this doesn't apply. **identity-service**'s new `agent_api_keys` migration sits in `identity-service/src/main/resources/db/migration/V<N>__agent_api_keys.sql` (whatever the next sequence number is).
- **Phase 1 Pitfall #4** — startup retry posture: mcp-server is a Eureka client and a config-server client; baseline retry config in `service-template/application.yml` already covers cold-boot. Identity-service `/agents/exchange` reachability handled by D-07 lazy exchange.
- **Springdoc per-service** — mcp-server likely skips Springdoc (MCP isn't REST). QUAL-01 satisfied by virtue of mcp-server having no human-facing REST surface.

### Integration Points

- **`docker-compose.yml`** — add `mcp-server` service entry: depends_on identity-service, eureka-server, config-server (NO postgres — mcp-server has no DB); env: `MCP_TRANSPORT=http`, `MCP_API_KEY=${MCP_API_KEY}`, `CONFIG_SERVER_URL`, `EUREKA_URI` (same as other services); ports: internal-only (gateway is the public face). Stale-Jib-image hazard from Plan 01-06 applies: editing `config-server/.../api-gateway.yml` requires `:config-server:jibDockerBuild` before `docker compose up -d`.
- **`api-gateway`** — SecurityConfig already enforces auth on `/mcp/**`; api-gateway.yml route entry possibly needs `metadata.response-timeout: -1` (see Claude's Discretion). Discovery-locator handles the `/mcp/**` → `lb://mcp-server` mapping by default.
- **`identity-service`** — adds `AgentExchangeController` (`POST /agents/exchange`), `AgentApiKey` entity + Flyway migration, `AgentApiKeyRepository`, `AgentExchangeService` (validates hash, mints JWT). Endpoint shape: accepts `{ "apiKey": "..." }` JSON or `X-MCP-Api-Key` header; returns `{ "accessToken": "<jwt>", "expiresIn": 86400 }`. Public allowlist exception added in `IdentitySecurityConfig`.
- **`api-contracts.md` doc update** — §1 mcp-server row "MCP HTTP+SSE transport" → "MCP Streamable HTTP transport (+ stdio for local-subprocess demo)"; §1 identity-service row already lists `/agents/exchange` (no change). Planner queues this as a Phase 9 documentation task.
- **`README.md`** — Phase 11 owns the master README, but Phase 9 leaves a runbook stub at `.planning/phases/09-mcp-server/RUNBOOK.md` (or similar): the `claude_desktop_config.json` snippet, the `npx @modelcontextprotocol/inspector` command, the demo `MCP_API_KEY` (regenerated each demo run), and the verification curl: `curl -X POST https://<tunnel>/mcp/messages -H "Authorization: Bearer <jwt>" -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'`.
- **`infra-tests`** — new test class `McpServerToolsListEqualityTest` for D-12; new `McpServerTestConfig` per Plan 05-04 pattern.

</code_context>

<specifics>
## Specific Ideas

- **The "agentic e-commerce over the network" demo moment graders see:** open Claude Desktop → MCP `n11-storefront` server lists 10 tools → "Bana bir kulaklık öner" → Claude calls `search_products` → Claude shows results → "İlk ürünü sepete ekle" → Claude calls `add_to_cart` (mutating, hits the auth bridge) → Claude calls `view_cart` → confirm → Claude calls `create_order` → Claude calls `get_payment_link` → grader opens the link in a browser → enters Iyzico test card `5528 7900 0000 0008` → Iyzico 3DS callback → order CONFIRMED → grader logs into the n11 storefront as that demo user and sees the order in `Siparişlerim`. End-to-end "Claude Desktop → real e-commerce checkout" is the Phase 9 + Phase 11 wow shot.
- **Single-line DRY proof for the README demo-script:** `diff <(jq '.tools[].name' < tools-list.mcp.json | sort) <(curl -s http://ai-service/internal/tools | jq '.[].name' | sort)` returns empty. Or, more elegantly, the integration test name itself: `McpServerToolsListEqualityTest.assertIdenticalToolCatalog()` printed in CI output.
- **Pitfall #15 demo-mode probe:** at boot, mcp-server logs `MCP transport: ${MCP_TRANSPORT}, capabilities: tools, version: ${spring-ai.version}`. First log line a demo viewer sees confirms which transport activated.
- **Audit log for `/agents/exchange`:** identity-service updates `agent_api_keys.last_used_at` on every successful exchange. `SELECT agent_label, last_used_at FROM agent_api_keys WHERE last_used_at > NOW() - INTERVAL '1 hour';` is a 30-second demo moment showing the agent's footprint in the identity layer.

</specifics>

<deferred>
## Deferred Ideas

- **Per-agent role scoping** — D-06 mints `ROLE_USER` for every agent. A future iteration could add `ROLE_AGENT_READONLY` (only read tools allowed) and gate via Spring Security. Out of scope for v1.
- **Multi-key per mcp-server instance** — D-08 supports one key per process. A future iteration could read keys per request (header-based) supporting many agents on one mcp-server. Out of scope.
- **MCP `resources` and `prompts` capabilities** — Phase 9 advertises only `tools`. Resources (catalog images? PDP documents?) and Prompts (canned shopping flows?) are differentiator opportunities for v2.
- **Stronger key hashing (HMAC + server pepper)** — D-05 SHA-256 base16 is sufficient for demo. Production posture would HMAC with a server pepper from Spring Cloud Config; out of scope.
- **API key rotation endpoint** — `POST /agents/{id}/rotate` in identity-service. The schema supports it (`revoked_at` column) but the endpoint itself is out of scope.
- **`ROLE_AGENT` audit claim** — D-06 considered but rejected for v1. Adding a non-blocking audit claim would be a 5-minute v2 enhancement.
- **`MCP_EAGER_EXCHANGE=true` flag for faster cold start** — D-07 default is lazy. Could add eager mode if the demo first-response latency turns out to be felt.
- **Streamable HTTP keep-alive tuning** — if Streamable HTTP holds long-running connections, gateway `response-timeout: -1` plus connection-keepalive config may need attention. Researcher confirms; deferred to planner.

</deferred>

---

*Phase: 09-mcp-server*
*Context gathered: 2026-05-01*
