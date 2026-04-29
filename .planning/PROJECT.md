# n11 Bootcamp Final Case — Agentic E-Commerce Clone

## What This Is

A microservices-based e-commerce backend with a Turkish-language React storefront, modeled after [n11.com](https://www.n11.com), built as the graded final case for the Patika.dev × n11 Spring Boot Bootcamp. The defining differentiator is **agentic commerce**: the storefront ships with an AI shopping assistant ("Yapay Zeka Alışveriş Asistanı") that answers customer questions and *takes actions* (search, cart, checkout) via Gemini-powered tool use, and the same toolset is exposed to external AI agents through an MCP server.

## Core Value

**A graders-impressing demonstration that the candidate can architect a clean, SOLID, microservices system *and* layer differentiated AI capabilities on top of it.**

If everything else fails, this must work: the bootcamp brief lands rock-solid (microservices + Iyzico + JWT + tests + deploy + CI), and the AI shopping assistant is a believable, working demo.

## Requirements

### Validated

(None yet — ship to validate)

### Active

#### Bootcamp Brief — Backend (must-have, locked by spec)

- [ ] RESTful APIs across product listing, cart, and order operations
- [ ] PostgreSQL persistence (DB-per-service, single instance) for products, orders, users
- [ ] Pagination on the product listing endpoint
- [ ] Cart operations: add, remove, update
- [ ] Order management: order creation and order flow
- [ ] Iyzico payment integration (sandbox)
- [ ] JWT-based authentication and authorization (validated at API gateway)
- [ ] Unit and integration tests (smoke unit + 1–2 integration per service on critical path)
- [ ] Swagger / OpenAPI documentation per service (Springdoc)
- [ ] Logging mechanism for error tracking

#### Bootcamp Brief — Frontend (must-have, locked by spec)

- [ ] React.js storefront with Turkish UI copy
- [ ] Product listing + product detail pages
- [ ] React Hooks (`useState`, `useEffect`) for state and data flow
- [ ] Pagination UI component
- [ ] Cart UI for cart operations
- [ ] API integration with backend services
- [ ] Error handling: user-friendly error messages and loading states
- [ ] Storefront layout informed by Playwright reconnaissance of n11.com structure

#### Bootcamp Brief — DevOps & Deployment (must-have, locked by spec)

- [ ] Backend services containerized (Jib, no Dockerfiles)
- [ ] GitHub Actions CI pipeline (build, test) on push/PR; release-tagging job builds + pushes Jib images for the local docker-compose pull
- [ ] Jenkins comparison documented (pipeline-logic understanding)
- [ ] Full-stack deployment via docker-compose on the candidate's machine (local production-like host); public demo URL exposed via Cloudflare Tunnel or ngrok during the interview window
- [ ] Slack notifications fire on CI build success/failure (deploy-equivalent signal for the local deploy)

#### Bootcamp Coordinator — Architectural Mandates

- [ ] Microservices architecture with **at least 10 services** (target: 13)
- [ ] Eureka service discovery
- [ ] RabbitMQ messaging
- [ ] SAGA pattern (choreography via RabbitMQ events)

#### Self-Imposed Differentiators (the "stand out" layer)

- [ ] MCP server exposing the storefront to external AI agents — **full agent purchase flow** (read + cart + place_order + payment_link)
- [ ] In-storefront AI shopping assistant — Turkish chat (floating bubble, persistent across pages), powered by Gemini 3.0 Flash, with RAG over the product catalog and tool use for cart/checkout actions
- [ ] Provider-agnostic LLM abstraction — `ChatProvider` / `EmbeddingProvider` ports with Gemini adapter (so a future OpenAI/Claude swap is one adapter away)
- [ ] Semantic search via Gemini Embedding model + pgvector

### Out of Scope

- **Multiple LLM providers in v1** — the abstraction supports it; we only implement the Gemini adapter. Why: scope; the abstraction itself is the SOLID demo.
- **NL-search and recommendations as separate UI features** — these naturally emerge from the chat assistant's tool use, not a separate search bar / recs panel. Why: don't dilute AI investment across many shallow features; go deep on the chat assistant.
- **Reviews and ratings** — not in bootcamp brief, no AI summary feature requested. Why: scope.
- **Admin dashboard / merchant tooling** — not in bootcamp brief. Why: scope.
- **Mobile app / native clients** — web-first only. Why: brief is React-only.
- **Real email / SMS sending in notification-service** — log-only mock for the demo. Why: time, no SMTP/Twilio account setup.
- **Production-grade observability stack (ELK, Loki, Grafana, distributed tracing)** — basic SLF4J/Logback per service + structured logs only. Why: 6-day window, not in brief.
- **Strict DB-per-service on separate Postgres instances** — single Postgres host with one schema per service instead. Why: deploy is a single docker-compose stack on the candidate's machine; multiple Postgres instances add ops surface for zero architectural gain (the boundary already lives at schema + DB-user role-deny).
- **Real-time WebSocket features (live stock, live cart sync)** — not required by brief, distracts from AI focus. Why: scope.
- **Heavy test coverage (>70%)** — smoke unit + 1–2 integration per service on critical path is the target. Why: 6-day window.

## Context

**Bootcamp framing.** This is the capstone case for the Patika.dev × n11 Spring Boot Bootcamp. Submission is graded; passing the rubric earns an interview slot at n11. The candidate pool is large and the field is competitive — *differentiation* is an explicit goal of this submission, not just compliance.

**Grading lens.** The candidate believes graders weight **code quality + SOLID** most heavily (the brief literally shouts SOLID in caps). All architectural decisions should serve that signal: clean layering per service, explicit ports/adapters for external integrations (LLM provider, payment gateway), dependency inversion across service boundaries.

**AI-as-differentiator strategy.** Most bootcamp candidates will ship a clean Spring Boot + React e-commerce app. To stand out, this submission leans hard into agentic commerce: the same tool set is exposed via two surfaces — the in-storefront chat assistant for end users, and the MCP server for external AI agents (e.g., Claude Desktop). This isn't bolted-on AI; it's a deliberate "agent-first" architecture choice that pays for itself in both code-quality marks and demo wow-factor.

**Execution model.** The candidate operates AI-assisted — large plans get executed by AI agents within the 6-day window. Architectural decisions, integration glue, and verification still require human-driving time, so the plan must front-load decisions and let agents grind on implementation.

**Reconnaissance plan.** Before frontend implementation, run a Playwright pass against [n11.com](https://www.n11.com) to capture header/nav/grid/PDP/cart layout structure, common Turkish copy patterns, and category taxonomy. This recon also informs frontend toolchain choice (Vite vs Next, styling approach).

**Verify-before-implement policy.** External SDKs (Gemini, Iyzico, Spring Cloud, MCP) drift. Before any plan-phase commits to specific calls, the researcher fetches official docs and the planner cites the relevant section. No SDK code is written from training-data recall alone.

## Constraints

- **Timeline**: ~6 days from 2026-04-28 — AI-assisted execution. Aggressive but workable for the 13-service scope because AI absorbs typing time. Architecture decisions are pre-locked; agents implement.
- **Tech stack — Backend**: Java 21, Spring Boot 3.x, Spring Cloud (Eureka, Gateway, Config), Spring Data JPA, Springdoc OpenAPI, RabbitMQ (Spring AMQP), PostgreSQL 16, pgvector extension for embeddings, Iyzico Java SDK (sandbox).
- **Tech stack — AI**: Gemini 3.0 Flash for chat (verify model availability against official Google AI docs at research time), Gemini Embedding model for semantic search. Behind a provider-agnostic `ChatProvider` / `EmbeddingProvider` port.
- **Tech stack — Frontend**: React (toolchain TBD post Playwright n11 recon — likely Vite + TypeScript + Tailwind + Zustand, but confirmed only after the recon pass).
- **Tech stack — DevOps**: Jib (no Dockerfiles), GitHub Actions for build + test (and Jib image publish on release tags), local docker-compose as the deploy target on the candidate's machine, Cloudflare Tunnel (preferred) or ngrok for exposing the demo URL + Iyzico webhook to the public internet, Slack webhook for CI/build notifications.
- **Architecture**: 13 microservices — `eureka-server`, `config-server`, `api-gateway`, `identity-service`, `product-service`, `inventory-service`, `cart-service`, `order-service`, `payment-service`, `notification-service`, `search-service`, `mcp-server`, `ai-service`. DB-per-service on a single Postgres host. SAGA via RabbitMQ choreography.
- **Auth posture**: JWT issued by `identity-service`, validated **only at the gateway**, downstream services trust gateway-injected `X-User-Id` / `X-User-Roles` headers. Internal-mesh trust boundary at the gateway.
- **Localization**: Frontend UI copy is Turkish. Product seed data uses Turkish names/descriptions. Backend logs and code identifiers stay English.
- **Security**: Iyzico sandbox keys only; no real card data. Slack webhook URL kept out of source. Tunnel access tokens (Cloudflare Tunnel / ngrok) treated as secrets — env vars only, never committed. The local Postgres + RabbitMQ are bound to localhost only; the gateway is the sole port the tunnel exposes externally.
- **Budget**: $0 cloud spend — deploy runs on the candidate's machine. Tunnel: Cloudflare Tunnel (free tier with a personal domain) or ngrok (free tier with random subdomain). Gemini API: free-tier coverage targeted for demo; key kept out of source.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Java 21 + Spring Boot 3.x | LTS, virtual threads, modern record/pattern-matching syntax — strong "modern stack" signal to graders | — Pending |
| 13-service decomposition (eureka + config + gateway + identity + product + inventory + cart + order + payment + notification + search + mcp + ai-service) | Comfortably exceeds the 10-service mandate; every service has a clearly bounded responsibility | — Pending |
| Choreography SAGA via RabbitMQ events | RabbitMQ is required by the brief; choreography is the natural fit and avoids inventing a new orchestrator service. Order saga: `OrderCreated` → reserve stock → `StockReserved` → take payment → `PaymentCompleted` → notify; compensating events on failure | — Pending |
| JWT validated only at the gateway | Standard pattern; gateway injects `X-User-Id` headers; internal services trust the mesh — fewer JWT secrets distributed, simpler service code | — Pending |
| DB-per-service on a single Postgres instance | Microservices boundary enforced at the data layer (one schema per service + per-service DB user with role-level deny on other schemas); single host keeps the local docker-compose stack lean | — Pending |
| Choreography over orchestration for SAGA | Simpler infra (no orchestrator service); each service owns its participation in the saga. Tradeoff: harder to trace — mitigated by structured logs with correlation IDs | — Pending |
| Provider-agnostic LLM abstraction (`ChatProvider` / `EmbeddingProvider` ports + Gemini adapter only) | Direct demo of dependency inversion + open-closed; serves the "code quality + SOLID" grading lens, makes AI a first-class architectural citizen | — Pending |
| Dedicated `ai-service` (not folded into search-service) | Bounded context for Gemini integration, prompt management, conversation state, tool dispatch. Cleaner service contracts; bumps total to 13, well past the 10 minimum | — Pending |
| Chat assistant + MCP server share one toolset | One agent backend, two surfaces (frontend chat bubble, external MCP). Pure DRY on the most differentiating feature | — Pending |
| Gemini 3.0 Flash via official Google AI API (verify at research time) | User directive; cheap, fast, function-calling, Turkish-fluent. Researcher must confirm model name + API surface against current docs | — Pending |
| Toolchain decision deferred until n11 recon | Picking Vite-vs-Next-vs-CRA blind is silly — once we see how dynamic n11's grid/PDP are, we'll know if SSR helps. Recon → decide | Resolved 2026-04-29 — superseded by the locked Vite 8 + React 19 SPA row below |
| Playwright recon before frontend phase | Capture header/nav/grid/PDP/cart structure + Turkish copy patterns from n11.com to inform layout, save guesswork | — Pending |
| Tests: smoke unit + 1–2 integration per service on critical path | Visible test coverage in every service, not chasing %. Realistic for 6 days | — Pending |
| Notification-service: log-only mock email | Service exists, consumes events, full SAGA participant; mocks the SMTP send. All architecture credit, no real-email plumbing | — Pending |
| Deploy target = local docker-compose on the candidate's machine; demo URL exposed via Cloudflare Tunnel (preferred) or ngrok | User-locked 2026-04-28: candidate's machine has sufficient compute to host all 13 services + Postgres + RabbitMQ in a single docker-compose stack. AWS Elastic Beanstalk + RDS dropped — there is no architectural fit problem to mitigate (Pitfall #12 vanishes) and no AWS spend to manage. Trade-off: deploy is online only while the candidate's machine + tunnel are running; demo posture is "live during the interview window." | Locked 2026-04-28 (revised from EB+RDS) |
| Frontend toolchain: Vite 8 + React 19 SPA + TypeScript strict (`noUncheckedIndexedAccess` + `exactOptionalPropertyTypes`) + Tailwind 4 (`@theme` directive, no config file) + Zustand 5 + React Router 7 + TanStack Query 5 + react-hook-form 7 + zod 4 | Locked post-Phase-2 n11 recon (2026-04-29). Three pieces of recon evidence: (1) **n11 has no in-storefront chat panel** (see `.planning/intel/n11-recon.md` §7) — Phase 11's floating-bubble UX is greenfield; Vite SPA owns the DOM cleanly without RSC re-render gymnastics. (2) **n11 PDP is fully client-rendered after initial HTML** (observed in `.planning/intel/screenshots/pdp-fullpage.png`) — no SSR-only data we'd lose by going SPA; our deliverable is a graded interview demo, not a public-search-indexed marketplace. (3) **JWT validated only at the gateway** (locked Phase 1) — SSR-side auth would force token-forwarding gymnastics through a Node runtime for zero benefit. AI chat panel SSE consumption (Phase 11) maps cleanly to native `EventSource` in a Vite SPA. **Carry-forward to Phase 10:** API base URL injected via `VITE_API_BASE_URL` env var — no hardcoded URLs in source (Pitfall #23 prevention). **Decision matrix score:** Vite 65 / Next 45 (see `.planning/intel/n11-recon.md` §Decision Matrix for full breakdown — weights: code-quality ×3, timeline ×2, recon-evidence ×1, JWT compat ×2, SSE compat ×2, pitfall avoidance ×2, brief literal ×1). | Locked 2026-04-29 (Phase 2) — see `.planning/intel/n11-recon.md` Decision Matrix |

## Open Questions

These remain unresolved at project initialization. Each should be answered before the dependent phase plans:

- **Public tunnel choice** — Cloudflare Tunnel (preferred for stable hostname + free tier with a personal domain) vs ngrok (zero-config but random subdomain unless paid)? *Decide in Phase 6 planning (the Iyzico webhook is the first real consumer).* Affects: Phase 6, Phase 11, demo prep.
- **MCP server tunnel exposure** — share the gateway tunnel (same hostname, `/mcp/**` route) vs a separate dedicated tunnel for the MCP transport? *Decide in Phase 9 (MCP server) planning.* Affects: Phase 9, demo prep.

### Resolved

- **AWS deploy scope** — RESOLVED 2026-04-28 (revised): AWS deployment dropped entirely. Candidate's machine has sufficient compute; deploy = local docker-compose, demo URL via tunnel. Earlier decision (Elastic Beanstalk + RDS, locked 2026-04-28 morning) is superseded. See Key Decisions row. **Caveat:** the bootcamp brief originally listed "AWS deployment (Elastic Beanstalk + RDS)" as a must-have; the candidate is responsible for confirming with the bootcamp coordinator that local-host + tunnel deployment is acceptable, or for reframing this in the README as a deliberate trade-off (cloud-native AWS deploy is a 1–2 day stretch from the current architecture).
- **Frontend toolchain** — RESOLVED 2026-04-29 (Phase 2): Vite 8 + React 19 SPA + TypeScript strict + Tailwind 4 + Zustand 5 + React Router 7 + TanStack Query 5 + react-hook-form + zod. Decision matrix score Vite 65 / Next 45. See Key Decisions row above and `.planning/intel/n11-recon.md` § Decision Matrix for the audit trail. Recon evidence: no in-storefront chat panel observed (Phase 11 invents); n11 PDP fully client-rendered (no SSR-only data); JWT-at-gateway makes SSR-side auth dead weight. API base URL via `VITE_API_BASE_URL` env var (Pitfall #23 prevention).

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-29 — Phase 2 complete: frontend toolchain locked (Vite 8 + React 19 SPA + Tailwind 4 + Zustand 5 + React Router 7 + TanStack Query 5 + react-hook-form + zod). Recon report `.planning/intel/n11-recon.md` is the Phase 10/11 hand-off contract (8 sections, 644 phrases, 25 color tokens, Decision Matrix subsection). FE-01 satisfied. Open Question "Frontend toolchain" moved to Resolved.*

*Earlier — 2026-04-28: deploy target changed from AWS Elastic Beanstalk + RDS to local docker-compose on the candidate's machine (with Cloudflare Tunnel / ngrok for demo URL). All cascading sections (DevOps brief, Out-of-Scope, Tech-stack, Security, Budget, Key Decisions, Open Questions) updated to match.*
