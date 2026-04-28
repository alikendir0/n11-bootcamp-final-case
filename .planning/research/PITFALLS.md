# Pitfalls Research

**Domain:** Spring Boot microservices + SAGA + Iyzico + agentic-commerce (bootcamp final case, 6-day window, AI-assisted, SOLID-graded)
**Researched:** 2026-04-28
**Confidence:** HIGH for Spring Cloud / RabbitMQ / Iyzico / Gemini items (verified against official docs and current 2026 sources). MEDIUM for MCP transport recommendations (spec is moving). LOW for the bootcamp-specific "what graders react to" claims (synthesized from the brief; no public post-mortems for this exact bootcamp).

This file is the pre-phase checklist for the planner. Every pitfall maps to a project-area phase and lists how to detect it before it kills the demo. Severity reflects "how badly does this hurt the grade or the demo?" — HIGH means a single recurrence can sink the submission; MEDIUM means it visibly degrades quality; LOW means it costs time but is recoverable.

Phase-area names used below (the roadmap will assign numbers):

- **P:Foundations** — repo, parent pom, Eureka, config-server, gateway, JWT path, observability scaffolding
- **P:Domain-Services** — product, inventory, cart, order, identity, notification (CRUD + JPA + Flyway + tests)
- **P:Saga** — order saga events, RabbitMQ topology, idempotency, compensation
- **P:Payment** — Iyzico sandbox integration, 3DS callback, payment-service
- **P:AI-Core** — provider-agnostic LLM port, Gemini adapter, conversation state, tool dispatcher
- **P:Search-RAG** — pgvector + embeddings + semantic search
- **P:MCP** — MCP server exposing the toolset to external agents
- **P:Frontend** — React storefront + chat bubble + Turkish copy
- **P:DevOps** — Jib, GitHub Actions, local docker-compose, Cloudflare Tunnel / ngrok, Slack
- **P:Demo-Polish** — seed data, scripts, walkthrough, README

---

## Critical Pitfalls

### Pitfall 1: Verification miss on Gemini 3 Flash model identifier

**Severity:** HIGH

**What goes wrong:**
Code is written against a guessed model name (e.g., `gemini-3.0-flash`, `gemini-3-flash`, `models/gemini-3.0-flash-latest`) that does not match what the Generative Language API currently serves. Every chat call returns 404 or `MODEL_NOT_FOUND`. Hours are spent chasing auth/SDK before realizing the identifier was wrong.

**Why it happens:**
The candidate's training data and PROJECT.md both reference "Gemini 3.0 Flash" colloquially. The current public identifier on `ai.google.dev` is **`gemini-3-flash-preview`** (preview status as of April 2026), and there is also `gemini-3.1-flash-lite-preview`. The colloquial name and the API identifier are not the same, and "preview" suffix matters.

**How to avoid:**
1. Before the AI port is wired, fetch [ai.google.dev/gemini-api/docs/models](https://ai.google.dev/gemini-api/docs/models) and copy the exact identifier into `application.yml` as `ai.gemini.model: gemini-3-flash-preview` (or whatever is current at research time).
2. Treat the model name as configuration, not a constant. Single property, no string-literals in code.
3. On `ai-service` startup, log the model identifier and the resolved provider class. First log line on boot.
4. Have a fallback chain in config: primary `gemini-3-flash-preview`, fallback `gemini-2.5-flash` (stable). If primary returns 404 on first probe, log and switch.

**Warning signs:**
- 404 / `INVALID_ARGUMENT` on the first chat call, not a timeout.
- Error body mentions "model not found" or lists available models.
- Demo works in `ai.google.dev` console but fails from the service.

**Phase to address:** P:AI-Core (verification gate before any tool-call code is written).

---

### Pitfall 2: Spring Cloud Gateway reactive vs MVC classpath collision

**Severity:** HIGH

**What goes wrong:**
The gateway module accidentally pulls `spring-boot-starter-web` (e.g., a developer added Springdoc with the wrong starter, or copied `pom.xml` from a domain service). Spring Boot starts in Servlet mode, the reactive `GatewayAutoConfiguration` cannot find `ServerCodecConfigurer`, the gateway fails to boot with `Parameter 0 of method modifyRequestBodyGatewayFilterFactory ... required a bean of type 'ServerCodecConfigurer' that could not be found` — and on the day of the demo nothing routes.

**Why it happens:**
Spring Cloud has two gateway flavors now (reactive WebFlux, and the newer Gateway MVC). They are **not compatible** with each other. Mixing `spring-boot-starter-web` with the reactive `spring-cloud-starter-gateway` is a documented incompatibility (spring-cloud/spring-cloud-gateway #1004). Springdoc-openapi has different artifacts for WebFlux (`springdoc-openapi-starter-webflux-ui`) vs MVC (`springdoc-openapi-starter-webmvc-ui`); the wrong one drags in the wrong web stack.

**How to avoid:**
1. Pick one model for the gateway up front. Default: **reactive** (`spring-cloud-starter-gateway`). If you want MVC + virtual threads, pick `spring-cloud-starter-gateway-mvc` instead — but **do not mix**.
2. In gateway `pom.xml`: use `spring-boot-starter-webflux` (transitively from gateway), `springdoc-openapi-starter-webflux-ui`. Add an `<exclusion>` for `spring-boot-starter-web` on every dependency that might bring it.
3. Add a Maven Enforcer rule (`bannedDependencies`) on the gateway module that fails the build if `spring-boot-starter-web` appears anywhere in the resolved tree.
4. The gateway's smoke test on CI must boot the app context — not just compile.

**Warning signs:**
- Gateway boot fails with `ServerCodecConfigurer` bean error.
- `mvn dependency:tree` on gateway shows `spring-boot-starter-web`.
- Health endpoint binds on Tomcat, not Netty (look at startup logs).

**Phase to address:** P:Foundations (locked at scaffolding time, before any route is added).

---

### Pitfall 3: Non-idempotent SAGA consumers + duplicate orders

**Severity:** HIGH

**What goes wrong:**
RabbitMQ provides at-least-once delivery. A consumer in `order-service` or `payment-service` processes the same `StockReserved` or `PaymentCompleted` event twice (network blip, ack lost, requeue). Result: two orders charged, or stock decremented twice, or the customer gets two "Siparişiniz alındı" emails. Graders running the demo see ghost orders.

**Why it happens:**
Developers assume "I sent one event, I'll get one delivery." RabbitMQ's [Reliability Guide](https://www.rabbitmq.com/docs/reliability) explicitly states the broker may redeliver after an unacked message or a connection drop — exactly-once is **not** offered. Bootcamp tutorials skip idempotency because the happy path looks fine.

**How to avoid:**
1. Every event carries a `messageId` (UUID) and `correlationId`. Producer sets both.
2. Every consumer keeps a `processed_messages(message_id PK, consumer_name, processed_at)` table per service-DB. First step in the handler: `INSERT ... ON CONFLICT DO NOTHING` and skip if conflict.
3. State transitions in the saga must be guarded: `UPDATE order SET status = 'PAID' WHERE id = ? AND status = 'PENDING_PAYMENT'`. If `rowsAffected == 0`, ack and return.
4. Configure a DLQ on every queue with `x-dead-letter-exchange` + `x-message-ttl` for retry, capped retry count via `x-death` header (see [RabbitMQ DLX docs](https://www.rabbitmq.com/docs/dlx)).
5. Consumer code template lives in a shared `messaging-commons` module. Copy-paste idempotency is fragile.

**Warning signs:**
- Demo seed creates 1 order via UI but order-service shows 2 rows.
- Inventory drops by 2 for a single click.
- Logs show same `messageId` twice with non-conflicting timestamps.

**Phase to address:** P:Saga (idempotency table + correlation-id baked into the first event handler, not bolted on later).

---

### Pitfall 4: Eureka registration race on cold boot

**Severity:** HIGH (during demo) / MEDIUM (in dev)

**What goes wrong:**
Services start via `docker-compose up`. The gateway, identity, product, etc. boot before `eureka-server` finishes. Clients try to register, fail silently (DNS/connection refused), and only re-attempt on the default 30s heartbeat cycle. The first 60–90 seconds of the demo show "503 Service Unavailable" or "no instances available."

**Why it happens:**
Docker `depends_on` guarantees container start order, not application readiness. Eureka has a known cold-start lag (cache `responseCacheUpdateIntervalMs` defaults to 30s) — even after eureka is up, registered clients aren't visible to the gateway for up to 30s. See [Netflix/eureka #1150](https://github.com/Netflix/eureka/issues/1150).

**How to avoid:**
1. Eureka server gets a real Spring Boot Actuator health probe (`/actuator/health` with `liveness`/`readiness` groups). docker-compose `depends_on.condition: service_healthy` waits on the readiness probe, not on container "started."
2. On clients, set `eureka.client.initial-instance-info-replication-interval-seconds: 5` and `eureka.client.registry-fetch-interval-seconds: 5` in dev — fast re-fetch.
3. On Eureka server: `eureka.server.response-cache-update-interval-ms: 5000`, `eureka.server.eviction-interval-timer-in-ms: 5000`. Disables the production cache for demo speed.
4. Disable self-preservation on the demo Eureka (`eureka.server.enable-self-preservation: false`) — otherwise stale instances linger after restart.
5. Document a 60s "warm-up" line in the demo script. Don't start narrating until the gateway shows all 11 service instances.

**Warning signs:**
- `Application is not registered yet with Eureka` repeated in client logs.
- Gateway `/actuator/gateway/routes` returns empty for the first minute.
- Hitting `/api/products` returns 503 right after boot.

**Phase to address:** P:Foundations.

---

### Pitfall 5: Iyzico 3DS callback handler missing → orders hang in PENDING_PAYMENT

**Severity:** HIGH

**What goes wrong:**
Sandbox is configured for 3DS (which Iyzico recommends in production). The customer enters the test OTP, Iyzico POSTs to the configured `callbackUrl`, but `payment-service` doesn't expose that endpoint (or it's behind a JWT filter that rejects the unauthenticated POST from Iyzico, or the URL is `localhost:8080` and Iyzico can't reach it). The order sits in PENDING_PAYMENT forever; no `PaymentCompleted` event is fired; the saga never advances. The demo dies at the moment the grader is most paying attention.

**Why it happens:**
Iyzico's 3DS flow: `init-3ds` returns an HTML form, customer submits it to Iyzico, Iyzico redirects/POSTs back to your `callbackUrl` with `paymentId`, `conversationData`, `mdStatus`, `status`. See [docs.iyzico.com 3DS Implementation](https://docs.iyzico.com/en/payment-methods/direct-charge/3ds/3ds-implementation). The callback is a server-to-browser-to-server pattern, so the URL must be **publicly reachable** and **unauthenticated**.

**How to avoid:**
1. Decision up front: **for the demo, prefer Iyzico Checkout Form (hosted page)** over Direct 3DS — Iyzico hosts the page, your only handler is the result callback. Less surface area.
2. Whitelist the callback path in the gateway (`/api/payment/iyzico/callback` → no JWT filter; verify request signature instead via Iyzico's `payment.retrieve(paymentId)` server-side).
3. For local dev: use ngrok or Cloudflare tunnel to expose the callback endpoint. Bake the public URL into `application-dev.yml`.
4. Idempotent callback handler — Iyzico may retry. Use the `paymentId` as the dedup key (links to Pitfall 3).
5. Dedicated integration test: mock Iyzico response, POST to the callback, assert `PaymentCompleted` event published.

**Warning signs:**
- Order status frozen at PENDING_PAYMENT after submitting the test card.
- `payment-service` logs show no inbound POST after redirect.
- Iyzico dashboard shows status "SUCCESS" but order DB doesn't.
- 401/403 in access log on `/iyzico/callback`.

**Phase to address:** P:Payment.

---

### Pitfall 6: JWT secret committed to git or duplicated across services

**Severity:** HIGH (security + demo credibility)

**What goes wrong:**
The HMAC signing secret for JWT lives in `application.yml` and gets committed. Or each service has its own copy of the secret in its own config, and they drift — gateway validates with one secret, identity-service signs with another, and tokens silently fail validation in production but pass in tests because both run with the same defaults.

**Why it happens:**
The locked auth posture is "JWT validated only at the gateway." That's correct, but the secret still needs to live somewhere. Naïve solutions copy it into every `application.yml`. Bootcamp examples often hardcode secrets for "simplicity."

**How to avoid:**
1. Secret lives in `config-server` only, fetched at boot by `identity-service` (signer) and `api-gateway` (verifier). No other service ever sees it.
2. Local dev: env var `JWT_SECRET` injected by `.env` (gitignored). `application.yml` reads `${JWT_SECRET}`.
3. Local docker-compose deploy: same `.env` flow used in dev — gitignored `.env` on the candidate's host, loaded by `docker compose --env-file`. Production-grade secret stores (Vault, Doppler) noted in the README as a "next iteration" upgrade.
4. Pre-commit hook runs `gitleaks` or `detect-secrets`. CI re-runs it.
5. Use **RS256 with a keypair** instead of HS256 if time permits — public key on the gateway, private key on identity-service. No shared secret to leak. This is also a SOLID/security signal worth points.

**Warning signs:**
- `git log -p | grep -i 'jwt.*secret'` returns hits.
- `application.yml` files contain the literal string for the secret.
- Tokens issued by identity-service are rejected by the gateway with `JWT signature does not match`.

**Phase to address:** P:Foundations (gateway + identity), validated in P:DevOps.

---

### Pitfall 7: Leaky `ChatProvider` abstraction defeats the SOLID demo

**Severity:** HIGH (this is the entire grading thesis)

**What goes wrong:**
The `ChatProvider` interface accepts and returns Gemini's `Content`/`Part`/`FunctionCall` types directly, or its method signatures use Gemini-specific enums (`HarmCategory`, `BlockReason`). Anyone reading the code sees that "swap to OpenAI" still requires touching the entire service layer. The graders' single most-leaning question — "show me your SOLID example" — is answered with a leaky abstraction.

**Why it happens:**
Time pressure plus AI-assisted coding plus Gemini's SDK shape: the easy thing is to forward the SDK types. Refactoring to neutral DTOs is "extra work" that doesn't change runtime behavior, so it gets skipped.

**How to avoid:**
1. Define `ChatProvider`, `EmbeddingProvider`, and the message/tool DTOs in a `ai-port` module that has **zero** Google AI SDK dependency. Verify with `mvn dependency:tree` that the port module has no `com.google.genai` artifacts.
2. The DTOs use neutral names: `ChatMessage(role, content, toolCalls)`, `ToolCall(name, argsJson)`, `ToolResult(toolCallId, resultJson)`. No `Content`, no `Part`.
3. The `GeminiChatProvider` adapter lives in `ai-adapter-gemini` and is the only place that imports the Google SDK. It maps Gemini `Content` → `ChatMessage` and back.
4. Add a *trivial second adapter* — even a `EchoChatProvider` that returns the user prompt back verbatim — to prove the port works with two implementations. That's the SOLID demo. Profile-driven: `ai.provider=gemini|echo`.
5. Tool definitions (the function-calling JSON schemas) live in YAML/JSON, not code. Each adapter loads them and translates to its provider's format.

**Warning signs:**
- `grep -rn "import com.google" ai-port/` returns anything.
- `ChatProvider` method signature mentions Gemini-specific types.
- Conversation state (saved to DB) contains JSON that's literally Gemini's `Content` shape.

**Phase to address:** P:AI-Core. Verify in P:Demo-Polish ("can I run with `ai.provider=echo` and the chat bubble still works structurally?").

---

### Pitfall 8: Connection pool exhaustion on the single Postgres host

**Severity:** HIGH

**What goes wrong:**
13 services each with HikariCP defaults (`maximum-pool-size: 10`) = up to 130 connections. PostgreSQL default `max_connections` is 100. The compose Postgres container ships with the upstream default (~100). First load test or even a simultaneous saga step hangs with `HikariPool-1 - Connection is not available, request timed out`.

**Why it happens:**
Each Spring Boot service has its own pool with its own defaults. Nobody multiplies. The single-host DB-per-service decision (PROJECT.md, "DB-per-service on a single Postgres instance") is correct for cost but multiplies pool pressure.

**How to avoid:**
1. Sized pool per service: services that don't need connections (notification with log-only mock) → `maximum-pool-size: 2`. Domain services → `5`. Infrastructure (eureka, config) → `0` (no DB).
2. Compute the budget: `services × pool-size ≤ pg.max_connections × 0.8`. Document the math in `docs/architecture/db-budget.md`.
3. Bump `max_connections` on the compose Postgres via a `command: postgres -c max_connections=200` override (or a `postgresql.conf` mount). Cheap.
4. Use PgBouncer in front of Postgres only if hitting the wall (extra service, probably skip for 6-day window — design the pool sizes correctly instead).

**Warning signs:**
- HikariCP `request timed out` exceptions during integration tests.
- `pg_stat_activity` shows >80 idle connections at idle.
- One service's slow query starves others.

**Phase to address:** P:Foundations (pool defaults set in parent POM / shared `application.yml`).

---

### Pitfall 9: Gemini free-tier rate limit hit by saga callbacks + chat traffic

**Severity:** HIGH (during demo)

**What goes wrong:**
Free tier for Gemini 2.5/3 Flash is **10 RPM** (Flash) or **15 RPM** (Flash-Lite), with a daily cap (~250–500 RPD as of December 2025 reduction). The demo includes the chat assistant (multi-turn → multi-call), the MCP server (more calls), and possibly an embedding pre-warm at startup. Mid-demo: `429 RESOURCE_EXHAUSTED`. Chat bubble dies in front of graders.

**Why it happens:**
Free-tier limits were halved in December 2025 ([blog.laozhang.ai, aifreeapi.com](https://www.aifreeapi.com/en/posts/gemini-api-free-tier-limit)). Function-calling chat is conversational: each user turn often produces 2–4 model calls (initial → tool result → final response, sometimes more). Easy to hit 10 RPM with a single demo flow.

**How to avoid:**
1. Enable a billing-attached project for the demo week. Free tier is for development; the demo runs on a paid project (still cheap — Flash pricing is on the order of cents per demo).
2. Cache embeddings at build time (semantic search index pre-built; runtime never recomputes embeddings for stable seed data).
3. Conversation summarization at 6+ turns → keep context small, fewer tokens, model is cheaper and faster.
4. Add a `RateLimitedChatProvider` decorator (Bucket4j or simple Guava `RateLimiter`) — caps requests per second client-side, surfaces a friendly Turkish error when exceeded.
5. Have a recorded video of the chat flow as a fallback. Worst-case demo plan.
6. On 429: catch, return a non-AI fallback message in Turkish ("Asistan biraz yoğun, lütfen bir dakika sonra tekrar deneyin."), do not crash the saga.

**Warning signs:**
- `429` in `ai-service` logs during dev iteration.
- Embedding pre-warm on startup takes >30s.
- Chat replies start fast, then slow as turn count grows.

**Phase to address:** P:AI-Core (rate-limit decorator + summarization), P:Demo-Polish (billing project, recorded fallback).

---

### Pitfall 10: Hallucinated tool arguments — model invents product IDs

**Severity:** HIGH

**What goes wrong:**
User: "Sepete bir telefon ekle." Model proposes `addToCart(productId="prod-12345")`, but no such ID exists. Tool layer naively executes, hits the database, throws `EntityNotFoundException`. Stack trace propagates back into the chat. Or worse: tool layer creates a fake row.

**Why it happens:**
Function calling returns *proposed* calls, not validated ones. The Gemini docs explicitly say the application is responsible for executing and feeding the result back. Models hallucinate IDs — especially when the system prompt doesn't pin them to a tool-discovered ID.

**How to avoid:**
1. Two-step tool design: every "act" tool requires an ID that came from a "read" tool in the same conversation. e.g., `searchProducts` returns IDs; `addToCart(productId)` validates the ID was returned by a previous tool call in this conversation (track in conversation state).
2. All tool arg validation happens at the dispatcher layer (`ToolDispatcher.invoke(name, argsJson)`), not in the business service. Reject with a model-facing error like `{"error":"productId 'prod-12345' was not in any prior search result"}` so the model can self-correct.
3. System prompt explicitly: "Asla ürün ID'sini uydurma. Önce `searchProducts` ile ara, oradaki ID'yi kullan."
4. Tools that mutate (cart, order, payment) require a confirmation step ("Onaylıyor musun?") before execution. The confirmation is a separate tool call.

**Warning signs:**
- `EntityNotFoundException` from cart/order in the chat path.
- Tool args contain plausible-looking but wrong IDs (`prod-12345`, `user-1`).
- Model's response references products that don't exist in seed data.

**Phase to address:** P:AI-Core (tool dispatcher + validation), validated in P:Demo-Polish.

---

### Pitfall 11: SAGA compensation incomplete — stock released, cart not cleared

**Severity:** HIGH

**What goes wrong:**
Order saga goes: `OrderCreated` → `StockReserved` → `PaymentFailed` → `StockReleased` → ??? Cart still contains the items. User retries, double-checkouts, or sees a stale cart. Or: payment succeeded but notification failed, so the user thinks the order didn't go through.

**Why it happens:**
Choreography SAGA forces every participant to define its compensation. Easy to miss cart-service in the failure path because it's "not a payment thing." Tutorials usually show only the happy path.

**How to avoid:**
1. Build the failure matrix before writing code:

| Step | Failure event | Compensation needed in |
|------|---------------|------------------------|
| Reserve stock | `StockReservationFailed` | order (mark FAILED) |
| Take payment | `PaymentFailed` | inventory (release), order (mark FAILED) |
| Notify | `NotificationFailed` | (log only — non-fatal) |
| Order success | `OrderCompleted` | cart (clear), inventory (commit reservation) |

2. Each compensation event has its own listener in the originating service. Tested with an integration test that *forces* the failure (mock payment to throw).
3. Saga state visualized: a `saga_steps` table per order with `step_name, status, completed_at, compensated_at`. One query shows the full story for any order.
4. Correlation ID propagated in every event header. MDC the request ID in logs across services.

**Warning signs:**
- Cart still has items after a failed checkout in the demo.
- Inventory shows reservations older than 5 minutes for failed orders.
- "Where did this order go?" requires SSH-ing to multiple services.

**Phase to address:** P:Saga.

---

### Pitfall 12: ~~AWS Elastic Beanstalk single-app vs 13 services mismatch~~ (DROPPED 2026-04-28)

**Status:** DROPPED — the deploy target was changed from AWS Elastic Beanstalk + RDS to **local docker-compose on the candidate's machine** (with Cloudflare Tunnel / ngrok exposing the demo URL). The EB-vs-13-microservices fit problem only existed when EB had to host the services; on the candidate's local docker host the 13 Jib images run in a single compose stack as designed, with no platform-fit conflict.

**Original framing kept for audit trail:**

> Beanstalk's natural unit is one app per environment. 13 microservices = 13 environments = 13 EC2 instances at minimum, well over free tier and over what a 6-day budget can sustain. Or: the candidate tries the **Multi-container Docker** platform on Beanstalk — but AWS deprecated the multi-container Docker on AL1 platform and the AL2/AL2023 path is single-container only. Discovery on Day 5 = sunk demo.

**Residual risk after the revision:**
- Local-host-dependency risk: machine reboot mid-demo, machine off when a grader visits. Mitigation: docker-compose `restart: unless-stopped`, README rehearsal of a 30-second `compose up`, and a documented note that the demo URL is "live during the interview window" (not a 24/7 hosted service).
- Tunnel-dependency risk: Cloudflare Tunnel / ngrok outage. Mitigation: keep the alternate tunnel ready in the README so the candidate can swap in under a minute.

**Caveat:** the bootcamp brief originally listed AWS deployment as must-have. Coordinator confirmation that local-host + tunnel deployment is acceptable for grading is recommended; the candidate is responsible for that conversation.

**Phase to address:** N/A (closed). Phase 11 owns tunnel wiring + the `full` compose profile per CONTEXT.md D-15..D-18 revised.

---

### Pitfall 13: Schema migrations across services without coordination

**Severity:** MEDIUM

**What goes wrong:**
Single Postgres host, schema-per-service. Each service has its own Flyway migrations under `db/migration`. Two services start in parallel, both try to create their schema, race condition on `CREATE SCHEMA IF NOT EXISTS`. Or: a service migration assumes another service's schema exists (cross-schema FK — forbidden by the architecture).

**Why it happens:**
Flyway is a single-app tool. With 13 apps on one DB, you have to give each its own `schemas` config. Easy to miss.

**How to avoid:**
1. Each service's `application.yml`: `spring.flyway.schemas: <service-name>` and `spring.flyway.default-schema: <service-name>`. Migrations create tables in the service's schema only.
2. **Forbidden:** any cross-schema reference (FK, view, query). Enforced by code review checklist + a one-line `psql` script in CI that fails if a service's tables reference another service's schema.
3. Schema creation: a single bootstrap migration (`V0__init_schemas.sql`) run by `config-server` or a one-shot init job, creating all 13 schemas + roles. Each service then has its own credentials limited to its schema.
4. Migration ordering inside a service is sequential. Across services, no ordering — each is independent.

**Warning signs:**
- `permission denied for schema X` in service logs at startup.
- Flyway lock contention (`FlywaySqlException: schema_history is locked`).
- A service's repository querying `another_service.products`.

**Phase to address:** P:Foundations (schema bootstrap), P:Domain-Services (Flyway config per service).

---

### Pitfall 14: Gateway path validation lets internal services be reached directly

**Severity:** MEDIUM (security hole) / HIGH if graders test for it

**What goes wrong:**
Domain services bind on `0.0.0.0` (Spring Boot default), and on the demo EC2 their ports are open in the security group. Anyone hitting `:8081/api/products` bypasses the gateway, the JWT check, and the `X-User-Id` injection. The "JWT validated only at the gateway" architecture is fictional.

**Why it happens:**
Easy to forget when iterating locally where everything's reachable. Security groups are a deploy-time afterthought.

**How to avoid:**
1. Domain services bind on `127.0.0.1` in production profile (`server.address: 127.0.0.1`). Only the gateway binds on `0.0.0.0`.
2. EC2 security group: only the gateway's port 80/443 is in the public ingress rule. All other ports are intra-VPC only.
3. Smoke test in CI: from outside the VPC (or simulated), hit a service-direct URL → expect connection refused.
4. Document the trust boundary explicitly in the README architecture diagram. Graders will see it.

**Warning signs:**
- `curl http://ec2-public-ip:8081/api/products` returns data.
- `nmap` from outside shows multiple service ports open.

**Phase to address:** P:DevOps.

---

### Pitfall 15: MCP transport mismatch with the demo target

**Severity:** MEDIUM

**What goes wrong:**
Candidate builds MCP server with **stdio** transport (the bootcamp tutorials and most local examples use stdio). Tries to connect from Claude Desktop running on a different machine, or from a remote tester — stdio doesn't work over network. Or: builds with deprecated **SSE** transport, current clients don't connect.

**Why it happens:**
Per the [MCP spec](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports) and [transport guides](https://mcpcat.io/guides/comparing-stdio-sse-streamablehttp/): stdio works only when the client spawns the server as a subprocess (Claude Desktop on the same machine). For remote clients use **Streamable HTTP** (the current standard). SSE is deprecated.

**How to avoid:**
1. Decide demo target on Day 1: "Claude Desktop on grader's machine" (stdio + a local launch script) vs "remote, accessed by URL" (Streamable HTTP).
2. Default recommendation: **Streamable HTTP**, hosted alongside the local docker-compose deploy and exposed via the same Cloudflare Tunnel / ngrok used for the demo URL. URL is in README. Anyone can connect. Demonstrates "agentic e-commerce *over the network*" which is the differentiation story.
3. Use the official Spring AI MCP server starter (`spring-ai-mcp-server-webmvc-spring-boot-starter` or webflux equivalent — pick to match the gateway choice). It handles the transport.
4. Capability negotiation: declare only the tools you actually expose. The MCP `initialize` response lists tools; clients reject mismatches.

**Warning signs:**
- Demo plan says "remote MCP demo" but the server is stdio.
- Claude Desktop config requires editing `claude_desktop_config.json` on the grader's machine (high friction).
- MCP `tools/list` returns tools the dispatcher doesn't actually implement.

**Phase to address:** P:MCP. Decision made in P:Foundations alongside the deploy-target decision (local docker-compose + tunnel).

---

### Pitfall 16: Tool set duplicated between chat assistant and MCP server

**Severity:** MEDIUM

**What goes wrong:**
The chat assistant in `ai-service` defines tools (`searchProducts`, `addToCart`, `placeOrder`) as Java methods or YAML. The MCP server in `mcp-server` redefines them. They drift. A tool fix lands in one but not the other. The "one toolset, two surfaces" architecture story falls apart.

**Why it happens:**
Two different services, two different developers (or two different AI sessions). Copy-paste is faster than refactoring to share.

**How to avoid:**
1. Tools live in a shared `tool-catalog` module. Each tool: name, JSON schema for args, description, dispatcher binding (a Spring bean reference).
2. Both services depend on `tool-catalog`. The chat assistant uses it to render Gemini function declarations; the MCP server uses it to render MCP `tools/list`.
3. Tool execution itself is a REST call from `mcp-server` and `ai-service` to the appropriate domain service, *not* an in-process invocation. Both surfaces hit the same API. (Note: this is the actual tool dispatcher; the catalog is just the schema.)
4. Single integration test verifies "tools are identical across both surfaces" — load the catalog, render both Gemini and MCP formats, assert tool names+schemas match.

**Warning signs:**
- Tool added in chat but not visible to MCP client.
- Two YAML files with similar tool definitions in different modules.
- Different argument names in the two surfaces (`productId` vs `product_id`).

**Phase to address:** P:AI-Core (catalog defined), P:MCP (consumes catalog).

---

### Pitfall 17: Gemini message format coupling in conversation state

**Severity:** MEDIUM

**What goes wrong:**
Conversation history is persisted in the DB as serialized Gemini `Content` JSON (the SDK's native shape). Swapping providers requires migrating all stored conversations. The SOLID story leaks into the database.

**Why it happens:**
`Content`/`Part` is what the SDK gives you; serializing it directly is the path of least resistance.

**How to avoid:**
1. Conversation table stores neutral DTOs: `(conversation_id, turn_index, role, content_text, tool_calls_json, tool_results_json)`.
2. Adapter converts neutral → provider format on each call. Conversion is one-way (provider → neutral) on response.
3. If conversation pruning/summarization is added, it operates on neutral DTOs. Provider-agnostic.

**Warning signs:**
- `conversations.payload` column contains literal `"role": "user", "parts": [...]` (Gemini shape).
- `EmbeddingProvider` has `Content` in its method signature.

**Phase to address:** P:AI-Core (conversation persistence model).

---

### Pitfall 18: Token expiry / clock skew between identity and gateway

**Severity:** MEDIUM

**What goes wrong:**
Identity service issues JWT with `exp = now + 1h`. Gateway validates with `now`. If their clocks differ by more than the leeway (default 0), tokens "from the future" or "expired" are rejected. In `docker-compose` with WSL2 host clock drift it's common — every container shares the host clock, so drift on the candidate's machine surfaces immediately. Demo intermittent failures: "user logged in but next request 401."

**Why it happens:**
JWT spec uses NumericDate (seconds since epoch). Sub-second precision varies by SDK. Container clocks on dev machines drift.

**How to avoid:**
1. Set `clockSkew` on the JWT verifier (Spring Security: `JwtTimestampValidator` with `Duration.ofSeconds(30)`).
2. Token expiry: 1 hour for the demo. Refresh-token flow not needed for grading.
3. Both services log `now` and the token's `iat`/`exp` at validation. Easy to spot drift.
4. Docker-compose: bind-mount `/etc/localtime` or use `time-sync` patterns; on WSL2 hosts, run `wsl --shutdown` before the demo if drift accumulates after a long laptop sleep.

**Warning signs:**
- Login succeeds, immediate next request 401.
- Token `exp` is exactly the issue time (clock not advancing in container).
- Inconsistent 401s that go away on retry.

**Phase to address:** P:Foundations.

---

### Pitfall 19: Streaming chat backpressure freezes the React UI

**Severity:** MEDIUM

**What goes wrong:**
The chat bubble streams Gemini responses. The browser receives tokens at 200/s. React state updates on every token (`setMessages([...messages, partial])`) → 200 re-renders/sec → main thread stalls → bubble freezes.

**Why it happens:**
Streaming chat is "easy" (EventSource, server-sent events) but the naïve React implementation pumps each token through state. With message length ~500 tokens, that's a measurable freeze.

**How to avoid:**
1. Buffer tokens client-side: append to a ref, flush to state every 50ms via `requestAnimationFrame` or `setTimeout`.
2. Use `useReducer` with a single `appendChunk` action; React batches.
3. Or: stream into a non-React DOM node (pre-rendered `<span>`), only commit to React state when streaming ends.
4. Backpressure on the server: gateway sets a sane chunk size; don't relay every Gemini chunk one-to-one.

**Warning signs:**
- Chat bubble laggy during long replies.
- Browser DevTools "Performance" tab shows 100+ re-renders for one reply.
- Mobile/lower-spec laptop is unusable.

**Phase to address:** P:Frontend.

---

### Pitfall 20: Turkish prompt drift — model replies in English

**Severity:** MEDIUM (demo polish)

**What goes wrong:**
System prompt says "you are a Turkish shopping assistant," but the user types "show me phones" in English (testing) and the model replies in English — and *stays* in English for subsequent Turkish turns because of conversation context contamination. Or: tool descriptions are English, model mirrors that language for confirmations.

**Why it happens:**
Gemini infers language from recent context. A single English turn shifts the conversation.

**How to avoid:**
1. System prompt: `"Tüm cevaplarını Türkçe ver. Kullanıcı başka bir dilde yazsa bile sen Türkçe cevap verirsin."` (Always respond in Turkish, even if the user writes in another language.)
2. Re-inject the system prompt at every turn (or use the `systemInstruction` field if the SDK supports it pinned across turns).
3. Tool descriptions in Turkish: `"Ürün arar"`, `"Sepete ekler"`. Tool names stay English (model handles fine).
4. Smoke test: 5-turn conversation alternating Turkish/English input, assert all model output is Turkish.

**Warning signs:**
- One English reply in dev = drift starting.
- User types English → model replies English → continues English even after Turkish input.

**Phase to address:** P:AI-Core (prompt design + integration test).

---

### Pitfall 21: Slack webhook URL leaked

**Severity:** MEDIUM

**What goes wrong:**
The Slack incoming webhook URL is committed to `.github/workflows/deploy.yml` (or the README, or `application.yml`). Anyone with read access can spam your Slack workspace. In a public repo, this is hours-fast.

**Why it happens:**
Slack webhooks look harmless ("just a URL") and copy-paste is faster than the secrets dance.

**How to avoid:**
1. Webhook URL is a GitHub Actions secret (`SLACK_WEBHOOK_URL`), referenced as `${{ secrets.SLACK_WEBHOOK_URL }}`.
2. Pre-commit hook + `gitleaks` rule for `hooks.slack.com/services/`.
3. If leaked: rotate immediately (Slack admin → re-issue webhook URL).

**Warning signs:**
- `gitleaks detect` flags the workflow.
- Slack shows messages from unknown sources after the repo goes public.

**Phase to address:** P:DevOps.

---

### Pitfall 22: Testcontainers blow the test time budget

**Severity:** MEDIUM

**What goes wrong:**
Each integration test starts its own Postgres + RabbitMQ container. CI runs 13 services in parallel × 2 integration tests each = 26 container startups, each 10–20 seconds. CI takes 20+ minutes. Iteration suffers.

**Why it happens:**
Default Testcontainers usage is "start a fresh container per test class." Without the singleton/reuse pattern, costs add up.

**How to avoid:**
1. Use Testcontainers' [singleton container pattern](https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/) — one Postgres + one RabbitMQ per JVM, shared across tests. JVM dies, containers stop.
2. Per-service: `testcontainers.reuse.enable=true` in `~/.testcontainers.properties` for local dev. CI uses fresh containers.
3. Schema-per-test instead of container-per-test: each test creates a unique schema, drops at end.
4. For unit-level "integration" (controller + service + repo, mocked downstream): use `@DataJpaTest` with H2 instead of Testcontainers. Reserve Testcontainers for the 1–2 critical-path tests per service.

**Warning signs:**
- CI run >15 minutes.
- `mvn test` on a single service >2 minutes.
- Developers stop running tests locally.

**Phase to address:** P:Domain-Services (Testcontainers pattern set in shared parent), P:DevOps.

---

### Pitfall 23: CORS / cookie / Authorization mismatch breaks frontend → gateway

**Severity:** MEDIUM (one-day delay)

**What goes wrong:**
React dev server on `localhost:5173`, gateway on `localhost:8080`. CORS preflight fails: `Access-Control-Allow-Origin` not set, or `Authorization` header not in `Access-Control-Allow-Headers`. Or: production frontend on `app.example.com`, gateway on `api.example.com`, cookies don't cross subdomains. Login appears to work but every authenticated request 401s.

**Why it happens:**
CORS + JWT-in-Authorization-header + dev-prod URL difference is a known minefield. Spring Cloud Gateway's CORS config is reactive-style and looks unfamiliar.

**How to avoid:**
1. Gateway global CORS config in `application.yml`: explicit `allowed-origins` list (no wildcards once `allow-credentials: true`), `allowed-headers: '*'` includes `Authorization`, `allowed-methods: [GET, POST, PUT, DELETE, OPTIONS]`.
2. JWT in `Authorization: Bearer <token>` header, **not** in cookies. Avoids cookie/SameSite/cross-subdomain pain.
3. Frontend uses `VITE_API_BASE_URL` from env, defaults to `http://localhost:8080`. Production points to the gateway URL. No hardcoded URLs.
4. Smoke test: from frontend container, `curl -H "Origin: http://localhost:5173" -X OPTIONS gateway/api/products` → must return 200 with full CORS headers.

**Warning signs:**
- Browser console: `Access to fetch at ... blocked by CORS policy`.
- Login works once, every subsequent request 401s.
- Tokens visible in DevTools but not sent on requests.

**Phase to address:** P:Foundations (gateway CORS), P:Frontend.

---

### Pitfall 24: Pagination off-by-one (page index 0 vs 1)

**Severity:** LOW (but always burns 30 minutes)

**What goes wrong:**
Spring Data `Pageable` is **0-indexed** (`page=0` is the first page). UX patterns and most frontend libraries (and humans typing into URL bars) assume 1-indexed. UI displays "Page 1 of 5" but the URL is `?page=0`. Inconsistency → bug reports → "the last page is empty."

**Why it happens:**
Spring Data and JS pagination libraries disagree by default.

**How to avoid:**
1. Pick an indexing convention at the API boundary. Recommendation: API exposes 1-indexed `?page=1`, controller subtracts 1 before passing to `Pageable.of(page-1, size)`. Document in OpenAPI.
2. OpenAPI schema documents the convention, with example values.
3. Integration test: `?page=1` returns first item; `?page=0` returns 400 Bad Request (out-of-range).

**Warning signs:**
- Frontend pagination off by one row.
- Last page empty when count is exact multiple of page size.

**Phase to address:** P:Domain-Services (product-service controller), P:Frontend.

---

### Pitfall 25: ~~GitHub Actions OIDC role trust policy misconfigured~~ (DROPPED 2026-04-28)

**Status:** DROPPED — there is no AWS account in the deploy story. The GitHub Actions release-tag job pushes Jib images to GHCR (or Docker Hub) using a registry token (`GITHUB_TOKEN` for GHCR, or a `DOCKERHUB_TOKEN` repo secret), not an AWS OIDC role. The OIDC trust-policy class of misconfiguration cannot apply.

**Replacement risk to keep in mind (LOW):** registry-token misconfiguration on the release-tag job. Mitigation: GHCR via `GITHUB_TOKEN` works out of the box for the same repo's namespace; if Docker Hub is used, the `DOCKERHUB_USERNAME` + `DOCKERHUB_TOKEN` repo secrets must be set before the first release tag. A `docker login` step at the top of the workflow fails fast if the secrets are missing.

**Phase to address:** P:DevOps (Phase 11) — register-and-publish step only.

---

### Pitfall 26: Day-1 bikeshedding instead of locking saga event contracts

**Severity:** HIGH (silent — kills the timeline before you notice)

**What goes wrong:**
Day 1 is spent on toolchain debates: Vite vs Next, Tailwind vs CSS-in-JS, JS vs TS. By Day 2 evening the saga events still aren't defined. Day 3 starts implementing services, every service invents its own event names, and Day 4 is spent reconciling. Demo on Day 6 has incomplete saga.

**Why it happens:**
Bikeshedding is comfortable; defining the actual hard problems isn't. Frontend toolchain is intentionally deferred in PROJECT.md ("Frontend toolchain deferred until n11 recon") — this is correct, *but* it can become an excuse to defer everything.

**How to avoid:**
1. Day 1 deliverable, before any code: a single markdown file `.planning/saga-contracts.md` listing every event name, payload schema, producer, consumers, compensation event. Locked.
2. Day 1 also produces `.planning/api-contracts.md` for the gateway: every external endpoint, request/response, JWT requirement.
3. Frontend toolchain decision **after** Playwright recon (PROJECT.md says so) — do the recon on Day 1 morning, decide before lunch, move on.
4. Track this with the PROJECT.md "Open Questions" — every open question gets a deadline.
5. Time-box every architecture debate to 30 minutes. Decision logged in PROJECT.md "Key Decisions" with rationale.

**Warning signs:**
- End of Day 1 and saga events not written down.
- Two services use different field names for the same concept (`orderId` vs `order_id` vs `id`).
- Open questions in PROJECT.md still all "pending" on Day 3.

**Phase to address:** P:Foundations (Day 1 deliverable).

---

### Pitfall 27: "Just one more AI feature" instead of polishing the chat assistant

**Severity:** HIGH (silent — corrodes the differentiator)

**What goes wrong:**
After the chat assistant works for the happy path, the candidate adds: voice input, image upload, recommendation widget, NL-search bar, multi-language, etc. Each is 60% done. Nothing is demo-polished. Graders see a half-working chat assistant and a kitchen sink of unfinished features.

**Why it happens:**
AI features are dopamine-inducing to build. PROJECT.md explicitly flags this ("don't dilute AI investment across many shallow features; go deep on the chat assistant"), but the temptation persists.

**How to avoid:**
1. Treat the Out-of-Scope list in PROJECT.md as **adversarial** — every "but it would be cool to add..." gets matched against the list before commitment.
2. The chat assistant has a "demo script": exactly the 5 user inputs and 5 expected behaviors that will be shown. Polish those 5 to perfection. Anything else is post-demo work.
3. After the chat assistant is *demo-ready*, the next priority is README + polish, not new AI features.
4. Daily standup with self: "What did I cut today?" If nothing, you're over-scoping.

**Warning signs:**
- Day 5: a new branch named `feature/voice-input` exists.
- The "5 demo flows" each have edge-case bugs that are unfixed.
- The README is shorter than `feature/...` branch list.

**Phase to address:** P:Demo-Polish (with vigilance throughout).

---

### Pitfall 28: Tests left to Day 6 instead of running on Day 2

**Severity:** HIGH

**What goes wrong:**
"I'll write tests at the end" → Day 6 morning, tests don't compile because the code has shifted. Or tests pass individually but fail in CI because of order dependencies. Or: the integration test infrastructure (Testcontainers, Wiremock for Iyzico) was never set up, and setting it up takes 4 hours that Day 6 doesn't have.

**Why it happens:**
Tests feel like overhead until they're not. Bootcamp brief explicitly requires them, and the SOLID grading lens cares about testability.

**How to avoid:**
1. Test infrastructure is Day 1 work: parent POM has Testcontainers + AssertJ + Mockito set up, with one passing example test.
2. Each service's first commit includes a passing test (even just a controller smoke test). "If it doesn't have a test, it's not done."
3. CI runs on every PR from Day 1. CI red = code red. No merging on red.
4. The 1–2 integration tests per service per critical path (per PROJECT.md scope) are written **in the same PR as the feature**, not later.
5. Day 5 is for fixing flaky tests, not writing new ones.

**Warning signs:**
- CI shows test count = 0 for a service after its features are merged.
- "I'll add tests once it works" said out loud.
- Day 4: zero CI runs in the past 24 hours.

**Phase to address:** All implementation phases. Set up in P:Foundations.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Hardcoded service URLs in dev (`http://localhost:8081`) instead of Eureka discovery | Skip Eureka client setup | Architecture story collapses; graders read code | **Never** for non-bootstrap services. Eureka is mandated. |
| JWT secret in `application.yml` (no config-server) | Save 1 hour | Pitfall 6 (security) | Only if `config-server` is broken at demo hour; then env-var fallback, never commit |
| Skip dead-letter queues on RabbitMQ | Save 30 min per queue | Pitfall 3; demos break on first redelivery | Never — DLQ is one-line topology config |
| Test against H2 instead of Postgres | Tests run faster | pgvector + Postgres-specific SQL won't work | OK for unit tests on pure logic. Repo + integration tests must use real Postgres |
| Hand-write Dockerfiles instead of Jib | Familiar | Brief mandates Jib | Never — brief is explicit |
| Skip the second `EchoChatProvider` adapter | Save 1 hour | The SOLID demo loses its punch | Never if you're claiming SOLID. This is the demo. |
| Conversation summarization deferred | Day 1 chat works | Day 5 chat hits token limit on long demo | Acceptable up to ~10 turns; mandatory for >10-turn demos |
| Single Postgres host (DB-per-schema, not DB-per-instance) | Cost / time | Slight architecture-story compromise | **Acceptable** — PROJECT.md locks this; document it as a deliberate tradeoff |
| Mock email in notification-service | Brief allows it | Loses real-email demo | **Acceptable** — PROJECT.md locks this |
| Skip refresh tokens | Save half a day | Demo tokens expire in the middle of a long session | Acceptable with 1-hour expiry + clear "re-login" UX |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Iyzico SDK | Synchronous-only methods called from a reactive controller in `payment-service` | If gateway is reactive but `payment-service` is MVC, Iyzico's blocking calls are fine. If `payment-service` is also reactive, wrap in `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` |
| Iyzico SDK | Mixing sandbox base URL `sandbox-api.iyzipay.com` with prod keys, or vice versa | Single source of truth: `iyzico.base-url` + `iyzico.api-key` + `iyzico.secret-key` always loaded together from config-server |
| Iyzico 3DS | Forgetting `locale: tr` and `currency: TRY`; sending `EUR` for a Turkish demo | Hard-code in the request builder; integration test asserts response `locale: "tr"` and `currency: "TRY"` |
| Iyzico Checkout Form vs Direct | Picking Direct 3DS for "more control" then dealing with HTML form rendering | For sandbox demo: **Checkout Form** (Iyzico hosts the form, you handle one callback). Direct 3DS only if you need card-on-file or stricter UX |
| Gemini function calling | Treating model's `functionCall` as an instruction to execute; missing the round-trip | App receives `functionCall` → app executes the tool → app sends `functionResponse` back to model → model replies to user. Three legs, not one |
| Gemini streaming | Mixing `streamGenerateContent` with `generateContent` in the same conversation | Pick one mode per conversation. Streaming for chat replies; non-streaming for tool-call legs |
| Gemini embeddings | Using a different embedding model for indexing vs query → vector spaces don't match | Lock the embedding model name in config; same model for both. Document in `application.yml` |
| RabbitMQ topology | Declaring queues/exchanges in code via `@Bean` in **every** consumer service → race on first start | Single `topology-init` job (or one-time admin script) creates exchanges/queues/bindings. Services only declare consumers |
| RabbitMQ deserialization | `Jackson2JsonMessageConverter` reading into a class that doesn't exist anymore (renamed) → consumer poison-loops | Use `__TypeId__` mapping with explicit class-id-to-name registry; if class missing, route to DLQ not retry |
| Spring Cloud Config | Config-server unreachable on boot → service crashes | `spring.cloud.config.fail-fast: false` for non-critical services; `retry` enabled with backoff |
| Eureka + Gateway | Gateway uses `lb://service-name` but service-name has uppercase letters | Eureka lower-cases service names; route IDs must match. Audit: `eureka.instance.appname` lowercase, gateway routes lowercase |
| Springdoc OpenAPI on gateway | Aggregate OpenAPI on gateway shows 0 endpoints because services use webmvc but gateway uses webflux Springdoc | Each service publishes its `/v3/api-docs`; gateway aggregates via `springdoc.swagger-ui.urls` list (configured manually) |
| MCP server + Spring AI | Tool registration fails silently when method signatures don't match expected types | Use `@Tool` annotation with explicit description; integration test calls `tools/list` and asserts each tool present |
| Local Postgres in docker-compose | Connecting from outside the candidate's host → blocked by default (compose binds 5432 to localhost) | Keep Postgres bound to localhost only — the public tunnel exposes only the gateway port. Document this in the README as the deliberate boundary. |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| N+1 service calls per user request (gateway → product → inventory → category → ...) | Slow page loads (>500ms) | API composition: gateway aggregates one call per page. Each domain service exposes batch endpoints (`POST /products/by-ids`) | At ~50 concurrent users; demo will *look* slow even single-user |
| HikariCP default pool of 10 × 13 services > Postgres `max_connections` | "Connection is not available" timeouts under any concurrent load | Pitfall 8 — sized pools, document the budget | First load test, or 2-3 concurrent saga executions |
| Eureka heartbeat default 30s → instance churn after restart appears as 503 | First 30-90s of demo show stale 503s | Pitfall 4 — fast registry/heartbeat in dev profile | Every restart |
| Embedding pre-warm at boot fetches 1000 product embeddings serially | Service takes 3+ minutes to start; rate-limit 429 | Embeddings cached at *build* time as a JSON file shipped with the jar; runtime loads into memory | Always (cold start every time) |
| Conversation context unbounded → 50-turn chat = 100k tokens per call | Chat slows down; rate-limit hit; cost explodes | Summarize after 10 turns, keep last 5 verbatim + summary | At ~10 turns |
| Frontend product list re-fetches on every state change | UI flicker, API spam | React Query (`@tanstack/react-query`) with stale-while-revalidate | Always |
| pgvector index missing on embeddings table | Vector search 5s+ on 1000 products | `CREATE INDEX ON products USING ivfflat (embedding vector_cosine_ops)` in Flyway migration | At ~500 products |
| Logging at INFO with full payload bodies | Log volume explodes; CloudWatch costs; hard to grep | Structured JSON logs; payloads at DEBUG; sample at 1% in production | At ~10 RPS |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Iyzico secret key in source | Account compromise; sandbox abuse | Config-server + env vars; `gitleaks` in pre-commit |
| Gemini API key in source | Quota theft; billing surprise | Same as above; key in env, never in code |
| JWT secret committed | Token forgery — anyone can impersonate any user | Pitfall 6 — secret only in config-server / env |
| Internal services bound to 0.0.0.0 with open security groups | Architecture bypass; auth skipped | Pitfall 14 — bind to 127.0.0.1; security group locks ports |
| `X-User-Id` header trusted from any source | Anyone bypassing the gateway can claim any user-id | Gateway *replaces* incoming `X-User-Id` (don't pass through); domain services validate the request came from the gateway via mTLS or shared secret header |
| 3DS callback endpoint behind JWT filter | Iyzico can't reach it; orders hang | Pitfall 5 — whitelist callback path; verify via signed `payment.retrieve` |
| MCP server with no auth | Anyone on the internet places orders against your demo | API-key header on MCP, distinct from JWT. Document in README. |
| Unprotected Actuator endpoints (`/actuator/env`) | Leaks secrets in env vars to anyone hitting the URL | Spring Security on Actuator; expose only `/health` and `/info` publicly; `/env`, `/configprops` require admin role |
| Postgres password rotation never | If a `.env` leaks, no rotation path | Document rotation in README ("for production: rotate via Vault / Doppler"); demo can keep static — Postgres is on the candidate's host, not internet-exposed |
| Tool dispatcher trusts model-supplied IDs | Pitfall 10 — model creates phantom orders | Validate every ID against domain rules; never just "execute what the model says" |
| Container-registry credentials in repo secrets (long-lived) | If repo public, credentials leaked | Prefer GHCR + `GITHUB_TOKEN` (no extra credential to rotate); if Docker Hub, use a scoped `DOCKERHUB_TOKEN` repo secret with read+write to the project's namespace only |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Turkish UI flashes English on first paint | Looks unprofessional; suggests i18n bolted on | Bundle Turkish strings inline in the initial HTML; no async i18n fetch on critical path |
| Chat bubble closes when user navigates between pages | "Conversation lost" — destroys agentic-commerce demo | Persistent floating bubble (PROJECT.md: "persistent across pages"). Conversation state in `ai-service`, not browser-local |
| Loading states absent → blank cart, blank product list during fetch | Looks broken | Skeleton loaders or spinners; React Query handles it |
| Error messages in English ("Network error") on a Turkish UI | Inconsistent | Map all error messages to Turkish at the API client layer |
| 3DS test card flow doesn't show clear "use these test card numbers" hint | Grader can't complete the demo flow | Demo banner on checkout page: "Test kartı: 5528 7900 0000 0008 / 12/30 / 123 / OTP: 283356" (Iyzico's standard sandbox card) |
| Adding to cart from chat assistant doesn't update the cart icon counter | Assistant feels disconnected | Cart-service publishes `CartUpdated` event over WebSocket / SSE; React listens; counter updates immediately |
| "Onaylıyor musun?" confirmation in chat but no parallel UI confirmation | Feels like the model is acting without consent | Cart updates from chat highlight in the cart UI for 3 seconds (visual feedback) |
| Pagination has no "go to last page" button | Annoying for testing | First/Prev/Next/Last buttons + page-N input |

---

## "Looks Done But Isn't" Checklist

- [ ] **Microservices count**: README says 13, but eureka and config-server count as infra, not domain services — verify the *domain-service* count (post Pitfall 26 work) is ≥10
- [ ] **JWT validation**: gateway has the filter, but is it actually applied? Verify by hitting a protected endpoint without a token → expect 401
- [ ] **SAGA compensation**: happy path works; force a payment failure (mock Iyzico to throw) and verify cart NOT cleared, inventory released, order marked FAILED
- [ ] **Iyzico 3DS callback**: completes the order (status PAID + `OrderCompleted` event published)
- [ ] **Idempotency**: send the same `OrderCreated` event twice (RabbitMQ admin → publish) — order count stays at 1
- [ ] **DLQ**: poison message lands in DLQ, doesn't loop. Verify with RabbitMQ admin.
- [ ] **Tests**: every service has at least 1 passing unit and 1 passing integration test in CI
- [ ] **Swagger**: gateway aggregates all 11 service docs, all reachable
- [ ] **Pagination**: API returns `totalElements`, `totalPages`, `number`, `size` consistently
- [ ] **CI/CD**: pipeline runs on push, builds, tests, deploys, posts to Slack
- [ ] **Provider-agnostic AI**: `ai.provider=echo` works (chat bubble shows echo response, doesn't crash)
- [ ] **MCP tools/list**: returns the same tool names as the chat assistant uses
- [ ] **MCP toolset reachable from a real MCP client**: tested with `npx @modelcontextprotocol/inspector` or Claude Desktop
- [ ] **Turkish UI**: every visible string is Turkish; no hardcoded English in JSX
- [ ] **Demo seed data**: 20+ Turkish products, real-looking names (e.g., "Apple iPhone 15 Pro 256 GB Doğal Titanyum"), test-card hint visible on checkout
- [ ] **Local-host deploy**: `docker compose --profile full up` brings up all 13 services + Postgres + RabbitMQ on the candidate's machine; the public tunnel hostname is reachable from outside (`curl https://<tunnel>/api/v1/products` returns 200 from a phone hotspot, not just the candidate's LAN)
- [ ] **Slack notifications**: a real message lands on deploy success/failure
- [ ] **README**: includes architecture diagram, service list, demo script, env vars list, license
- [ ] **Logging**: every service writes structured logs with `traceId`/`correlationId`; demo can `grep correlationId=X` across all services and see the full saga
- [ ] **Health checks**: every service has `/actuator/health/readiness` returning real status (not just `UP` lying)
- [ ] **No secrets in git**: `gitleaks` clean on the final main branch
- [ ] **OpenAPI**: every endpoint has a description, example request, example response
- [ ] **Frontend env**: `VITE_API_BASE_URL` works; production build with prod URL works locally

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Pitfall 1 (Gemini model name wrong) | LOW | Update single config property; redeploy. <30 min |
| Pitfall 2 (Gateway reactive/MVC mix) | MEDIUM | Strip `spring-boot-starter-web` from gateway; rebuild deps; re-test boot. 1–2 hours |
| Pitfall 3 (Non-idempotent consumers) | HIGH | Add `processed_messages` table; wrap every consumer; tests; re-test saga. Half a day |
| Pitfall 5 (Iyzico 3DS callback missing) | MEDIUM | Add controller; whitelist gateway path; test with sandbox flow. 2–3 hours. **In demo:** switch to non-3DS sandbox flow as live workaround |
| Pitfall 6 (JWT secret leaked) | HIGH | Rotate secret; force re-login of all sessions; rewrite git history if pre-public; gitleaks on every commit going forward. 2 hours + vigilance |
| Pitfall 7 (Leaky AI port) | HIGH | Refactor port DTOs; add second adapter; re-test. Half a day. **Cannot fix at demo time.** |
| Pitfall 9 (Gemini rate limit hit during demo) | LOW (with prep) / HIGH (without) | Prep: paid project + recorded fallback video. Without prep: cancel demo, reschedule |
| Pitfall 11 (Compensation incomplete) | MEDIUM | Add missing handlers; integration test forced-failure paths. 2–4 hours |
| Pitfall 12 (DROPPED) | — | No longer in scope; deploy = local docker-compose on candidate's host. Recovery N/A |
| Pitfall 14 (Internal services exposed) | LOW (config) | Update security group; add `server.address: 127.0.0.1`. 30 min |
| Pitfall 19 (Streaming UI freeze) | MEDIUM | Add token buffering. 1 hour |
| Pitfall 20 (Turkish drift) | LOW | Strengthen system prompt; pin `systemInstruction`. 30 min |
| Pitfall 21 (Slack webhook leaked) | LOW | Rotate webhook in Slack admin. 5 min. (Cleanup git history if needed.) |
| Pitfall 22 (Slow tests) | MEDIUM | Singleton container pattern. Half a day |
| Pitfall 26 (Day-1 bikeshedding) | UNRECOVERABLE if discovered Day 4+ | Only prevention: lock contracts Day 1. Recovery: cut scope drastically (drop one of search-service / mcp-server) |
| Pitfall 27 (Scope creep) | HIGH | Cut all unfinished branches; deploy what works; polish only. Day 5 latest |
| Pitfall 28 (Tests left to Day 6) | HIGH | If Day 6 morning: cut to smoke tests only on critical paths; document the trade-off in README. Better than nothing |

---

## Pitfall-to-Phase Mapping

| Pitfall | Severity | Prevention Phase | Verification |
|---------|----------|------------------|--------------|
| 1. Gemini model name verification | HIGH | P:AI-Core | First chat call returns 200, model name in startup log |
| 2. Gateway reactive/MVC collision | HIGH | P:Foundations | Maven Enforcer rule passes; gateway boots cleanly |
| 3. Non-idempotent consumers | HIGH | P:Saga | Duplicate-event integration test asserts single side-effect |
| 4. Eureka registration race | HIGH | P:Foundations | Gateway shows all services within 60s of cold boot |
| 5. Iyzico 3DS callback missing | HIGH | P:Payment | Sandbox test flow completes; `OrderCompleted` event observed |
| 6. JWT secret leaked | HIGH | P:Foundations | Gitleaks clean; secret only in config-server / env |
| 7. Leaky `ChatProvider` abstraction | HIGH | P:AI-Core | `ai.provider=echo` works end-to-end |
| 8. Connection pool exhaustion | HIGH | P:Foundations | Pool budget documented; load test under saga doesn't time out |
| 9. Gemini rate limit | HIGH | P:AI-Core + P:Demo-Polish | Demo runs on paid project; recorded fallback exists |
| 10. Hallucinated tool args | HIGH | P:AI-Core | Tool dispatcher rejects unknown IDs in test |
| 11. SAGA compensation incomplete | HIGH | P:Saga | Forced-failure tests for each saga step |
| 12. ~~Beanstalk fit~~ DROPPED | — | — (closed by deploy revision) | Local docker-compose deploy on candidate's host; Pitfall #12 no longer in scope |
| 13. Schema migration coordination | MEDIUM | P:Foundations + P:Domain-Services | Schema bootstrap migration; cross-schema query test fails |
| 14. Internal services exposed | MEDIUM/HIGH | P:DevOps | External `curl` to service port refused |
| 15. MCP transport mismatch | MEDIUM | P:MCP | Demo target test: real client connects, lists tools |
| 16. Toolset duplicated chat/MCP | MEDIUM | P:AI-Core + P:MCP | Single integration test asserts identical tool catalogs |
| 17. Conversation state coupled to Gemini format | MEDIUM | P:AI-Core | Persisted conversation has neutral DTO shape |
| 18. JWT clock skew | MEDIUM | P:Foundations | `clockSkew` configured; clock drift test |
| 19. Streaming chat UI freeze | MEDIUM | P:Frontend | Long-reply demo doesn't lag visibly |
| 20. Turkish prompt drift | MEDIUM | P:AI-Core | 5-turn mixed-language test passes |
| 21. Slack webhook leaked | MEDIUM | P:DevOps | Gitleaks clean; webhook in secrets only |
| 22. Testcontainers slow | MEDIUM | P:Foundations + P:DevOps | CI under 15 min |
| 23. CORS/auth mismatch | MEDIUM | P:Foundations + P:Frontend | Frontend → gateway smoke test on every CI |
| 24. Pagination off-by-one | LOW | P:Domain-Services + P:Frontend | OpenAPI documents convention; integration test |
| 25. ~~GitHub Actions OIDC misconfig~~ DROPPED | — | — (no AWS account) | OIDC not in scope; release-tag job uses GHCR / Docker Hub registry token instead |
| 26. Day-1 bikeshedding | HIGH | P:Foundations | `.planning/saga-contracts.md` and `.planning/api-contracts.md` exist by EOD Day 1 |
| 27. Scope creep | HIGH | P:Demo-Polish (vigilance throughout) | Branch list checked daily; nothing started after Day 4 |
| 28. Tests deferred to Day 6 | HIGH | All implementation phases | CI runs on every commit from Day 1; test count > 0 per service |

---

## Sources

Verified during research (2026-04-28):

- [Gemini API Models — Google AI for Developers](https://ai.google.dev/gemini-api/docs/models) — Gemini 3 Flash identifier verification (`gemini-3-flash-preview`)
- [Gemini API Rate Limits](https://ai.google.dev/gemini-api/docs/rate-limits) — Free-tier RPM/RPD figures
- [aifreeapi.com — Gemini Free Tier 2026 Guide](https://www.aifreeapi.com/en/posts/gemini-api-free-tier-limit) — December 2025 reduction confirmed
- [Spring Cloud Gateway issue #1004](https://github.com/spring-cloud/spring-cloud-gateway/issues/1004) — webmvc/webflux incompatibility
- [Spring Cloud Gateway Troubleshooting](https://docs.spring.io/spring-cloud-gateway/reference/4.2/spring-cloud-gateway/troubleshooting.html) — `ServerCodecConfigurer` error
- [RabbitMQ Reliability Guide](https://www.rabbitmq.com/docs/reliability) — at-least-once delivery; idempotency mandate
- [RabbitMQ Dead Letter Exchanges](https://www.rabbitmq.com/docs/dlx) — DLX configuration
- [Iyzico 3DS Implementation](https://docs.iyzico.com/en/payment-methods/direct-charge/3ds/3ds-implementation) — callback flow, locale, currency
- [Iyzico Tokenization / Pay With iyzico](https://docs.iyzico.com/en/payment-methods/tokenization/tokenization-integration/pay-with-iyzico) — Checkout Form alternative
- [MCP Specification — Transports (2025-06-18)](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports) — Streamable HTTP is current; SSE deprecated
- [MCPcat Transport Comparison](https://mcpcat.io/guides/comparing-stdio-sse-streamablehttp/) — practical decision guide
- [Netflix/eureka issue #1150](https://github.com/Netflix/eureka/issues/1150) — registration timing details
- [Netflix/eureka issue #1256](https://github.com/Netflix/eureka/issues/1256) — delayed registration patterns
- ~~[AWS Elastic Beanstalk: ECS Multi-container Tutorial](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/create_deploy_docker_ecstutorial.html)~~ — kept as audit trail; no longer applicable after the local-deploy revision
- ~~[AWS re:Post — Beanstalk for microservices](https://repost.aws/questions/QUL4JIR5OcTKWaHrFiEwwMGA/elastic-beanstalk-to-deploy-a-microservices-based-application-with-multiple-components)~~ — kept as audit trail; no longer applicable
- [Cloudflare Tunnel — Get started with `cloudflared`](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/get-started/) — primary tunnel exposure path for the local-deploy demo URL + Iyzico webhook
- PROJECT.md (locked decisions) and REQUIREMENTS-n11.md (bootcamp brief) — project constraints

LOW confidence on:
- Specific Iyzico SDK Java synchronous/reactive interaction patterns (verified via docs that callback flow is HTTP-form-based; the SDK's threading model details should be re-verified before P:Payment).
- Bootcamp-grader-specific reaction patterns (no public post-mortems for this exact bootcamp).

---
*Pitfalls research for: Spring Boot microservices + SAGA + Iyzico + agentic-commerce (n11 bootcamp final case)*
*Researched: 2026-04-28*
