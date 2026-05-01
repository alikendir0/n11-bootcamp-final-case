---
phase: 10-frontend-storefront
verified: 2026-05-01T17:20:00Z
status: human_needed
score: 5/5 must-haves verified
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 4/5
  gaps_closed:
    - "User can register / log in, view \"Siparişlerim\" with status timeline, and manage their address book."
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Visual smoke of storefront pages on http://localhost:8083"
    expected: "Header/footer/home/listing/PDP/cart/checkout/auth/account pages match the Turkish UI contract without English flashes or layout regressions."
    why_human: "Visual fidelity, sticky-header scroll behavior, 3-4 slide hero carousel, category nav, and account sidebar layout cannot be fully proven by static code checks."
  - test: "Seed-backed demo flow after adding visible catalog seed data"
    expected: "Search/category navigation can reach a visible product; user can reach PDP, add to cart, login, checkout, and reach Iyzico hand-off or /odeme/sonuc."
    why_human: "Full browser flow depends on live stack/seed data, auth, and hosted payment interaction."
  - test: "Account section live flow — /hesabim, /siparislerim, /siparislerim/:orderId, /adreslerim"
    expected: "/hesabim renders greeting + Toplam Sipariş + Kayıtlı Adres stat cards via real TanStack Query calls; /siparislerim renders order list sorted date-desc with Turkish status badges + Detay links; /siparislerim/:orderId renders OrderTimeline + items + address + payment + Siparişi İptal Et CTA for PENDING orders only; /adreslerim renders address grid + Yeni Adres Ekle inline form."
    why_human: "Live account section requires authenticated session with real order/address data in backend."
  - test: "Cancel order flow from /siparislerim/:orderId for a PENDING order"
    expected: "Clicking Siparişi İptal Et opens CancelOrderDialog (Vazgeç + Evet, İptal Et buttons), confirming posts to /api/v1/orders/{id}/cancel, order status badge updates to İptal Edildi and timeline switches to red banner."
    why_human: "Requires live backend saga + authenticated PENDING order; mutation side-effect visible only in running browser."
  - test: "Sandbox Iyzico completion"
    expected: "Hosted payment completes and redirects back to /odeme/sonuc?orderId=<id>, which renders confirmed/failure/timeout state as appropriate."
    why_human: "External Iyzico hosted-page behavior requires live sandbox credentials and browser interaction."
---

# Phase 10: Frontend Storefront Verification Report

**Phase Goal:** Build the Turkish React storefront — header / footer / hero / category nav / paginated listing / PDP / cart / multi-step checkout / account section / login+register — all in Turkish per the Phase 2 recon report, all calling the backend through the gateway only, with KDV-inclusive prices, taksit preview, and Türkiye locale conventions throughout.
**Verified:** 2026-05-01T17:20:00Z
**Status:** human_needed
**Re-verification:** Yes — after gap-closure plan 10-11 (FE-10 account section recovery)

## Goal Achievement

Plan 10-11 successfully closed the single remaining gap from the previous verification: the FE-10 account section. All four account routes now render real, substantive components (not PlaceholderPage). The router is correctly wired, orderStatus.ts covers all 7 saga states, and 37 new regression tests lock the saga-to-display contract. All five roadmap success criteria are verified at the code level. The remaining open items are visual/live-flow verifications that cannot be completed by static analysis.

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User lands on the homepage and sees a sticky header (logo / search / account+cart with item-count badge), a 3-4-slide hero carousel, a "Çok Satanlar" rail, and a footer with help links + payment-method icons — every visible string in Turkish, no English flashes on first paint. | ✓ VERIFIED | `HomePage.tsx` renders `HeroCarousel` and `ProductRail heading="Yeni Gelenler"`; `Header.tsx` contains sticky layout, search bar with placeholder "Aradığınız ürün, kategori veya markayı yazınız", cart badge; all invariants pass; build exits 0. |
| 2 | User can paginate the listing, open a PDP with gallery/tabs/KDV-inclusive price/taksit/free-shipping/stock/"Sepete Ekle" CTA, and add to cart. | ✓ VERIFIED | `ListingGrid.tsx` fetches via `fetchProducts`, renders skeleton/error/empty/grid/pagination with Turkish labels; `FreeShippingBadge.tsx` + `lib/freeShipping.ts` implement "Kargo Bedava" threshold; `lib/taksit.ts` computes installment tiers; 5 taksit tests pass; invariant gate confirmed no hardcoded service URLs. |
| 3 | User can complete a multi-step checkout; cart shows line items + KDV breakdown + "Siparişi Tamamla" CTA; prices use `Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' })`; dates render `28 Nisan 2026`. | ✓ VERIFIED | `lib/format.ts` exports `formatTRY` (`Intl.NumberFormat('tr-TR', { currency: 'TRY' })`) and `formatTRDate` (`Intl.DateTimeFormat('tr-TR', { day: 'numeric', month: 'long', year: 'numeric' })`); invariant #4/#5 confirm these are the sole formatters; 7 format tests pass; checkout idempotency-key invariant #10 passes. |
| 4 | User can register / log in (Turkish validation messages), view "Siparişlerim" with status timeline, and manage their address book. | ✓ VERIFIED | `router.tsx` imports and renders `AccountHubPage`, `OrdersPage`, `OrderDetailPage`, `AddressesPage` — 0 PlaceholderPage references remain; `orderStatus.ts` maps all 7 OrderStatus values to Turkish labels/steps; `OrderTimeline.tsx` renders 4-step horizontal timeline + İptal Edildi banner; `OrderDetailPage.tsx` wires `fetchOrder`, `cancelOrder`, `isCancellable`; 37 regression tests across 3 files cover the saga→display contract; all 76 tests pass. |
| 5 | Frontend hits backend exclusively via api-gateway base URL; skeleton loaders/error toasts exist; React Query cache + 401 auth injection survive retry. | ✓ VERIFIED | `apiClient.ts` builds `${VITE_API_BASE_URL}/api/v1${path}`, attaches Bearer token, clears token and dispatches `auth:unauthorized` on 401; invariant #1 confirmed no hardcoded service URLs in `src/`; invariant #2 confirmed `n11_auth_token` localStorage key centralized in `tokenStore.ts`; `RouteErrorFallback` from `ErrorBoundary.tsx` on root route renders Turkish error page. |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `frontend/src/lib/orderStatus.ts` | Saga-state → Turkish timeline/badge mapping (computeTimelineStep, getStatusBadge, isCancellable, TIMELINE_STEPS) | ✓ VERIFIED | 70 lines; exports 4 public symbols; handles all 7 OrderStatus values across 2 switch statements; 4 Turkish step labels in TIMELINE_STEPS; 23 unit tests pass. |
| `frontend/src/components/account/AccountSidebar.tsx` | 3-link nav (Hesabım / Siparişlerim / Adreslerim) with active-state highlight | ✓ VERIFIED | NavLink with `isActive` class toggling; uses ROUTES constants; 35 lines. |
| `frontend/src/components/account/OrderStatusBadge.tsx` | Inline Turkish status pill consuming getStatusBadge | ✓ VERIFIED | Delegates to `getStatusBadge(status)`; renders `label` + `classes`; 7 badge label tests pass. |
| `frontend/src/components/account/OrderTimeline.tsx` | Four-step horizontal timeline + İptal Edildi cancelled banner | ✓ VERIFIED | Renders `TIMELINE_STEPS` with `aria-current="step"` on active step; İptal Edildi banner for cancelled states with optional cancelReason; 7 timeline tests pass. |
| `frontend/src/components/account/CancelOrderDialog.tsx` | Destructive confirmation modal (Vazgeç + Evet, İptal Et) | ✓ VERIFIED | `role="dialog"` modal with disabled state during pending mutation; Turkish copy verbatim ("Siparişinizi iptal etmek istediğinizden emin misiniz?"). |
| `frontend/src/pages/AccountHubPage.tsx` | /hesabim hub with greeting + stat cards via real queries | ✓ VERIFIED | Fetches `fetchMe` (lazy), `fetchMyOrders`, `fetchAddresses` via TanStack Query; renders Toplam Sipariş + Kayıtlı Adres stat cards; 62 lines substantive. |
| `frontend/src/pages/OrdersPage.tsx` | /siparislerim order list sorted desc by createdAt with status badges + Detay links | ✓ VERIFIED | Fetches `fetchMyOrders`; defensive `.slice().sort()` by `createdAt` desc; renders `OrderStatusBadge` + `Link` to `ROUTES.ORDER_DETAIL(order.id)`; `formatTRY` + `formatTRDate` used; skeleton loader present. |
| `frontend/src/pages/OrderDetailPage.tsx` | /siparislerim/:orderId detail + timeline + cancel flow | ✓ VERIFIED | Wires `fetchOrder`, `cancelOrder`, `isCancellable`; renders `OrderTimeline` + items section + address section + payment section; 404 guard via `ApiError.status === 404 → NotFoundPage`; cancel error reads only `err.problem?.detail`; 135 lines. |
| `frontend/src/pages/AddressesPage.tsx` | /adreslerim address book (list + create) | ✓ VERIFIED | Fetches `fetchAddresses`; reuses `NewAddressForm` + `AddressCard` from checkout; shows/hides inline form; 66 lines. |
| `frontend/src/lib/orderStatus.test.ts` | Regression coverage for all 7 OrderStatus values across computeTimelineStep, getStatusBadge, isCancellable, TIMELINE_STEPS | ✓ VERIFIED | 23 tests across 4 describe blocks; every OrderStatus value covered; all pass. |
| `frontend/src/components/account/OrderStatusBadge.test.tsx` | Regression coverage for Turkish badge labels per saga state | ✓ VERIFIED | 7 tests; all 7 OrderStatus values covered via `screen.getByText`; all pass. |
| `frontend/src/components/account/OrderTimeline.test.tsx` | Regression coverage for step index + cancelled-banner rendering | ✓ VERIFIED | 7 tests; active-step aria-current for PENDING/CONFIRMED; İptal Edildi banner for CANCELLED/STOCK_FAILED/PAYMENT_FAILED; cancelReason prop rendering; all pass. |
| `frontend/src/lib/types.ts` | Named OrderStatus type alias + BackendProductSummaryDto + BackendProductPage | ✓ VERIFIED | `export type OrderStatus = ...` at line 102; `export interface BackendProductSummaryDto` at line 43; `export interface BackendProductPage` at line 54; `Order.status: OrderStatus` (not inline union). |
| `frontend/src/router.tsx` | 4 account imports + 4 real route elements + RouteErrorFallback preserved | ✓ VERIFIED | 0 PlaceholderPage references; 4 account-page imports; `errorElement: <RouteErrorFallback />`; `<AccountHubPage />`, `<OrdersPage />`, `<OrderDetailPage />`, `<AddressesPage />` on RequireAuth children. |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| `frontend/src/router.tsx` | `frontend/src/pages/AccountHubPage.tsx` | auth-required route element renders `<AccountHubPage />` instead of PlaceholderPage | ✓ WIRED | grep confirms 0 PlaceholderPage + 4 real account components in router. |
| `frontend/src/pages/OrderDetailPage.tsx` | `frontend/src/api/orderApi.ts` | `fetchOrder + cancelOrder + orderQueryKey + myOrdersQueryKey` | ✓ WIRED | `OrderDetailPage` imports and calls `fetchOrder` (query) + `cancelOrder` (mutation); `qc.invalidateQueries` on cancel success. |
| `frontend/src/lib/orderStatus.ts` | `frontend/src/components/account/OrderTimeline.tsx` | `computeTimelineStep(status) + TIMELINE_STEPS` | ✓ WIRED | `OrderTimeline.tsx` imports and calls `computeTimelineStep`; iterates `TIMELINE_STEPS`. |
| `frontend/src/components/account/OrderStatusBadge.tsx` | `frontend/src/lib/orderStatus.ts` | `getStatusBadge(status)` | ✓ WIRED | `OrderStatusBadge` calls `getStatusBadge(status)` and renders label + classes. |
| `frontend/src/api/orderApi.ts` | gateway `/api/v1/orders` | `apiFetch` through centralized apiClient | ✓ WIRED | `apiFetch<Order[]>('/orders')`, `apiFetch<Order>('/orders/${orderId}')`, `apiFetch<void>('/orders/${orderId}/cancel')`; all go through `VITE_API_BASE_URL` — invariant #1 confirmed. |
| `frontend/src/api/addressApi.ts` | gateway `/api/v1/identity/addresses` | `apiFetch` through centralized apiClient | ✓ WIRED | `apiFetch<Address[]>('/identity/addresses')` — correct gateway routing. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|---|---|---|---|---|
| `AccountHubPage.tsx` | `ordersQuery.data.length`, `addressesQuery.data.length` | `fetchMyOrders()` → `GET /api/v1/orders`; `fetchAddresses()` → `GET /api/v1/identity/addresses` | Yes — real API calls; loading state rendered as "..." via `isLoading` | ✓ FLOWING |
| `OrdersPage.tsx` | `orders` (sorted slice of `data`) | `fetchMyOrders()` → `GET /api/v1/orders`; status badge from `getStatusBadge(order.status)` | Yes — real API array; defensive sort; empty state handled | ✓ FLOWING |
| `OrderDetailPage.tsx` | `order` | `fetchOrder(orderId)` → `GET /api/v1/orders/${orderId}` | Yes — real API call; 404 guard; timeline/items/address from order object | ✓ FLOWING |
| `AddressesPage.tsx` | `addresses` | `fetchAddresses()` → `GET /api/v1/identity/addresses` | Yes — real API call; empty state + create form | ✓ FLOWING |
| `ProductRail.tsx` | `data.content` | `fetchProducts({ page: 0, size: 10 })` → normalized `BackendProductPage` | Yes — API query through gateway, normalized at productApi boundary | ✓ FLOWING |
| `ListingGrid.tsx` | `data.content`, `data.totalPages` | `fetchProducts(backend, slugToUuid)` category slug → UUID → API | Yes — category slug maps to UUID before fetch | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| All 3 new regression test files | `npm --prefix frontend test -- --run` | 10 files / 76 tests passed | ✓ PASS |
| orderStatus.ts saga→display contract | `npm --prefix frontend test -- --run src/lib/orderStatus.test.ts` | 23 tests passed | ✓ PASS |
| OrderStatusBadge Turkish labels | `npm --prefix frontend test -- --run src/components/account/OrderStatusBadge.test.tsx` | 7 tests passed | ✓ PASS |
| OrderTimeline cancelled banner | `npm --prefix frontend test -- --run src/components/account/OrderTimeline.test.tsx` | 7 tests passed | ✓ PASS |
| Frontend invariant gate | `npm --prefix frontend run lint:invariants` | All 11 frontend invariants OK | ✓ PASS |
| Production build | `npm --prefix frontend run build` | Built in 495ms; chunk-size warning only (non-blocking) | ✓ PASS |
| PlaceholderPage removed from router | `grep -c "PlaceholderPage" frontend/src/router.tsx` | 0 | ✓ PASS |
| Account routes wired to real components | `grep -cE "<(AccountHubPage\|OrdersPage\|OrderDetailPage\|AddressesPage) />" frontend/src/router.tsx` | 4 | ✓ PASS |
| RouteErrorFallback preserved | `grep -c "errorElement: <RouteErrorFallback />" frontend/src/router.tsx` | 1 | ✓ PASS |
| No hardcoded service URLs | Invariant #1 (no VITE_API_BASE_URL bypasses in src/) | OK | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| FE-02 | 10-02 | Header layout — logo, search bar, account+cart with item-count badge; sticky on scroll | ✓ COVERED | `Header.tsx` has sticky layout with logo, search (placeholder Turkish), account + cart badge; invariants pass. |
| FE-03 | 10-02 | Footer with help links and payment-method icon strip | ✓ COVERED | Prior verification evidence; build/invariants pass; Turkish link labels. |
| FE-04 | 10-04 | Hero carousel on homepage (3-4 static slides) | ✓ COVERED | `HomePage.tsx` renders `HeroCarousel`; build passes. |
| FE-05 | 10-04 | "Yeni Gelenler" / bestseller rail on homepage | ✓ COVERED | `ProductRail heading="Yeni Gelenler"` fetches `fetchProducts` normalized from `BackendProductPage`. |
| FE-06 | 10-04/10-10 | Product listing with paginated grid in Turkish | ✓ COVERED | `ListingGrid.tsx` skeleton/error/empty/grid/pagination; 15 listing param tests pass. |
| FE-07 | 10-05 | PDP with image gallery, tabs, KDV price, stock, CTA | ✓ COVERED | `ProductDetailPage.tsx`; `formatTRY` for price; build/invariants pass. |
| FE-08 | 10-06 | Cart page with line items, totals, KDV breakdown, taksit, "Siparişi Tamamla" | ✓ COVERED | `CartPage.tsx`, `CartSummary.tsx` with `qualifiesForFreeShipping`, "Siparişi Tamamla" CTA; idempotency invariant #10 passes. |
| FE-09 | 10-07 | Multi-step checkout (address → Iyzico → confirmation) | ✓ COVERED | `CheckoutAddressPage`, `CheckoutPaymentPage`, `CheckoutResultPage`; Iyzico return path invariant #9 passes. |
| FE-10 | 10-08/10-11 | Account section: Siparişlerim (list + detail + timeline), address book | ✓ COVERED | Router wires `AccountHubPage`, `OrdersPage`, `OrderDetailPage`, `AddressesPage`; `orderStatus.ts` saga→display mapping; 37 regression tests; 0 PlaceholderPage references. |
| FE-11 | 10-03/10-10 | Login/register in Turkish with Turkish form validation | ✓ COVERED | `RegisterPage.tsx` Zod resolver with Turkish password message; backend 400 visible via `errors.root.server`; targeted tests pass. |
| FE-13 | All | All UI copy Turkish | ✓ COVERED | Account section: "Hesabım", "Siparişlerim", "Adreslerim", "Sipariş Alındı", "Hazırlanıyor", "Kargoya Verildi", "Teslim Edildi", "İptal Edildi", "Evet, İptal Et", "Vazgeç", "Onay Bekliyor", "Onaylandı", "Stok Yetersiz", "Ödeme Başarısız", "İptal Edildi", "Siparişi İptal Et", "Toplam Sipariş", "Kayıtlı Adres"; invariant #3 no dangerouslySetInnerHTML. |
| FE-14 | 10-04 | Accessible pagination with Turkish labels and keyboard navigation | ✓ COVERED | `Pagination.tsx` Turkish "Önceki"/"Sonraki" labels; listing tests pass; invariants pass. |
| FE-15 | All | Skeleton loaders on PDP + listing; toast/inline errors on failures | ✓ COVERED | `OrdersPage` has `OrdersSkeleton`; `OrderDetailPage` has `DetailSkeleton`; `sonner` toast in `cancelOrder` onSuccess/onError; skeleton on listing; route error fallback. |
| FE-16 | 10-01+ | Frontend integrates with backend via api-gateway only | ✓ COVERED | `apiClient.ts` centralizes `VITE_API_BASE_URL`; invariant #1 confirmed no hardcoded service URLs; all account API calls use `apiFetch`. |
| LOC-01 | 10-04/05/06 | KDV-inclusive price display everywhere | ✓ COVERED | Backend `price_gross` stored and displayed as-is; `formatTRY` used consistently on PDP, cart, orders list, order detail. |
| LOC-02 | 10-01+ | TRY currency formatting via Intl.NumberFormat('tr-TR', currency: 'TRY') | ✓ COVERED | `format.ts` line 1: `new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' })`; invariant #4 confirms sole formatter; 7 format tests pass. |
| LOC-03 | 10-05 | Taksit preview on PDP (2/3/6/9/12 tiers) | ✓ COVERED | `lib/taksit.ts`; 5 taksit tests pass; prior PDP evidence. |
| LOC-04 | 10-05/10-06 | "Kargo Bedava" badge when price >= FREE_SHIPPING_THRESHOLD | ✓ COVERED | `FreeShippingBadge.tsx` + `lib/freeShipping.ts` threshold check; `CartSummary.tsx` shows "Kargo Bedava" / upsell message; env-configurable `VITE_FREE_SHIPPING_THRESHOLD`. |
| LOC-05 | 10-01/10-08/10-09 | Turkish date formatting (28 Nisan 2026) | ✓ COVERED | `formatTRDate` used in `OrdersPage` + `OrderDetailPage` + `CheckoutResultPage`; `Intl.DateTimeFormat('tr-TR', { day: 'numeric', month: 'long', year: 'numeric' })`; invariant #5 confirms sole formatter. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| `frontend/src/pages/PlaceholderPage.tsx` | 2 | "TBD by next plan" — dead file, no longer imported anywhere | ℹ️ Info | File is unreachable dead code; router no longer imports it; 0 impact on users. No blocking concern. |

### Human Verification Required

#### 1. Visual smoke of storefront pages

**Test:** Open `http://localhost:8083` after `docker compose up` and navigate homepage, category listing, PDP, cart, checkout, auth pages.
**Expected:** Sticky header with logo/search/cart-badge, 3-4 slide hero carousel, "Yeni Gelenler" product rail, footer with help links + payment icons; all strings Turkish; no English flashes on first paint; layout matches n11 recon colors (#1C1C1E primary, correct grid).
**Why human:** Visual fidelity, scroll behavior, carousel animation, and recon-faithfulness cannot be verified by static analysis.

#### 2. Account section live flow

**Test:** Login, navigate to `/hesabim`, `/siparislerim`, `/siparislerim/{id}`, `/adreslerim`.
**Expected:** AccountHubPage renders greeting + stat cards with real data from backend; OrdersPage lists orders sorted by date descending with Turkish status badges and Detay links; OrderDetailPage renders OrderTimeline at correct step for order status, shows items/address/payment, shows "Siparişi İptal Et" CTA only for PENDING orders; AddressesPage shows saved addresses and inline NewAddressForm on "Yeni Adres Ekle".
**Why human:** Live account section requires authenticated session with real orders and addresses in the running backend.

#### 3. Cancel order flow

**Test:** Navigate to a PENDING order in `/siparislerim/:orderId`, click "Siparişi İptal Et", confirm in CancelOrderDialog.
**Expected:** Modal shows "Siparişinizi iptal etmek istediğinizden emin misiniz?" with "Vazgeç" + "Evet, İptal Et" buttons; confirming sends POST to `/api/v1/orders/{id}/cancel`; order status badge updates to "İptal Edildi"; timeline switches to red İptal Edildi banner; toast shows "Sipariş iptal edildi."
**Why human:** Requires live backend saga, authenticated PENDING order, and browser-observable state updates.

#### 4. Seed-backed end-to-end demo flow

**Test:** Ensure backend seed includes visible products; run the full flow: browse category → PDP → "Sepete Ekle" → cart → login → checkout address step → Iyzico Checkout Form → `/odeme/sonuc`.
**Expected:** Every step transitions correctly; Iyzico hand-off succeeds; `/odeme/sonuc` renders appropriate state card based on saga outcome.
**Why human:** Full flow depends on live stack, seed data, auth, and hosted payment behavior.

#### 5. Iyzico sandbox completion

**Test:** Complete hosted sandbox payment with test card and confirm return to `/odeme/sonuc?orderId=<id>`.
**Expected:** Success/failure/timeout card renders according to backend saga state; Polling completes within PaymentStatus retry window.
**Why human:** External Iyzico hosted-page behavior requires live sandbox credentials and browser interaction.

### Gaps Summary

No automated gaps remain. All five roadmap success criteria for Phase 10 are verified in code:

- SC-1 (homepage with sticky header, hero, rail, footer): VERIFIED by component/invariant evidence.
- SC-2 (listing pagination, PDP with all features, add to cart): VERIFIED by component files + taksit/format tests + invariants.
- SC-3 (multi-step checkout, KDV prices, Turkish dates): VERIFIED by checkout pages + format.ts + invariants #4/#5/#9/#10.
- SC-4 (register/login, Siparişlerim with status timeline, address book): VERIFIED by account pages + orderStatus.ts + 76 passing tests + 0 PlaceholderPage references.
- SC-5 (gateway-only, skeletons, errors, 401-retry): VERIFIED by apiClient.ts + 11 invariants all OK.

The only remaining items are live-environment verifications (visual, browser-flow, backend-integration) that are classified as human_needed.

Plan 10-11's recovery work is fully substantiated in the codebase: the stranded branch content is on master, types.ts correctly exports the named `OrderStatus` alias while preserving 10-10's `BackendProductSummaryDto` / `BackendProductPage`, router.tsx has 0 PlaceholderPage references and 4 real account component elements, and 37 new tests pin the saga→display contract for CI.

---

_Verified: 2026-05-01T17:20:00Z_
_Verifier: Claude (gsd-verifier)_
