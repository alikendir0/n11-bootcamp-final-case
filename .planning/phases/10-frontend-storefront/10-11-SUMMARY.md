---
phase: 10-frontend-storefront
plan: "11"
subsystem: frontend
tags: [react, account, orders, addresses, order-status-timeline, gap-closure, regression-tests]
dependency_graph:
  requires:
    - frontend/src/lib/types.ts (Plan 10-01 — Order type; modified here to extract OrderStatus)
    - frontend/src/api/orderApi.ts (Plan 10-07 — fetchMyOrders, fetchOrder, cancelOrder)
    - frontend/src/api/addressApi.ts (Plan 10-07 — fetchAddresses, addressesQueryKey)
    - frontend/src/api/authApi.ts (Plan 10-03 — fetchMe)
    - frontend/src/components/checkout/NewAddressForm.tsx (Plan 10-07 — reused on /adreslerim)
    - frontend/src/components/checkout/AddressCard.tsx (Plan 10-07 — reused on /adreslerim)
    - frontend/src/lib/routes.ts (Plan 10-01 — ROUTES.ACCOUNT, ORDERS, ORDER_DETAIL, ADDRESSES)
    - frontend/src/components/feedback/ErrorBoundary.tsx (Plan 10-10 — RouteErrorFallback preserved in router.tsx)
  provides:
    - frontend/src/lib/orderStatus.ts (computeTimelineStep, getStatusBadge, isCancellable, TIMELINE_STEPS)
    - frontend/src/components/account/AccountSidebar.tsx (3-link nav with active state)
    - frontend/src/components/account/OrderStatusBadge.tsx (inline Turkish status pill)
    - frontend/src/components/account/OrderTimeline.tsx (4-step horizontal + İptal Edildi banner)
    - frontend/src/components/account/CancelOrderDialog.tsx (destructive confirmation modal)
    - frontend/src/pages/AccountHubPage.tsx (/hesabim hub with greeting + stat cards)
    - frontend/src/pages/OrdersPage.tsx (/siparislerim date-desc order list)
    - frontend/src/pages/OrderDetailPage.tsx (/siparislerim/:orderId detail + cancel flow)
    - frontend/src/pages/AddressesPage.tsx (/adreslerim CRUD floor)
    - frontend/src/lib/orderStatus.test.ts (23 regression tests for saga→display contract)
    - frontend/src/components/account/OrderStatusBadge.test.tsx (7 badge label tests)
    - frontend/src/components/account/OrderTimeline.test.tsx (7 timeline + cancelled banner tests)
  affects:
    - FE-10 (account hub, orders list, order detail, address book — CLOSED)
    - FE-13 (all copy Turkish — CLOSED for account section)
    - FE-15 (skeleton loaders on orders list + detail — CLOSED)
    - LOC-04 (formatTRDate in OrdersPage + OrderDetailPage — CLOSED)
    - LOC-05 (Turkish date formatting — CLOSED)
    - AUTH-08 (address book CRUD floor — CLOSED)
    - ORD-03 (orders list sorted desc by date — CLOSED)
    - ORD-04 (status timeline mapped from saga state — CLOSED)
tech_stack:
  added: []
  patterns:
    - "Gap-closure recovery: git checkout <stranded-branch> -- <file> for brand-new files; surgical Edit-tool patches for evolved files"
    - "OrderStatus extracted as top-level named type alias — enables typed import by orderStatus.ts and component props"
    - "Single orderStatus.ts source of truth: computeTimelineStep + getStatusBadge consumed by OrderTimeline + OrderStatusBadge"
    - "Regression tests pin the saga→display contract: future regressions fail CI before reaching UAT"
    - "AddressesPage CRUD floor: list + create only; edit/delete deferred to v2 per CONTEXT.md"
key_files:
  created:
    - frontend/src/lib/orderStatus.ts
    - frontend/src/components/account/AccountSidebar.tsx
    - frontend/src/components/account/OrderStatusBadge.tsx
    - frontend/src/components/account/OrderTimeline.tsx
    - frontend/src/components/account/CancelOrderDialog.tsx
    - frontend/src/pages/AccountHubPage.tsx
    - frontend/src/pages/OrdersPage.tsx
    - frontend/src/pages/OrderDetailPage.tsx
    - frontend/src/pages/AddressesPage.tsx
    - frontend/src/lib/orderStatus.test.ts
    - frontend/src/components/account/OrderStatusBadge.test.tsx
    - frontend/src/components/account/OrderTimeline.test.tsx
  modified:
    - frontend/src/lib/types.ts (extracted OrderStatus as named type alias; 10-10 types preserved)
    - frontend/src/router.tsx (wired 4 account pages; removed PlaceholderPage import; 10-10 errorElement preserved)
decisions:
  - "Recovery strategy: git checkout for 9 brand-new files; surgical Edit patches for types.ts + router.tsx to avoid reverting 10-10's BackendProductSummaryDto / BackendProductPage / RouteErrorFallback"
  - "Regression tests added (not in original 10-08): orderStatus.test.ts / OrderStatusBadge.test.tsx / OrderTimeline.test.tsx pin the saga→display contract for CI"
  - "OrderStatus extracted as named type alias (preserves 10-10 compatibility while enabling typed import)"
metrics:
  duration: "~12 minutes"
  completed: "2026-05-01"
  tasks_completed: 4
  files_created: 12
  files_modified: 2
---

# Phase 10 Plan 11: FE-10 Gap Closure — Account Section Recovery Summary

Recovered the FE-10 account section from stranded worktree `worktree-agent-ac0db2df7b7bfdd05` and wired it onto master, adding 3 regression tests that pin the saga-state→Turkish-display contract — closing all 8 requirements originally targeted by Plan 10-08.

## What Was Built

### Task 1: 9 brand-new account/orderStatus files restored (commit 0cafc67)

Files recovered from stranded branch commits `293648a` / `a5a492b` / `e4a89a0` via `git checkout worktree-agent-ac0db2df7b7bfdd05 -- <file>`:

| File | Source Commit |
|------|--------------|
| `frontend/src/lib/orderStatus.ts` | 293648a |
| `frontend/src/components/account/AccountSidebar.tsx` | 293648a |
| `frontend/src/components/account/OrderStatusBadge.tsx` | 293648a |
| `frontend/src/components/account/OrderTimeline.tsx` | 293648a |
| `frontend/src/pages/AccountHubPage.tsx` | a5a492b |
| `frontend/src/pages/OrdersPage.tsx` | a5a492b |
| `frontend/src/components/account/CancelOrderDialog.tsx` | e4a89a0 |
| `frontend/src/pages/OrderDetailPage.tsx` | e4a89a0 |
| `frontend/src/pages/AddressesPage.tsx` | e4a89a0 |

None of these files existed on master before this task — zero merge conflict risk.

`orderStatus.ts` exports:
- `TIMELINE_STEPS` — `['Sipariş Alındı', 'Hazırlanıyor', 'Kargoya Verildi', 'Teslim Edildi']`
- `computeTimelineStep(status)` — PENDING/STOCK_RESERVED → step 0; PAID → step 1; CONFIRMED → step 2; FAILED/CANCELLED → `{activeStepIndex: -1, isCancelled: true}`
- `getStatusBadge(status)` — Turkish label + Tailwind classes per saga state
- `isCancellable(status)` — only PENDING returns true

### Task 2: Surgical hand-patch to frontend/src/lib/types.ts (commit 0e89ad1)

Applied Edit-tool patch (not Write — to preserve 10-10 additions):
- Extracted `export type OrderStatus = 'PENDING' | 'STOCK_RESERVED' | ...` as top-level named alias
- Changed `Order.status` from inline union to `status: OrderStatus`
- **10-10's `BackendProductSummaryDto` and `BackendProductPage` interfaces are unchanged and confirmed present**

Verified: `grep -c "^export interface BackendProductSummaryDto" types.ts` → 1, `grep -c "^export interface BackendProductPage" types.ts` → 1.

### Task 3: Surgical hand-patch to frontend/src/router.tsx (commit 8a50d05)

Three separate Edit-tool operations (not Write):
1. Removed `import PlaceholderPage from './pages/PlaceholderPage'`
2. Added 4 account-page imports below `CheckoutResultPage`
3. Replaced all 4 PlaceholderPage route elements with `<AccountHubPage />`, `<OrdersPage />`, `<OrderDetailPage />`, `<AddressesPage />`

**10-10's contract preserved:**
- `import { RouteErrorFallback } from './components/feedback/ErrorBoundary'` — present (grep count: 1)
- `errorElement: <RouteErrorFallback />` on root route — present (grep count: 1)

`grep -c "PlaceholderPage" router.tsx` → 0 (all references removed).

### Task 4: Regression tests + aggregate gates (commit 652bae6)

Three new test files covering the saga→display contract:

**`orderStatus.test.ts` — 23 tests across 4 describe blocks:**
- TIMELINE_STEPS: verifies 4 Turkish labels in order
- computeTimelineStep: each of 7 OrderStatus values with expected step + isCancelled
- getStatusBadge: each of 7 values with Turkish label; red classes for failure states
- isCancellable: true only for PENDING; false for 6 remaining values

**`OrderStatusBadge.test.tsx` — 7 tests:**
- All 7 OrderStatus values rendered; Turkish badge label text asserted via `screen.getByText`

**`OrderTimeline.test.tsx` — 7 tests:**
- Active timeline (PAID): all 4 step labels in DOM
- Step-indicator aria-current for PENDING (step 1) and CONFIRMED (step 3)
- İptal Edildi banner replaces timeline for CANCELLED, STOCK_FAILED, PAYMENT_FAILED
- cancelReason text appears inside cancelled banner

## Aggregate Gate Results

| Gate | Result | Summary line |
|------|--------|-------------|
| `npm --prefix frontend test` | PASS | 10 files / 76 tests passed (was 39 before this plan) |
| `npm --prefix frontend run lint:invariants` | PASS | All 11 frontend invariants OK |
| `npm --prefix frontend run build` | PASS | Built in 500ms; chunk-size warning expected/non-blocking |

## Untouched Files (Confirmed Not Modified)

The following files were NOT touched by this plan (per the plan's explicit prohibition):
- `frontend/src/api/productApi.ts` — 10-10 delivery, unchanged
- `frontend/src/components/listing/ProductCard.tsx` — 10-09/10-10 delivery, unchanged
- `frontend/src/components/feedback/ErrorBoundary.tsx` — 10-10 delivery, unchanged
- `frontend/src/pages/RegisterPage.tsx` — 10-10 delivery, unchanged
- `frontend/package.json` — unchanged
- `frontend/package-lock.json` — unchanged (npm install ran in worktree only for build/test execution)
- `frontend/scripts/check-frontend-invariants.sh` — unchanged
- `frontend/playwright.config.ts` — unchanged
- `frontend/vitest.config.ts` — unchanged
- `frontend/e2e/*` — unchanged

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Worktree had no node_modules**
- **Found during:** Task 4 (before running test/lint/build gates)
- **Issue:** Worktree created fresh — same pattern as Plan 10-07/10-08 deviations
- **Fix:** `npm --prefix frontend install` before running gates; package.json and package-lock.json not modified
- **Commit:** No separate commit — setup step, not code change

### No Other Deviations

The plan executed exactly as specified. All 3 Edit-tool patches applied cleanly. All 9 git checkout operations succeeded. The stranded branch `worktree-agent-ac0db2df7b7bfdd05` was reachable and contained the expected 3 commits.

## Verifier Re-check Readiness

After this plan lands on master, the next verifier pass is expected to flip:

| Observable Truth | Before | Expected After |
|-----------------|--------|---------------|
| #4: "User can register / log in, view Siparişlerim with status timeline, and manage their address book." | ✗ FAILED | ✓ VERIFIED |
| Score | 4/5 | 5/5 |

Specific verifier checks that will now pass:
- `gsd-sdk query verify.artifacts 10-08-PLAN.md` → 4/4 (all account files present)
- `gsd-sdk query verify.key-links 10-08-PLAN.md` → 2/2 (fetchOrder/cancelOrder wired; computeTimelineStep wired)
- `grep -c "PlaceholderPage" router.tsx` → 0
- `grep -c "^export type OrderStatus" types.ts` → 1
- `npm --prefix frontend test` → 76 tests passed

## Threat Flags

No new security surfaces introduced. All threat mitigations from the plan's `<threat_model>` are present:
- T-10G2-01 (foreign orderId): `OrderDetailPage` guards `error.status === 404 → <NotFoundPage />`
- T-10G2-03 (non-PENDING cancel): `isCancellable(status)` gates the CTA; backend independently rejects
- T-10G2-04 (cancel error leaks): `onError` reads only `err.problem?.detail` with safe fallback "Sipariş iptal edilemedi."
- T-10G2-07 (misclick cancel): `CancelOrderDialog` two-button modal with "Vazgeç" + "Evet, İptal Et"

## Known Stubs

None. All four account routes render fully wired components consuming real backend APIs through TanStack Query. "Teslim Edildi" step 4 in the timeline is intentionally greyed — there is no real shipping carrier in v1 (CONTEXT.md deferred, correct UX behavior).

## Self-Check: PASSED
