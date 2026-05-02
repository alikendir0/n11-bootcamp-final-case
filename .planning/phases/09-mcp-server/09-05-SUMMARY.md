---
phase: 09-mcp-server
plan: 05
subsystem: infra
tags: [api-gateway, docker-compose, mcp, streamable-http, jwt]

requires:
  - phase: 09-mcp-server
    provides: mcp-server service module, agent JWT bridge, and Streamable HTTP endpoint configuration
provides:
  - Explicit api-gateway /mcp/** route to lb://MCP-SERVER with response-timeout disabled for Streamable HTTP
  - Internal-only docker-compose mcp-server service using MCP_API_KEY from .env
  - Activated /mcp/** API contract row documenting JWT Bearer auth and StripPrefix=0
affects: [phase-09-runbook, phase-11-demo, api-gateway, docker-compose]

tech-stack:
  added: []
  patterns:
    - Gateway Streamable HTTP route mirrors ai-service SSE timeout posture
    - Internal-only compose service with env_file secret injection

key-files:
  created:
    - .planning/phases/09-mcp-server/09-05-SUMMARY.md
  modified:
    - config-server/src/main/resources/config/api-gateway.yml
    - docker-compose.yml
    - .env.example
    - .planning/api-contracts.md

key-decisions:
  - "MCP Streamable HTTP is exposed only through the existing gateway route /mcp/**, with no public allowlist entry."
  - "mcp-server remains an internal-only compose service: no ports mapping, no Postgres dependency, and no RabbitMQ dependency."

patterns-established:
  - "Gateway long-lived transports use per-route response-timeout: -1 and avoid body filters."
  - "MCP_API_KEY is represented as an empty .env.example placeholder and injected through docker-compose env_file/.env only."

requirements-completed: [AI-12]

duration: 3 min
completed: 2026-05-02
---

# Phase 09 Plan 05: Gateway and Compose Front Door Summary

**/mcp/** is now routed through the API gateway to the internal mcp-server compose service with Streamable HTTP timeout handling and JWT-only access.**

## Performance

- **Duration:** 3 min
- **Started:** 2026-05-02T08:16:17Z
- **Completed:** 2026-05-02T08:18:58Z
- **Tasks:** 4
- **Files modified:** 5

## Accomplishments

- Added an explicit `mcp-server` route in `api-gateway.yml` at line 145: `/mcp/**` → `lb://MCP-SERVER`, `StripPrefix=0`, `PreserveHostHeader=true`, and `response-timeout: -1`.
- Added an internal-only `mcp-server` service in `docker-compose.yml` at line 534 with healthcheck on port 8090, `MCP_TRANSPORT=http`, and `MCP_API_KEY=${MCP_API_KEY:-}`.
- Added the empty `.env.example` `MCP_API_KEY=` placeholder at line 68 without committing a plaintext key.
- Activated the `.planning/api-contracts.md` §2 `/mcp/**` routing row at line 103 and documented the Streamable HTTP caveat in §6.

## Task Commits

Each task was committed atomically:

1. **Task 1: api-gateway.yml /mcp/** route block + Springdoc decision** - `60d7376` (feat)
2. **Task 2: docker-compose.yml mcp-server service block** - `45fc160` (feat)
3. **Task 3: .env.example MCP_API_KEY placeholder** - `bdea048` (chore)
4. **Task 4: api-contracts.md §2 routing table activation** - `1b6b092` (docs)

**Plan metadata:** committed separately after state/roadmap updates.

## Files Created/Modified

- `config-server/src/main/resources/config/api-gateway.yml` - Adds the `/mcp/**` gateway route with Streamable HTTP long-connection metadata and no Springdoc aggregation entry.
- `docker-compose.yml` - Adds the internal-only mcp-server service block without direct host port exposure, Postgres dependency, or RabbitMQ dependency.
- `.env.example` - Documents the local `MCP_API_KEY=` placeholder and seed-log workflow while preserving no-secrets posture.
- `.planning/api-contracts.md` - Activates `/mcp/** → mcp-server` in the gateway routing table and records the Streamable HTTP timeout posture.
- `.planning/phases/09-mcp-server/09-05-SUMMARY.md` - Captures the plan execution result.

## Verification

- `grep -c "id: mcp-server" config-server/src/main/resources/config/api-gateway.yml` → `1`
- `grep -c '^  mcp-server:' docker-compose.yml` → `1`
- `grep -q "^MCP_API_KEY=$" .env.example` → passed
- `grep -q '/mcp/\*\*' .planning/api-contracts.md && ! grep -E '/mcp/\*\*.*public' .planning/api-contracts.md` → passed
- `docker compose config` → passed and rendered `mcp-server`

`docker compose up -d mcp-server` was intentionally not run in this plan because Plan 09-07 owns the live rebuild/smoke sequence after `./gradlew :config-server:jibDockerBuild :mcp-server:jibDockerBuild`. The compose parse gate confirms the new service block is syntactically valid.

## Decisions Made

- **No Springdoc entry for mcp-server:** followed research guidance that MCP is JSON-RPC, not a REST surface.
- **No `/mcp/**` public allowlist:** `/mcp/**` falls through to the existing gateway `anyExchange().authenticated()` rule, so every network MCP request must carry a valid exchanged Bearer JWT.
- **No direct compose port mapping:** mcp-server remains reachable through api-gateway only, preserving the gateway as the sole public edge.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## Known Stubs

- `.env.example:68` — `MCP_API_KEY=` is an intentional empty secret placeholder required by the plan; the real value belongs only in gitignored `.env`.

## Threat Flags

None - the new `/mcp/**` network surface and compose secret handling were already covered by the plan threat model (`T-09-19` through `T-09-22`).

## User Setup Required

None - no external service configuration was required during this plan. The eventual local `.env` value is generated during the identity-service demo seed flow covered by the Phase 9 runbook/smoke plan.

## Next Phase Readiness

- Ready for Plan 09-06/09-07 to document and smoke-test the full MCP route through rebuilt config-server and mcp-server Jib images.
- Plan 09-07 should reference `api-gateway.yml` line 145 for the route and `docker-compose.yml` line 534 for the service block.

## Self-Check: PASSED

- Found all modified/created plan files on disk.
- Found all task commits in git history: `60d7376`, `45fc160`, `bdea048`, `1b6b092`.

---
*Phase: 09-mcp-server*
*Completed: 2026-05-02*
