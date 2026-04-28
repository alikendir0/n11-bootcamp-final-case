---
phase: 01-foundations-day-1-contracts
plan: 02
subsystem: contracts
tags: [saga, rabbitmq, json-schema, openapi, gateway-routing, rfc-7807, sse, correlation-id]

requires:
  - phase: 01-01
    provides: "Repository skeleton (Gradle multi-module + .planning/ tree) so contract docs land in the canonical location"
provides:
  - .planning/saga-contracts.md (147 lines, 9 sections — envelope, topology, DLX/DLQ, retry, idempotency, correlation, catalog, drift gate, schema-naming)
  - 9 JSON-Schema 2020-12 files in .planning/saga-contracts/ (envelope + 8 payloads)
  - .planning/api-contracts.md (266 lines, 7 sections — endpoints, gateway routing, public allowlist, auth-strip, correlation-ID propagation, SSE caveat, RFC-7807 problem+json with 5 example responses)
  - Locked retry-policy wording: "3 total attempts (= 1 initial + 2 retries). Delays between attempts: 1s, then 5s. After the 3rd attempt fails, the message goes to DLQ. The 30s upper bound is a safety cap on the exponential growth of the backoff (multiplier=5, max=30000ms), not a delay between attempts 3 and 4 — there is no attempt 4."
  - Locked SSE caveat ("metadata.response-timeout: -1") so Phase 8 has a known anchor
  - Locked RFC-7807 shape (T-01-04 mitigation: sanitized `detail`)
  - Cross-document link: api-contracts §5 ↔ saga-contracts §6 (correlation-ID story)
affects: [01-03, 01-04, 01-05, 01-06, 01-07, 01-08, all-Phase-3-onward, especially Phase-5-saga, Phase-8-SSE]

tech-stack:
  added: [json-schema-2020-12, rfc-7807-problem-json]
  patterns:
    - Drift gate via classpath-loaded canonical schemas (D-08): every Phase 5+ saga test extends AbstractEventSchemaTest, which validates produced JSON against the corresponding .schema.json
    - Two-step envelope+payload validation (envelope.schema.json validates wire metadata, then payload validates against per-eventType schema)
    - Producer outbox + consumer processed_events inbox = exactly-once effect under at-least-once delivery
    - Authorization-strip at gateway + X-User-Id/X-User-Roles/X-Correlation-Id header injection (defense at the edge, trust the mesh)

key-files:
  created:
    - .planning/saga-contracts.md
    - .planning/saga-contracts/envelope.schema.json
    - .planning/saga-contracts/order-created.schema.json
    - .planning/saga-contracts/stock-reserved.schema.json
    - .planning/saga-contracts/stock-reserve-failed.schema.json
    - .planning/saga-contracts/payment-completed.schema.json
    - .planning/saga-contracts/payment-failed.schema.json
    - .planning/saga-contracts/order-confirmed.schema.json
    - .planning/saga-contracts/order-cancelled.schema.json
    - .planning/saga-contracts/stock-released.schema.json
    - .planning/api-contracts.md
  modified: []

key-decisions:
  - "Retry-policy wording is verbatim and load-bearing — any later doc that paraphrases must link back, not restate. Pitfall #6 specifically warned that earlier drafts conflated the 30s safety cap with a per-attempt delay."
  - "Schema name `orders` (plural) — not `order` (SQL reserved word). Saga vocabulary stays singular (`order.created`, `orderId`); only the Postgres schema goes plural."
  - "All 9 schemas use `additionalProperties: false` and explicit `required` arrays — strict validation is the drift gate."
  - "Cross-reference policy locked: api-contracts.md links saga-contracts.md (and vice versa) for the correlation-ID story so future authors can't silently fork the contract."

patterns-established:
  - "Saga contract authority: any new saga event MUST be added to .planning/saga-contracts.md AND .planning/saga-contracts/<eventType>.schema.json BEFORE producer/consumer code is written. Schema is the single source of truth."
  - "REST contract authority: any new endpoint extends the per-service tables in api-contracts.md. Springdoc OpenAPI generates the per-service rigor at impl time; this doc locks the cross-service shape (route prefix, auth, error shape, header injection)."
  - "Drift gate (D-08): Plan 04 will copy .planning/saga-contracts/*.schema.json into common-events/src/main/resources/saga-schemas/ so AbstractEventSchemaTest can load them via classpath: `ClassLoader.getResourceAsStream(\"saga-schemas/\" + eventType.replace('.', '-') + \".schema.json\")`."

requirements-completed:
  - ARCH-12
  - ARCH-05
  - QUAL-07

duration: ~5min
completed: 2026-04-28
---

# Phase 01 Plan 02: Day-1 Contracts (Saga + REST) Summary

**Day-1 saga and REST contracts locked: 8-field envelope, 4 exchanges + 12 queues, DLX/DLQ convention, exact retry-policy wording (3 total attempts = 1 + 2 retries; 1s/5s delays; 30s cap is a multiplier ceiling not a 4th delay), idempotency two-mechanism contract, gateway routing prefix map, RFC-7807 problem+json shape with 5 example responses, and the SSE caveat (`metadata.response-timeout: -1`) — Phase 5+ producers/consumers and Phase 3+ services now have an authoritative reference and cannot drift.**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-04-28T18:45Z
- **Completed:** 2026-04-28T18:50Z
- **Tasks:** 2
- **Files created:** 11 (1 saga-contracts.md + 9 schema.json + 1 api-contracts.md)

## Accomplishments

- `.planning/saga-contracts.md` (147 lines, 9 sections, 45 markdown table rows) locks the envelope, topology, DLX/DLQ, retry, idempotency, correlation, catalog, drift gate, and the `orders`-vs-`order` schema-naming note
- 9 JSON-Schema 2020-12 files all parse cleanly (validated via Python `json.load`; jq unavailable on this Windows host) and all declare `$schema: https://json-schema.org/draft/2020-12/schema` + `additionalProperties: false` + explicit `required` arrays
- `.planning/api-contracts.md` (266 lines, 7 sections) locks the per-service endpoint inventory (10 services), the gateway routing prefix map (10 prefixes + 503 catch-all), the public allowlist (7 entries), the authorization-strip rule, the correlation-ID propagation table (6 hops), the SSE caveat with the exact `metadata.response-timeout: -1` value, and the RFC-7807 problem+json shape with 5 example responses (validation 400 / not-found 404 / conflict 409 / unauthorized 401 / internal 500)
- T-01-04 mitigation locked: every example response uses generic `detail` text — no exception class names, SQL fragments, or stack traces leak
- Cross-references both directions: api-contracts.md → saga-contracts.md (4 links) and saga-contracts.md → envelope.schema.json (and the rest of the schema directory)
- Pitfall #26 closed for the saga and REST surfaces: no later phase needs to reinvent these

## Task Commits

1. **Task 1: saga-contracts.md + 9 JSON-Schema files** — `7b95a2e` (feat: lock Day-1 saga contracts)
2. **Task 2: api-contracts.md** — `7ab6b21` (feat: lock Day-1 API contracts)

**Plan metadata commit:** *(see post-plan commit below)*

## Files Created/Modified

- `.planning/saga-contracts.md` — envelope/topology/DLQ/retry/idempotency/correlation/catalog/drift-gate/schema-naming
- `.planning/saga-contracts/envelope.schema.json` — 8-field wire envelope (uuid eventId, dotted eventType, integer eventVersion, RFC3339 occurredAt, uuid correlationId, nullable causationId, producer string, payload object)
- `.planning/saga-contracts/order-created.schema.json` — order-service emits; required: orderId/userId/currency=TRY/totalAmount/items[≥1]
- `.planning/saga-contracts/stock-reserved.schema.json` — inventory-service emits; required: orderId/reservationId/reservedItems[≥1]
- `.planning/saga-contracts/stock-reserve-failed.schema.json` — inventory-service emits compensation trigger; reason ∈ {INSUFFICIENT_STOCK, PRODUCT_NOT_FOUND, RESERVATION_CONFLICT}
- `.planning/saga-contracts/payment-completed.schema.json` — payment-service emits after Iyzico success; carries iyzicoPaymentId
- `.planning/saga-contracts/payment-failed.schema.json` — payment-service emits; reason ∈ {DECLINED, TIMEOUT, FRAUD, INSUFFICIENT_FUNDS, UNKNOWN}
- `.planning/saga-contracts/order-confirmed.schema.json` — order-service emits at saga happy-path closure; cart-service + notification-service consume
- `.planning/saga-contracts/order-cancelled.schema.json` — order-service emits at compensation; reason ∈ {OUT_OF_STOCK, PAYMENT_DECLINED, USER_CANCELLED, PAYMENT_TIMEOUT} (4 values, audited)
- `.planning/saga-contracts/stock-released.schema.json` — inventory-service emits after compensation; audit-only consumer
- `.planning/api-contracts.md` — 7-section REST contract (endpoints / routing / allowlist / auth-strip / correlation / SSE / RFC-7807)

## Decisions Made

- **Retry-policy wording is the load-bearing line, locked verbatim.** Any later doc that needs the policy MUST link back to `saga-contracts.md` §4 rather than restate it. Pitfall #6 specifically warned earlier drafts had drifted into "30s between attempts" or "4 attempts" wording — both wrong.
- **Schema name `orders` (plural) only in DDL contexts.** The saga vocabulary remains singular (`order.created`, `orderId`, `order-service`). Only the Postgres schema and the SQL identifier are plural. This was decided on RESEARCH finding #5 (SQL `ORDER` reserved word) and explicitly re-stated in `saga-contracts.md` §9 + `api-contracts.md` §1 (order-service section heading) so Plan 03's `init.sh` and Plan 06's `service-template.yml` placeholders cannot drift.
- **JSON-Schema strictness.** Every payload schema uses `additionalProperties: false` + explicit `required` arrays. This is the drift gate's teeth — produced JSON with an extra field will fail `AbstractEventSchemaTest` (Plan 04). If a future phase legitimately needs a new field, it goes through this contract first.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] `jq` unavailable on this Windows host**

- **Found during:** Task 1 (verify gate)
- **Issue:** The plan's `<verify>` block uses `jq -e .` to validate that each schema file parses as valid JSON, but `jq` is not on PATH on this machine (only Node 22.5.1 and Python 3.13.9 are available). Without a JSON parser the verify gate cannot run.
- **Fix:** Substituted `python -c "import json; json.load(open('<file>'))"` for `jq -e .` — functionally equivalent (parse-and-fail-on-error). Ran across all 9 schemas: all parsed cleanly. The acceptance criteria are still met; only the verifier tool changed.
- **Files modified:** none (this is a tool substitution, not a content change)
- **Verification:** All 9 schema files round-trip through `python json.load`; `grep -l 'json-schema.org/draft/2020-12/schema' .planning/saga-contracts/*.schema.json | wc -l` returns 9
- **Committed in:** part of `7b95a2e` (no separate commit — fix was at verifier-runtime, not in source)

---

**Total deviations:** 1 (tool substitution to work around missing `jq`; semantically identical). No content changes; all acceptance criteria met against the locked schema files. Rule 3 (blocking issue, auto-fix using equivalent tooling) applied — no architectural decision required.

## Issues Encountered

- None of substance. The Windows host's missing `jq` was the only friction; Python served as a 1:1 replacement.

## Verification Summary

End-of-plan checks (all passing):

| Check | Result |
|-------|--------|
| 11 files exist (1 saga.md + 9 schema.json + 1 api.md) | ✅ |
| All 9 schemas parse as valid JSON | ✅ (Python json.load) |
| Retry policy locked-wording occurrence count | ✅ (1 occurrence of the verbatim sentence) |
| `metadata.response-timeout: -1` present | ✅ (2 occurrences in api-contracts.md) |
| `orders` (plural) referenced in init contexts | ✅ (saga-contracts.md §9 + api-contracts.md §1) |
| All 9 schemas declare 2020-12 | ✅ |
| order-cancelled.schema.json has 4 reasons | ✅ |
| api-contracts.md ≥ 150 lines | ✅ (266) |
| saga-contracts.md ≥ 100 lines, ≥ 25 table rows | ✅ (147 lines, 45 rows) |
| All 7 api-contract sections present | ✅ |
| 5 example RFC-7807 responses | ✅ |
| Cross-link saga ↔ api | ✅ |
| No deletions in HEAD~2..HEAD | ✅ |

## Notes for Downstream Plans

- **Plan 04 (common-events):** Copy `.planning/saga-contracts/*.schema.json` into `common-events/src/main/resources/saga-schemas/` so `AbstractEventSchemaTest` can resolve them via classpath. Naming: `saga-schemas/<eventType-with-dots-replaced-by-dashes>.schema.json` (e.g. `saga-schemas/order-created.schema.json` for `order.created`). The schema files are already named in this exact convention so the copy is a flat directory copy.
- **Plan 05 (eureka + config):** The gateway routing table in `api-contracts.md` §2 is the source of truth. Phase 1's `api-gateway.yml` ships only `discovery.locator.enabled=true` + the global `httpclient.response-timeout: 60s` default + a commented-out SSE-route shape. Service-id casing is auto-lowered.
- **Plan 06 (api-gateway):** Phase 1 posture is `permitAll()` (D-14). Phase 3 will flip JWT enforcement on; the public allowlist in `api-contracts.md` §3 is what to permit then.
- **Phase 5 (cart + order saga):** Use the canonical schemas as test fixtures via `AbstractEventSchemaTest`. Producer outbox table schema is in `saga-contracts.md` §5.1; consumer `processed_events` inbox is created by `service-template/src/main/resources/db/migration/V1__init_processed_events.sql`.
- **Phase 8 (ai-service SSE):** Activate the commented-out `ai-chat-stream` route in `config-server/src/main/resources/config/api-gateway.yml`. The exact `metadata.response-timeout: -1` line is the load-bearing override; do NOT add `ModifyResponseBody` or retry filters on this route.

## Next Phase Readiness

- ✅ Contracts are authoritative: any new saga event or REST endpoint must update these docs first
- ✅ Drift gate has its inputs ready (Plan 04 will wire `AbstractEventSchemaTest`)
- ✅ Pitfall #26 (Day-1 bikeshedding) closed for saga and REST surfaces
- ✅ Phase 1 Wave 1 plan 01-02 complete; Plans 01-03 (postgres + rabbit) and 01-04 (common libs) can run in parallel — no file overlap with this plan's outputs

## Self-Check: PASSED

All 11 files created, all 2 commits exist (`7b95a2e`, `7ab6b21`), all acceptance criteria verified.

---
*Phase: 01-foundations-day-1-contracts*
*Plan: 01-02*
*Completed: 2026-04-28*
