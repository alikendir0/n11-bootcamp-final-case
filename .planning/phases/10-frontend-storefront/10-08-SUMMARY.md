---
phase: 10-frontend-storefront
plan: "08"
subsystem: frontend
tags: [react, account, orders, addresses, tanstack-query, order-status-timeline, cancel-order]
dependency_graph:
  requires:
    - frontend/src/lib/types.ts (Plan 10-01 — OrderStatus union; extended with named type)
    - frontend/src/api/orderApi.ts (Plan 10-07 — fetchMyOrders, fetchOrder, cancelOrder)
    - frontend/src/api/addressApi.ts (Plan 10-07 — fetchAddresses, addressesQueryKey)
    - frontend/src/api/authApi.ts (Plan 10-03 — fetchMe for lazy /auth/me)
    - frontend/src/components/checkout/NewAddressForm.tsx (Plan 10-07 — reused on /adreslerim)
    - frontend/src/components/checkout/AddressCard.tsx (Plan 10-07 — reused on /adreslerim)
    - frontend/src/lib/routes.ts (Plan 10-01 — ROUTES.ACCOUNT, ORDERS, ORDER_DETAIL, ADDRESSES)
    - frontend/src/store/authStore.ts (Plan 10-01 — user.email, user.fullName)
  provides:
    - frontend/src/lib/orderStatus.ts (computeTimelineStep, getStatusBadge, isCancellable, TIMELINE_STEPS)
    - frontend/src/components/account/AccountSidebar.tsx (3-link nav)
    - frontend/src/components/account/OrderStatusBadge.tsx (inline status pill)
    - frontend/src/components/account/OrderTimeline.tsx (4-step horizontal + cancel banner)
    - frontend/src/components/account/CancelOrderDialog.tsx (destructive confirm modal)
    - frontend/src/pages/AccountHubPage.tsx (/hesabim hub)
    - frontend/src/pages/OrdersPage.tsx (/siparislerim list)
    - frontend/src/pages/OrderDetailPage.tsx (/siparislerim/:orderId detail)
    - frontend/src/pages/AddressesPage.tsx (/adreslerim CRUD floor)
  affects:
    - FE-10 (account hub, orders list, order detail, address book — closed)
    - FE-13 (all copy Turkish — closed)
    - FE-15 (skeleton loaders on orders list + detail)
    - LOC-04 (formatTRDate used in OrdersPage and OrderDetailPage — closed)
    - LOC-05 (formatTRDate used throughout — closed)
    - AUTH-08 (address book CRUD floor — closed)
    - ORD-03 (orders list sorted desc by date — closed)
    - ORD-04 (status timeline mapped from saga state — closed)
tech_stack:
  added: []
  patterns:
    - "Single orderStatus.ts source of truth: computeTimelineStep + getStatusBadge consumed by OrderTimeline + OrderStatusBadge"
    - "Lazy /auth/me fetch in AccountHubPage — enabled only when user.fullName missing (JWT has no fullName claim)"
    - "Cancel mutation: useMutation + invalidateQueries on both orderQueryKey + myOrdersQueryKey"
    - "exactOptionalPropertyTypes: spread prop pattern to pass optional cancelReason only when defined"
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
  modified:
    - frontend/src/lib/types.ts (extracted OrderStatus as named type alias)
    - frontend/src/router.tsx (replaced all 4 account PlaceholderPages; removed PlaceholderPage import)
decisions:
  - "OrderStatus extracted as named type alias from Order.status inline union — needed by orderStatus.ts + component props"
  - "Teslim Edildi step 4 is static greyed placeholder — out of v1 scope per CONTEXT.md (no real shipping carrier)"
  - "AddressesPage ships CRUD floor (list + create only) — edit/delete deferred to v2 per CONTEXT.md deferred section"
  - "cancelReason spread pattern for exactOptionalPropertyTypes compatibility (same pattern as Plan 10-07 NewAddressForm fix)"
metrics:
  duration: "~6 minutes"
  completed: "2026-05-01"
  tasks_completed: 3
  files_created: 9
  files_modified: 2
---

# Phase 10 Plan 08: Account Hub, Orders, Addresses Summary

Authenticated account section at `/hesabim`, `/siparislerim`, `/siparislerim/:orderId`, and `/adreslerim` — closing FE-10, AUTH-08, ORD-03, ORD-04 with saga-state-to-Turkish-label mapping locked in a single shared module.

## What Was Built

### Task 1: Order status module + account primitives (commit 293648a)

**`frontend/src/lib/orderStatus.ts`**
- `TIMELINE_STEPS`: 4-step array ("Sipariş Alındı" → "Hazırlanıyor" → "Kargoya Verildi" → "Teslim Edildi")
- `computeTimelineStep(status)`: maps PENDING/STOCK_RESERVED → step 0; PAID → step 1; CONFIRMED → step 2; STOCK_FAILED/PAYMENT_FAILED/CANCELLED → `{ activeStepIndex: -1, isCancelled: true }`
- `getStatusBadge(status)`: PENDING/STOCK_RESERVED → gray; PAID → blue; CONFIRMED → green; FAILED/CANCELLED → red
- `isCancellable(status)`: only PENDING

**`frontend/src/components/account/AccountSidebar.tsx`**
- NavLink-based sidebar with Hesabım / Siparişlerim / Adreslerim; active-state highlighted with `font-bold bg-gray-100`

**`frontend/src/components/account/OrderStatusBadge.tsx`**
- Inline pill consuming `getStatusBadge` mapping

**`frontend/src/components/account/OrderTimeline.tsx`**
- 4-step horizontal `<ol>` indicator; isCancelled → full-width red "İptal Edildi" banner with optional cancelReason

### Task 2: AccountHubPage + OrdersPage (commit a5a492b)

**`frontend/src/pages/AccountHubPage.tsx`**
- Two-column layout (240px sidebar + content)
- "Hoş geldin, {displayName}" heading; displayName from lazy `/auth/me` or JWT email
- Stat cards: Toplam Sipariş (fetchMyOrders count) + Kayıtlı Adres (fetchAddresses count)
- Loading state shows "..." in stat value

**`frontend/src/pages/OrdersPage.tsx`**
- Lists orders sorted desc by createdAt (defensive re-sort; backend already returns desc)
- Each order card: order ID (8-char prefix + ellipsis), date via formatTRDate, total via formatTRY, OrderStatusBadge, "Detay →" link
- Empty state: "Henüz siparişiniz yok." + "Alışverişe Başla" CTA
- Loading: 3-row animate-pulse skeleton; error: inline "Tekrar Dene" button

### Task 3: OrderDetailPage + CancelOrderDialog + AddressesPage (commit e4a89a0)

**`frontend/src/components/account/CancelOrderDialog.tsx`**
- Modal with `role="dialog" aria-modal="true"`
- Copy verbatim: "Siparişinizi iptal etmek istediğinizden emin misiniz?"
- "Vazgeç" (secondary outline) + "Evet, İptal Et" (`bg-[#DC2626]`)
- `pending` prop disables both buttons and shows "İptal ediliyor..."

**`frontend/src/pages/OrderDetailPage.tsx`**
- 4 sections: Sipariş Durumu (OrderTimeline) + Ürünler (items table with qty × price) + Teslimat Adresi + Ödeme Yöntemi
- Cancel CTA visible only when `isCancellable(order.status)` (PENDING only)
- cancelOrder mutation → invalidateQueries(orderQueryKey) + invalidateQueries(myOrdersQueryKey) + toast.success
- 404 guard: `error.status === 404 → <NotFoundPage />`
- DetailSkeleton for loading state

**`frontend/src/pages/AddressesPage.tsx`**
- CRUD floor: list card grid + "Yeni Adres Ekle" CTA → inline NewAddressForm
- Reuses AddressCard from checkout (display-only, non-selectable)
- Empty state: "Henüz kayıtlı adresiniz yok." + CTA
- Edit/delete deferred to v2 per CONTEXT.md

**`frontend/src/router.tsx`**
- All 4 PlaceholderPage references for account routes replaced with real pages
- PlaceholderPage import removed (unused)

## Saga-State → Display Step Mapping (Verified)

| Saga State | Timeline Step | Badge Label | Color |
|-----------|--------------|-------------|-------|
| PENDING | Step 1: "Sipariş Alındı" | "Onay Bekliyor" | gray |
| STOCK_RESERVED | Step 1: "Sipariş Alındı" | "Onay Bekliyor" | gray |
| PAID | Step 2: "Hazırlanıyor" | "Hazırlanıyor" | blue |
| CONFIRMED | Step 3: "Kargoya Verildi" | "Onaylandı" | green |
| STOCK_FAILED | İptal Edildi banner | "Stok Yetersiz" | red |
| PAYMENT_FAILED | İptal Edildi banner | "Ödeme Başarısız" | red |
| CANCELLED | İptal Edildi banner | "İptal Edildi" | red |

"Teslim Edildi" (step 4) is always greyed out — no real shipping carrier in v1; intentional per CONTEXT.md deferred section.

## Cancel Flow Tested (Component Level)

1. Order in PENDING state → "Siparişi İptal Et" button visible
2. Click → `showCancelDialog = true` → CancelOrderDialog renders
3. "Evet, İptal Et" → `cancelMutation.mutate()` → POST /orders/{id}/cancel
4. onSuccess: `qc.invalidateQueries(orderQueryKey)` + `qc.invalidateQueries(myOrdersQueryKey)` + `toast.success('Sipariş iptal edildi.')`
5. "Vazgeç" → dialog closes without mutation

## Address Book CRUD Floor Confirmed

`AddressesPage` ships list + create only. Edit/delete CTAs are explicitly deferred to v2 per CONTEXT.md Deferred section: "Address book CRUD polish (set-default, delete, edit) on /adreslerim — v1 ships CRUD floor; UX polish is v2."

## Final Router State

All Phase 10 routes now render real components. No PlaceholderPage references remain in router.tsx.

| Route | Component |
|-------|-----------|
| `/hesabim` | AccountHubPage |
| `/siparislerim` | OrdersPage |
| `/siparislerim/:orderId` | OrderDetailPage |
| `/adreslerim` | AddressesPage |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing] OrderStatus named type not exported from types.ts**
- **Found during:** Task 1 npm run build
- **Issue:** `types.ts` inlined the OrderStatus union inside `Order.status` — no named `OrderStatus` type was exported. `orderStatus.ts` and component props needed the named type.
- **Fix:** Extracted `export type OrderStatus = 'PENDING' | 'STOCK_RESERVED' | ...` above the `Order` interface; `Order.status` now uses `status: OrderStatus`.
- **Files modified:** `frontend/src/lib/types.ts`

**2. [Rule 1 - Bug] TypeScript exhaustive switch needs default branches**
- **Found during:** Task 1 npm run build
- **Issue:** `tsc` reported `Function lacks ending return statement` for `computeTimelineStep` and `getStatusBadge` even with all enum members covered — strict mode requires explicit `default`.
- **Fix:** Added `default` branches returning safe fallback values.
- **Files modified:** `frontend/src/lib/orderStatus.ts`

**3. [Rule 3 - Blocking] Worktree missing node_modules**
- **Found during:** Task 1 build attempt
- **Issue:** Worktree had no `node_modules/` — same issue as Plan 10-07 deviation #5.
- **Fix:** `npm install` inside worktree `frontend/`
- **Files modified:** `frontend/package-lock.json`

**4. [Rule 1 - Bug] exactOptionalPropertyTypes: cancelReason prop**
- **Found during:** Task 3 npm run build
- **Issue:** `<OrderTimeline cancelReason={order.cancelReason} />` where `order.cancelReason?: string` (union with null) failed `exactOptionalPropertyTypes` — `string | undefined` is not assignable to `string`.
- **Fix:** Spread pattern: `{...(order.cancelReason ? { cancelReason: order.cancelReason } : {})}` — passes prop only when defined.
- **Files modified:** `frontend/src/pages/OrderDetailPage.tsx`

**5. [Rule 1 - Bug] Unused PlaceholderPage import in router.tsx**
- **Found during:** Task 3 npm run build (tsc error TS6133)
- **Issue:** After replacing all 4 account placeholders, `PlaceholderPage` import became unused — TypeScript strict mode errors on unused imports.
- **Fix:** Removed the `PlaceholderPage` import line.
- **Files modified:** `frontend/src/router.tsx`

## Known Stubs

None. All four account pages are fully wired to real backend APIs. "Teslim Edildi" is a static greyed step in the timeline — this is an intentional v1 design decision documented in CONTEXT.md (no real shipping carrier; step 4 placeholder is the correct UX behavior for v1).

## Threat Flags

No new threat surfaces beyond the plan's `<threat_model>`:
- T-10-27 (orderId tampering in URL): mitigated — backend GET /orders/{id} enforces ownership; 404 → NotFoundPage guard on frontend
- T-10-28 (cancel repudiation): accepted — backend records cancel_reason; v1 grading doesn't require client audit

## Self-Check: PASSED
