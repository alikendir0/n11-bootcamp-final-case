# Phase 1: Foundations + Day-1 Contracts - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in 01-CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-28
**Phase:** 1-foundations-day-1-contracts
**Areas discussed:** DB isolation model, Day-1 contracts depth, Skeleton scope, AWS coordinator query (later resolved + then dropped — see amendments)

---

## DB Isolation Model

### Q1 — Postgres isolation model

| Option | Description | Selected |
|--------|-------------|----------|
| Schema-per-service in 1 DB | Single DB `n11`, schemas per service, distinct user, REVOKE USAGE on others. Aligns with ARCH-09 wording. | ✓ |
| Database-per-service on 1 host | Separate DBs `identity_db, product_db, ...`, FDW required for cross-DB. Matches ARCHITECTURE.md illustrative naming but contradicts ARCH-09. | |
| You decide | — | |

**User's choice:** Schema-per-service in 1 DB.
**Notes:** Resolves the doc conflict in favor of ARCH-09's role-level deny phrasing.

### Q2 — Cross-schema deny enforcement

| Option | Description | Selected |
|--------|-------------|----------|
| Bootstrap init.sql in docker-compose | One file mounted into Postgres container, declarative, runs at first boot. | ✓ |
| Per-service Flyway V1 migration | Distributed; coordinating REVOKE across services is awkward. | |
| Spring Boot init bean | Heavier moving part, overkill. | |

### Q3 — Per-service DB credentials in dev

| Option | Description | Selected |
|--------|-------------|----------|
| Single .env at repo root + Spring Cloud Config | All 13 user/password pairs in gitignored .env, sourced by docker-compose AND config-server. | ✓ |
| Each service ships its own .env | Redundant, higher leak risk. | |
| Hardcode dev defaults in application-dev.yml | Cheap shortcut, weakens QUAL-09. | |

### Q4 — Schema-isolation boundary smoke

| Option | Description | Selected |
|--------|-------------|----------|
| Plain Testcontainers JUnit + raw JDBC | infra-tests/ module, asserts permission denied on cross-schema SELECT. | ✓ |
| Bash + psql script in CI only | Less canonical Java-shop feel. | |
| Skip — trust SQL grants | Doesn't satisfy the smoke-test wording in success criterion #4. | |

### Q5 — search_path convention

| Option | Description | Selected |
|--------|-------------|----------|
| ALTER USER ... SET search_path in init.sql | Server-side; uniform JDBC URL across services. | ✓ |
| ?currentSchema=<schema> on each JDBC URL | Per-service URL flag; one more moving part. | |
| Schema-qualify every table reference | Most explicit, most boilerplate. | |

---

## Day-1 Contracts Depth

### Q1 — saga-contracts.md depth

| Option | Description | Selected |
|--------|-------------|----------|
| Full event spec + JSON Schema files | Narrative + sibling .schema.json folder; CI-validatable; kills drift. | ✓ |
| Markdown narrative only | Cheaper, mild drift risk at Phase 5. | |
| Lightweight — names + queue topology only | Risk of payload bikeshedding at Phase 5. | |

### Q2 — api-contracts.md depth

| Option | Description | Selected |
|--------|-------------|----------|
| Endpoint table + I/O sketches per service | Enough to write controllers; Springdoc fills OpenAPI rigor at impl. | ✓ |
| Inline OpenAPI snippets per endpoint | Highest fidelity; duplicates work Springdoc auto-generates. | |
| Endpoint-table only, no DTO sketches | Cart-vs-order field-name drift risk. | |

### Q3 — CI drift gate

| Option | Description | Selected |
|--------|-------------|----------|
| JSON-Schema validation in saga integration tests | AbstractEventSchemaTest in common-events; producers fail build on drift. | ✓ |
| Manual review at PR time | No enforcement teeth. | |
| Defer to "hardening" phase | Risks never getting done. | |

### Q4 — Day-1 extras (multi-select)

| Option | Description | Selected |
|--------|-------------|----------|
| Correlation-ID propagation policy doc | Gateway-generates, MDC, RestClient/RabbitTemplate interceptors, AMQP envelope. | ✓ |
| RFC-7807 problem+json shape spec | Lock fields + 4–5 example responses; lives in common-error. | ✓ |
| Gateway routing table (`/api/v1/<svc>/**` map) | Authoritative path→service map + public allowlist. | ✓ |
| DLQ + retry policy spec | 3 attempts 1s/5s/30s; `<exchange>.dlx` naming. | ✓ |

---

## Skeleton Scope

### Q1 — Service modules scaffolded in Phase 1

| Option | Description | Selected |
|--------|-------------|----------|
| Infra trio + service-template archetype | eureka, config, gateway + clone-able template. | ✓ |
| All 13 hello-world services | Heaviest Day-1; many empty modules churning. | |
| Infra trio only | Drift risk; later phases re-derive cross-cutting wiring. | |

### Q2 — service-template contents (multi-select)

| Option | Description | Selected |
|--------|-------------|----------|
| Spring Boot 3.5.14 + Eureka client + Springdoc + Actuator | Baseline runtime. | ✓ |
| Logback JSON encoder + correlation-ID MDC servlet filter | Observability backbone. | ✓ |
| RFC-7807 @ControllerAdvice + ProblemDetail mapper | Common error shape via common-error module. | ✓ |
| Flyway scaffold + processed_events inbox migration | Saga consumer prerequisite. | ✓ |

### Q3 — Shared modules layout

| Option | Description | Selected |
|--------|-------------|----------|
| Flat top-level modules | Settings.gradle stays flat; fits 13 services. | ✓ |
| Grouped: services/* and libs/* | Tidier in IDE; mild value at this scale. | |
| Multi-repo | Out of scope per PROJECT.md. | |

### Q4 — Phase 1 docker-compose shape

| Option | Description | Selected |
|--------|-------------|----------|
| Infra services only, in containers | Postgres+pgvector, RabbitMQ, eureka, config, gateway. Business services from IDE. | ✓ |
| Infra + later services as Jib via profiles | Heavier compose; pays off as services come online. | |
| Bare metal: only datastores in compose | Doesn't satisfy success criterion #1. | |

### Q5 — Gateway shell behavior pre-services

| Option | Description | Selected |
|--------|-------------|----------|
| Discovery-locator + health + Springdoc aggregator stub | Eureka auto-routes, filters coded but permitAll, 503 on unmatched. | ✓ |
| Explicit hardcoded routes per service | More typing; less reliant on Eureka. | |
| Health + actuator only | Weakest "foundations" framing; success criterion #1 may not pass. | |

---

## AWS Coordinator Query

### Q1 — Channel + delivery

| Option | Description | Selected |
|--------|-------------|----------|
| You already know the channel | Specify in follow-up. | |
| Bootcamp Discord/Slack channel | Public; benefits peers. | |
| Direct DM/email to coordinator | Faster, private; one-shot. | ✓ |

### Q2 — Question framing

| Option | Description | Selected |
|--------|-------------|----------|
| Hard-vs-flexible with 3 fallback paths | Direct yes/no question with shown thinking. | ✓ |
| Open-ended ("what AWS shape do you expect") | Vague-answer risk. | |
| Verbatim brief language | Adds a turn. | |

### Q3 — Deadline + fallback

| Option | Description | Selected |
|--------|-------------|----------|
| EOD Day 2 → ECS Fargate | Two business days for coordinator; Fargate cleanest 13-microservice fit. | ✓ |
| EOD Day 1 → single-EC2 + docker-compose | Tight; least cloud-native signal. | |
| EOD Day 3 → EB single-instance Docker | Cuts Phase 11 deploy time short. | |

### Q4 — Recording locus

| Option | Description | Selected |
|--------|-------------|----------|
| PROJECT.md Key Decisions row + Open Questions update | Flips Open Questions row to Resolved. | ✓ |
| New file `.planning/decisions/aws-deploy-scope.md` | ADR-style trail; extra doc. | |
| Comment in Phase 11 PLAN.md | Punt; doesn't satisfy success criterion #3. | |

---

## Claude's Discretion

- Concrete user passwords, pgvector pinning, Logback JSON field set per env, Springdoc aggregator URL rendering — pick reasonable defaults.
- Whether `service-template` is a clonable archetype vs a `apply from:` Gradle subproject — recommend clone-and-edit; flag the alternative in PLAN.md.
- JSON-Schema validator library choice for the drift gate — pick a current pinned version.
- gitleaks placement (CI-only Day-1; pre-commit hook deferred to Phase 11).
- config-server backing — `native` filesystem for Day 1; git-backed deferred unless interview demands it.

## Post-Discussion Amendment (2026-04-28)

After CONTEXT.md was first written, the user clarified that AWS credits are available and committed directly to **Elastic Beanstalk + RDS** as the deploy target — no coordinator query needed. This supersedes D-15..D-18 in the AWS Coordinator Query area above.

**New decisions (replace D-15..D-18 in CONTEXT.md):**
- Deploy target locked: Elastic Beanstalk + RDS Postgres 16.
- Shape: single AWS instance hosting all 13 services compose-style (RabbitMQ in same compose; RDS is the only externalized datastore). Sidesteps Pitfall #12 because the EB-vs-13-microservices mismatch only exists when EB tries to host 13 separate apps.
- EB platform mode: prefer EB Multi-container Docker (Amazon Linux 2 platform) if available; fall back to EB Single-instance Docker with a supervisor pattern. Final pick deferred to Phase 11 after region/account availability check.
- Coordinator query is dropped entirely — no Day-1 outbound message, no fallback deadline.

**Reasoning trail (paraphrased from the conversation):**
- User raised: would AWS credits make EB+RDS workable? — Answer: credits change cost, not architectural fit; EB's modern single-app model still doesn't fit 13 microservices regardless of cost.
- User raised: RDS is one Postgres so that side is fine; is the problem with Jib? — Answer: no, Jib produces 13 OCI images cleanly; the problem is EB's hosting model expecting one image per env.
- User raised: why not host all 13 on a single instance? — Answer: totally legitimate for a 6-day demo; gives up cloud-native signal but gains brutal dev/prod parity. EB Multi-container Docker is the cleanest path; plain EC2 + compose is the honest fallback; EB single-instance + supervisor is a middle option.
- User raised: can we defer the deploy decision until Phase 11? — Answer: yes, Phases 1–10 don't couple to deploy shape; locking just the *shape* (single-instance compose-style) on Day 1 is enough to kill Pitfall #12 as a Day-1 risk.
- User locked: "We will go with Elastic Beanstalk for sure, plan accordingly." Recorded above; CONTEXT.md and PROJECT.md / STATE.md updated.

## Deferred Ideas

- gitleaks pre-commit hook → Phase 11 (DevOps).
- Git-backed config-server → revisit Phase 11.
- Pre-commit hooks (Spotless, ktlint, checkstyle) → not Phase 1.
- Distributed tracing (Sleuth/OpenTelemetry) → out of scope (PROJECT.md).
- Redis-backed rate limit → revisit if multi-instance gateway is a Phase 11 concern.
- Full multi-instance compose → satisfied in Phase 11 via `full` profile + Jib images.
- JWKS endpoint / JWT issuer config → Phase 3.
- Static-yıldız placeholder for missing reviews → Phase 10/11.

## Post-Discussion Amendment #2 (2026-04-28, same day, supersedes Amendment #1)

The user revised the deploy decision: **AWS dropped entirely.** The candidate's local machine has sufficient compute to run all 13 services + Postgres + RabbitMQ in one docker-compose stack, so AWS Elastic Beanstalk + RDS is no longer needed. This supersedes Amendment #1's EB+RDS lock and reverts the AWS Coordinator Query area to "not applicable."

**New decisions (replace D-15..D-18 in CONTEXT.md again):**
- Deploy target locked: local docker-compose on the candidate's machine.
- Shape: single docker-compose stack with a `full` profile = 13 Jib images + Postgres-16 + RabbitMQ-4 on the candidate's host.
- Public exposure: Cloudflare Tunnel (preferred — stable hostname via personal domain on free tier) → ngrok (fallback — zero-config / random subdomain on free tier). Phase 6 verifies for Iyzico webhook; Phase 11 wires the demo URL.
- $0 cloud spend; AWS account / credits no longer needed.

**Reasoning trail (paraphrased):**
- User stated: "the planned program will be deployed locally from my computer, which has enough computing power, this nullifies the need for AWS Elastic B deployment."
- Implication: Pitfall #12 (EB-vs-13-microservices fit) is no longer in scope; the only remaining deploy-day risks are tunnel uptime and the candidate's machine staying on through the demo (mitigated via `restart: unless-stopped` + a 30-second `compose up` rehearsal documented in the README).
- Caveat surfaced to user: bootcamp brief originally listed AWS deployment as must-have. Coordinator confirmation that local-host + tunnel deployment is acceptable for grading is recommended; the decision to absorb that grading risk lies with the candidate.
