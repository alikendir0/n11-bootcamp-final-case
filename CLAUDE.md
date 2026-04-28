# CLAUDE.md

> Project guidance for Claude Code. Loaded automatically every session.
> Keep this file tight — context is paid for in tokens.

## What this is

n11-style Turkish e-commerce clone built as the **Patika.dev × n11 Spring Boot Bootcamp final case**. Graded — gates an interview at n11. Scope is ambitious: 13 microservices + agentic-commerce differentiator (MCP server + Gemini-powered Turkish chat assistant). 6-day timeline, AI-assisted execution.

**Grading lens:** code quality + SOLID weighted heaviest. Every architectural decision should serve that signal.

## Source of truth

The `.planning/` tree is the authoritative project context. Read these in order if you're picking up a session cold:

1. `.planning/PROJECT.md` — locked decisions, constraints, open questions
2. `.planning/ROADMAP.md` — 11 phases, dependencies, success criteria
3. `.planning/STATE.md` — current phase / what's next
4. `.planning/REQUIREMENTS.md` — 105 v1 requirement IDs (AUTH/PROD/CART/ORD/PAY/NOTIF/AI/FE/LOC/ARCH/QUAL/DEV) with traceability
5. `.planning/research/SUMMARY.md` — research synthesis (versions, architecture, top pitfalls)
6. `REQUIREMENTS-n11.md` (project root) — original bootcamp brief, kept for reference

If a file in `.planning/` conflicts with this CLAUDE.md, **the `.planning/` file wins** — this file is a navigator, not the spec.

## Architecture cheat sheet

**13 services:** `eureka-server`, `config-server`, `api-gateway`, `identity-service`, `product-service`, `inventory-service`, `cart-service`, `order-service`, `payment-service`, `notification-service`, `search-service`, `ai-service`, `mcp-server`.

**Stack:** Java 21 + Spring Boot 3.5.14 + Spring Cloud 2025.0.x (Northfields) · Gradle multi-module · PostgreSQL 16 + pgvector 0.8.2 (schema-per-service, single host, distinct DB user per schema) · RabbitMQ 4.x · choreography SAGA via RabbitMQ events · JWT validated only at the gateway · Springdoc 2.8.17 · Jib Gradle 3.5.3 · GitHub Actions · React 19 + Vite 8 (TS/styling/state locked post-Playwright recon).

**AI library split (don't conflate):**
- `google-genai 1.51.0` → chat + embeddings (inside ai-service `GeminiChatAdapter` and `GeminiEmbeddingAdapter`)
- `spring-ai-starter-mcp-server-webmvc 1.1.5` → MCP wire protocol only (mcp-server)
- Two libraries, two responsibilities. Spring AI is **not** used for Gemini chat (it doesn't list `gemini-3-flash-preview`).

**Gemini model identifier (verify at impl time):** `gemini-3-flash-preview`. Fallback: `gemini-2.5-flash`. Always re-verify against `https://ai.google.dev/gemini-api/docs/models` before writing adapter code.

**Order saga (choreography):** `OrderCreated` → inventory reserves → `StockReserved` → payment-service calls Iyzico → `PaymentCompleted` → order CONFIRMED → `OrderConfirmed` → notification logs. Compensations: `StockReserveFailed`, `PaymentFailed`, `UserCancelled`, `PaymentTimeout`.

## Non-negotiable rules

1. **Provider-agnostic LLM abstraction is the SOLID demo.** `ChatProvider` and `EmbeddingProvider` ports live in a zero-dep `ai-port` Gradle module with **neutral DTOs only** — no Gemini SDK types (`Content`, `Part`, `FunctionCall`) leak into the port. A trivial `EchoChatProvider` second adapter ships in tests to prove substitutability. **If the abstraction leaks, the entire grading thesis collapses.** Cannot be fixed at demo time.

2. **One agent toolset, two consumers.** The `agent-toolset` shared Gradle module defines the 10 tools (`search_products`, `get_product`, `list_categories`, `add_to_cart`, `view_cart`, `update_cart_item`, `remove_from_cart`, `create_order`, `get_payment_link`, `get_order_status`). Both `ai-service` (chat assistant) and `mcp-server` import this module. Never duplicate tool definitions.

3. **Saga consumers must be idempotent.** Every consumer uses a `processed_events` inbox table keyed by event ID. RabbitMQ delivers at-least-once — duplicates will arrive. Test for redelivery.

4. **Verify external SDK docs before writing code.** Project policy: never invent SDK calls from training-data recall. Use Context7 / WebFetch against `ai.google.dev`, `github.com/iyzico/iyzipay-java`, `spring.io`, `modelcontextprotocol.io`. The Iyzico Checkout Form sample lives in `src/test/java/com/iyzipay/sample/CheckoutFormSample.java` of the SDK — read it before writing payment-service.

5. **No secrets in source.** Iyzico keys, Gemini API key, JWT signing key, Slack webhook all via env vars / Spring Cloud Config. `.gitignore` covers them.

6. **Frontend in Turkish; identifiers in English.** UI copy ("Sepete Ekle", "Stokta", "Tükendi", etc.) is Turkish per `LOC-*` requirements. Code identifiers, log messages, commit messages stay English.

## Open questions (block specific phases)

| Question | Affects | Resolved by |
|---|---|---|
| AWS deploy scope (EB+RDS hard or "any AWS"?) | Phase 1 decision, Phase 11 execution | Day-1 query to bootcamp coordinator |
| MCP transport choice (stdio / HTTP+SSE / both?) | Phase 9 (MCP Server) | Phase 9 planning |
| Frontend toolchain (Vite+TS+Tailwind+Zustand or alt) | Phase 10 (Frontend Storefront) | Phase 2 (Playwright recon) outcome |
| Conversation state store (Redis vs Postgres) | Phase 8 (AI port) | Phase 8 planning |
| Kapıda Ödeme (no-op path or disabled radio) | Phase 5 (Cart & Order) | Phase 5 planning |

When you encounter one of these, read the linked `.planning/` artifact and either resolve from existing decisions or surface as a question — don't guess.

## GSD workflow

This project uses [Get Shit Done](https://github.com/get-shit-done) (`.planning/` based, GSD v1).

Routine session entry:
- `/gsd-progress` — show current phase + what's next
- `/gsd-discuss-phase N` — gather context before planning a phase (gray-area surfacing)
- `/gsd-ui-phase N` — generate UI design contract (Phases 2, 10, 11)
- `/gsd-plan-phase N` — produce executable PLAN.md
- `/gsd-execute-phase N` — execute the plan with parallelization (config has `parallelization: true`)
- `/gsd-next` — auto-advance to the next logical step

Workflow toggles (in `.planning/config.json`):
- `mode: yolo` — auto-approve gates
- `granularity: fine` — 8–12 phases (current: 11)
- `model_profile: quality` — Opus for research/roadmap/planner agents
- `research: true`, `plan_check: true`, `verifier: true` — quality belt-and-braces

## Top pitfalls to keep top of mind

(Full list: `.planning/research/PITFALLS.md` — 28 numbered, severity-tagged.)

1. **#7 — Leaky `ChatProvider` abstraction** (Phase 8) — see Non-negotiable Rule #1
2. **#12 — EB single-app vs 13 microservices mismatch** (Phase 1/11) — Day-1 coordinator query is critical
3. **#26 — Day-1 bikeshedding** (Phase 1) — saga + API contracts must be written before any service code
4. **#5 — Iyzico webhook unreachable on localhost** (Phase 6) — public tunnel decision is a deliverable
5. **#11 — Cross-schema accidental joins** (every backend phase) — distinct DB users with role-level deny enforce the boundary

## When something feels off

- Architectural confusion → re-read `.planning/research/ARCHITECTURE.md` (per-service contracts + saga events)
- Stack/version question → `.planning/research/STACK.md`
- "Should I add this feature?" → check `.planning/REQUIREMENTS.md` Out-of-Scope first, then PROJECT.md
- "Which AI feature is in scope?" → only the chat assistant (with full tool use) and the MCP server. NL search and recommendations emerge from chat tool-use, NOT separate UIs (per PROJECT.md Out-of-Scope)

---
*Last updated: 2026-04-28 after initial roadmap*
