---
phase: 10-frontend-storefront
plan: "09"
subsystem: frontend-testing
tags: [vitest, playwright, e2e, ci-gates, frontend-invariants, turkish-storefront]
dependency_graph:
  requires:
    - "10-01 through 10-08 frontend storefront implementation"
  provides:
    - "frontend/scripts/check-frontend-invariants.sh — executable 11-invariant grep gate"
    - "frontend/package.json scripts — npm test, test:e2e, lint:invariants"
    - "frontend/playwright.config.ts — CI-friendly Playwright config with env-driven baseURL"
    - "frontend/e2e/demo-flow.spec.ts — demo smoke through Iyzico hand-off"
  affects:
    - "Phase 11 CI/devops pipeline can run npm test + lint:invariants + test:e2e"
tech-stack:
  added:
    - "@playwright/test 1.59.1"
  patterns:
    - "Playwright baseURL defaults to localhost:5173 and is overrideable via PLAYWRIGHT_BASE_URL"
    - "Vitest is scoped to src/**/*.test.{ts,tsx} so e2e specs do not pollute npm test"
    - "Invariant grep gate keeps frontend source aligned with Phase 10 contracts"
key-files:
  created:
    - frontend/scripts/check-frontend-invariants.sh
    - frontend/playwright.config.ts
    - frontend/e2e/demo-flow.spec.ts
    - frontend/e2e/fixtures/seed.ts
  modified:
    - frontend/package.json
    - frontend/package-lock.json
    - frontend/.gitignore
    - frontend/vitest.config.ts
key-decisions:
  - "Keep Playwright webServer disabled; developers/CI start the storefront explicitly to avoid racing the backend stack."
  - "Default Playwright baseURL remains http://localhost:5173 while live validation can use PLAYWRIGHT_BASE_URL=http://localhost:8083."
  - "Scope Vitest discovery to src unit tests so npm test stays the umbrella unit-test command."
requirements-completed: [FE-13, FE-15, FE-16, LOC-02, LOC-03, LOC-05]
metrics:
  duration: "~3 minutes active continuation; prior task commits already present"
  completed: "2026-05-01T12:27:15Z"
  tasks_completed: 3
  files_created: 4
  files_modified: 4
---

# Phase 10 Plan 09: Test Posture Summary

**Vitest unit umbrella, Playwright demo smoke, and executable invariant grep gate for the Turkish storefront.**

## Performance

- **Started:** 2026-05-01T12:23:59Z
- **Completed:** 2026-05-01T12:27:15Z
- **Tasks:** 3/3
- **Files changed:** 8

## What Was Built

### Task 1: Frontend invariants script + executable CI gate

- Added `frontend/scripts/check-frontend-invariants.sh` and made it executable.
- The script checks all 11 Phase 10 invariants:
  1. no hardcoded localhost URLs in `frontend/src`
  2. `n11_auth_token` only in `tokenStore`
  3. no `dangerouslySetInnerHTML`
  4. `Intl.NumberFormat` only in `format.ts`
  5. `Intl.DateTimeFormat` only in `format.ts`
  6. route literals centralized in `routes.ts` / router
  7. CTA primary black token `#1C1C1E`
  8. `RequireAuth` guard present
  9. Iyzico return path `/odeme/sonuc`
  10. `Idempotency-Key` on order POST
  11. no chat-bubble placeholder before Phase 11
- Verification output: `All 11 frontend invariants OK.`

### Task 2: Vitest run-all wiring + verify unit-test count

- `frontend/package.json` scripts now include:
  - `test`: `vitest run`
  - `test:e2e`: `playwright test`
  - `lint:invariants`: `bash scripts/check-frontend-invariants.sh`
- `npm test` now runs only the unit suites under `src/`.
- Final unit test result: **33 passing tests across 4 files**:
  - `tokenStore.test.ts`: 6
  - `format.test.ts`: 7
  - `listingParams.test.ts`: 15
  - `taksit.test.ts`: 5

### Task 3: Playwright smoke E2E covering the demo flow

- Added `frontend/playwright.config.ts` with:
  - `testDir: './e2e'`
  - `baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:5173'`
  - headless CI-friendly defaults, single worker, Turkish locale, Istanbul timezone
  - one Chromium desktop project
- Added env-driven seed fixture in `frontend/e2e/fixtures/seed.ts` (`PW_TEST_EMAIL`, `PW_TEST_PASSWORD`, `PW_TEST_QUERY`).
- Added `frontend/e2e/demo-flow.spec.ts` covering:
  - homepage → `/arama?q=macbook`
  - first `/urun/...` result
  - anonymous `Sepete Ekle` → `/giris-yap?redirectUrl=...`
  - login
  - authenticated add-to-cart + toast
  - `/sepetim`
  - `/odeme/adres`
  - address creation fallback
  - `/odeme/odeme`
  - `Sipariş Ver`
  - Iyzico hand-off or `/odeme/sonuc`
- Playwright artifacts are ignored via `frontend/.gitignore`.

## Task Commits

| Task | Name | Commit | Notes |
|------|------|--------|-------|
| 1 | Frontend invariants script + executable CI gate | `cadcf1b`, `168ddae` | Script created, executable, and cwd-independent |
| 2 | Vitest run-all wiring + CI scripts | `49ef8ba` | `test:e2e` and `lint:invariants` scripts wired |
| 3 | Playwright smoke E2E covering demo flow | `bc5e455` | Playwright config/spec/fixtures + Vitest scoping fix |

## Verification Commands Run

| Command | Result |
|---------|--------|
| `bash frontend/scripts/check-frontend-invariants.sh` | PASS — all 11 OK |
| `npm test` | PASS — 33 tests across 4 files |
| `npm run lint:invariants` | PASS — runs from `frontend/` and all 11 OK |
| `npx playwright test --list` | PASS — lists 1 `demo-flow.spec.ts` test |
| `npm run build` | PASS |
| `PLAYWRIGHT_BASE_URL=http://localhost:8083 npx playwright test` | Expected environment failure: search returned no product; spec failed clearly with `backend not seeded: search returned no visible product result` rather than hanging |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Scoped Vitest to unit tests only**
- **Found during:** Task 3 verification (`npm test` after adding Playwright spec)
- **Issue:** Vitest discovered `frontend/e2e/demo-flow.spec.ts` and attempted to execute Playwright's `test()` API, failing the unit-test umbrella command.
- **Fix:** Added `include: ['src/**/*.test.{ts,tsx}']` to `frontend/vitest.config.ts`.
- **Files modified:** `frontend/vitest.config.ts`
- **Verification:** `npm test` passes with 33 unit tests; `npx playwright test --list` still lists the e2e spec.
- **Committed in:** `bc5e455`

**2. [Rule 3 - Blocking] Committed package-lock dependency entries needed by current frontend tooling**
- **Found during:** Task 3 dependency verification
- **Issue:** `frontend/package-lock.json` had pending dependency entries from frontend tooling installs, and Playwright's dependency entry was required for reproducible CI installs.
- **Fix:** Ran `npm install` in `frontend/` and committed the resulting lockfile entries together with `@playwright/test`.
- **Files modified:** `frontend/package-lock.json`
- **Verification:** `npm test`, `npm run build`, and `npx playwright test --list` all pass.
- **Committed in:** `bc5e455`

**3. [Rule 1 - Bug] Made invariant gate cwd-independent**
- **Found during:** Final verification (`npm run lint:invariants` from `frontend/`)
- **Issue:** The package script runs with `frontend/` as cwd, but the initial script assumed repo-root cwd and looked for `frontend/src` relative to `frontend/`.
- **Fix:** Resolve `SCRIPT_DIR`, `FRONTEND_DIR`, and `FRONTEND_SRC` relative to the script file.
- **Files modified:** `frontend/scripts/check-frontend-invariants.sh`
- **Verification:** Both `bash frontend/scripts/check-frontend-invariants.sh` from repo root and `npm run lint:invariants` from `frontend/` pass.
- **Committed in:** `168ddae`

**Total deviations:** 3 auto-fixed (2 bugs, 1 blocking).  
**Impact on plan:** Both fixes were required to preserve the plan's test posture. No feature scope was added.

## Known Stubs

None in files created/modified by this plan.

## Threat Flags

No new security-relevant runtime surface beyond the plan's threat model. Test credentials are env-driven in `seed.ts`, and no real secrets are committed.

## Phase 11 Handoff

Run the three frontend quality signals locally/CI from `frontend/`:

```bash
npm test
npm run lint:invariants
PLAYWRIGHT_BASE_URL=http://localhost:5173 npm run test:e2e
```

For the user's live dev server on port 8083, use:

```bash
PLAYWRIGHT_BASE_URL=http://localhost:8083 npm run test:e2e
```

The smoke requires a backend catalog seeded with a product matching `PW_TEST_QUERY` (default `macbook`) and a valid seed user (`PW_TEST_EMAIL` / `PW_TEST_PASSWORD`). Without that data, the test fails quickly with a clear seeding message.

## Self-Check: PASSED

- FOUND: `frontend/scripts/check-frontend-invariants.sh`
- FOUND: executable invariant script
- FOUND: `frontend/playwright.config.ts`
- FOUND: `frontend/e2e/demo-flow.spec.ts`
- FOUND: `.planning/phases/10-frontend-storefront/10-09-SUMMARY.md`
- FOUND commits: `cadcf1b`, `49ef8ba`, `bc5e455`, `168ddae`

---
*Phase: 10-frontend-storefront*
*Completed: 2026-05-01*
