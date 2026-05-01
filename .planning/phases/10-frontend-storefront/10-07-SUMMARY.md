---
phase: 10-frontend-storefront
plan: "07"
subsystem: frontend
tags: [react, checkout, iyzico, tanstack-query, zod, react-hook-form, address-book, idempotency-key, saga-polling]
dependency_graph:
  requires:
    - frontend/src/lib/apiClient.ts (Plan 10-01 — apiFetch + ApiError)
    - frontend/src/lib/types.ts (Plan 10-01 — Address, Order, PaymentStatus)
    - frontend/src/lib/routes.ts (Plan 10-01 — ROUTES.CHECKOUT_*)
    - frontend/src/store/checkoutStore.ts (Plan 10-01 — ensureIdempotencyKey + reset)
    - frontend/src/hooks/useCart.ts (Plan 10-02 — cartQueryKey)
    - frontend/src/lib/zodTurkish.ts (Plan 10-03 — global Turkish errorMap)
    - frontend/src/router.tsx (Plans 10-02/06 — RequireAuth + placeholder slots)
  provides:
    - frontend/src/api/addressApi.ts (fetchAddresses, createAddress, addressesQueryKey)
    - frontend/src/api/orderApi.ts (createOrder, fetchOrder, fetchMyOrders, cancelOrder, query keys)
    - frontend/src/api/paymentApi.ts (fetchPaymentForOrder, paymentForOrderQueryKey)
    - frontend/src/components/checkout/CheckoutStepper.tsx (3-step indicator)
    - frontend/src/components/checkout/AddressCard.tsx (radio card for address selection)
    - frontend/src/components/checkout/NewAddressForm.tsx (zod-validated inline address form)
    - frontend/src/pages/CheckoutAddressPage.tsx (/odeme/adres — step 1)
    - frontend/src/pages/CheckoutPaymentPage.tsx (/odeme/odeme — step 2)
    - frontend/src/pages/CheckoutResultPage.tsx (/odeme/sonuc — step 3)
    - frontend/src/router.tsx (/odeme/* routes wired)
  affects:
    - AUTH-08 (address-book picker consumed in checkout)
    - FE-09 (multi-step checkout closed)
    - ORD-01 (POST /orders with Idempotency-Key + addressId + paymentMethod)
    - PAY-04 (Iyzico hosted-page redirect + /odeme/sonuc polling seam)
    - Plan 10-08 (checkout flow usable in demo script)
tech_stack:
  added: []
  patterns:
    - "TanStack Query refetchInterval with queryFn-based stop condition (terminal status detection)"
    - "Per-attempt Idempotency-Key UUID via checkoutStore.ensureIdempotencyKey (reused across retries)"
    - "pollForPaymentPageUrl: 5-retry loop with 1s delay for Phase 6 D-08 saga lag"
    - "window.location.assign(paymentPageUrl): hard redirect to Iyzico hosted page"
    - "30s setTimeout + timedOut state for polling safety valve"
    - "refetchIntervalInBackground=false: pause polling when tab inactive (T-10-26 DoS mitigation)"
    - "zod regex /^\d{5}$/ for Türkiye postal code + react-hook-form 7 zodResolver"
key_files:
  created:
    - frontend/src/api/addressApi.ts
    - frontend/src/api/orderApi.ts
    - frontend/src/api/paymentApi.ts
    - frontend/src/components/checkout/CheckoutStepper.tsx
    - frontend/src/components/checkout/AddressCard.tsx
    - frontend/src/components/checkout/NewAddressForm.tsx
    - frontend/src/pages/CheckoutAddressPage.tsx
    - frontend/src/pages/CheckoutPaymentPage.tsx
    - frontend/src/pages/CheckoutResultPage.tsx
  modified:
    - frontend/src/lib/types.ts (added PENDING_INITIALIZATION to PaymentStatus union)
    - frontend/src/router.tsx (/odeme/adres, /odeme/odeme, /odeme/sonuc wired)
decisions:
  - "createOrder returns OrderCreatedResponse {orderId, status} not full Order — backend POST /orders returns minimal OrderResponse DTO"
  - "cancelOrder returns void — backend POST /orders/{id}/cancel returns 204 No Content"
  - "PaymentStatus union extended with PENDING_INITIALIZATION — backend 202 status when no payment row exists yet"
  - "pollForPaymentPageUrl polls on orderId from OrderCreatedResponse.orderId (not order.id)"
  - "node_modules installed locally in worktree frontend/ (symlink approach failed due to tsc not on PATH)"
metrics:
  duration: "~20 minutes"
  completed: "2026-05-01"
  tasks_completed: 4
  files_created: 9
  files_modified: 2
---

# Phase 10 Plan 07: Checkout Flow (Address → Payment → Result) Summary

Multi-step checkout flow at `/odeme/adres` → `/odeme/odeme` → `/odeme/sonuc` closing FE-09, AUTH-08, ORD-01, and PAY-04 with Iyzico hosted-page redirect and saga state polling.

## What Was Built

### Task 1: API surfaces + CheckoutStepper (commit 10284ac)

**`frontend/src/api/addressApi.ts`**
- `fetchAddresses()` → GET `/identity/addresses` → Address[]
- `createAddress(input: AddressInput)` → POST `/identity/addresses` → Address
- `addressesQueryKey = ['addresses']`

**`frontend/src/api/orderApi.ts`**
- `createOrder(req, idempotencyKey)` → POST `/orders` with `Idempotency-Key: <UUID>` header → `OrderCreatedResponse { orderId, status }`
- `fetchOrder(orderId)` → GET `/orders/{orderId}` → Order (full OrderDetailDto)
- `fetchMyOrders()` → GET `/orders` → Order[]
- `cancelOrder(orderId)` → POST `/orders/{orderId}/cancel` → void (204 No Content)
- `orderQueryKey(orderId)`, `myOrdersQueryKey`

**`frontend/src/api/paymentApi.ts`**
- `fetchPaymentForOrder(orderId)` → GET `/payments/{orderId}` → PaymentStatus
- `paymentForOrderQueryKey(orderId)`

**`frontend/src/components/checkout/CheckoutStepper.tsx`**
- 3-step indicator: "Adres › Ödeme › Onay"
- Active step: `bg-[#1C1C1E]` filled circle; past step: `bg-[#34A853]` (green checkmark-style); future: gray-200
- Past steps link back (accessible navigation)

### Task 2: Address step components + CheckoutAddressPage (commit 8cd4983)

**`frontend/src/components/checkout/AddressCard.tsx`**
- Radio card with all D-09 fields: title, isDefault badge, recipientName, phone, mahalle, streetLine, ilce, il, postalCode
- Selected state: `border-[#1C1C1E] ring-2 ring-[#1C1C1E]`

**`frontend/src/components/checkout/NewAddressForm.tsx`**
- react-hook-form 7 + zod 4 with zodResolver
- All 8 D-09 fields (title, recipientName, phone, il, ilce, mahalle, streetLine, postalCode)
- Turkish field validation: `postalCode: z.string().regex(/^\d{5}$/)`, phone min 10 digits, all strings min 1
- `createAddress()` mutation with `toast.success('Adres eklendi.')` + `onCreated(created)` callback
- `exactOptionalPropertyTypes` fix: Field prop types include `| undefined`

**`frontend/src/pages/CheckoutAddressPage.tsx`**
- Auto-selects `isDefault` address (or first) when addresses load
- Auto-expands inline form when address list is empty
- "+ Yeni adres ekle" toggle; new address auto-selected on success + form collapses
- "Devam Et" → `setAddress(selectedId)` + `navigate(ROUTES.CHECKOUT_PAYMENT)`

### Task 3: CheckoutPaymentPage (commit 4a982bf)

**`frontend/src/pages/CheckoutPaymentPage.tsx`**
- D-13 stepper guard: `!addressId` → `navigate(ROUTES.CHECKOUT_ADDRESS, { replace: true })`
- `ensureIdempotencyKey()` generates/reuses per-attempt UUID from checkoutStore
- `createOrder({ addressId, paymentMethod: 'CREDIT_CARD' }, idempotencyKey)` with `Idempotency-Key` header
- `pollForPaymentPageUrl(orderId)`: 5 retries × 1s delay — handles Phase 6 D-08 async saga lag
- `window.location.assign(paymentPageUrl)` redirects to Iyzico hosted page
- Fall-through path: if no paymentPageUrl after retries → navigate to result page (which keeps polling)
- Payment method radio: "Kredi Kartı" (default-checked), "Kapıda Ödeme" (disabled, "Yakında") per FE-V2-03 deferral

### Task 4: CheckoutResultPage (commit c92cf58)

**`frontend/src/pages/CheckoutResultPage.tsx`**
- URL: `/odeme/sonuc?orderId=<UUID>`
- UUID validation: regex `/^[0-9a-f]{8}-...-[0-9a-f]{12}$/i` — invalid → redirect HOME
- Polls `GET /orders/{orderId}` every 2s via `refetchInterval` with function-based stop condition
- `MAX_POLL_MS = 30_000` safety timeout via `setTimeout` + `timedOut` state
- `refetchIntervalInBackground: false` — polling pauses when tab is inactive (T-10-26 mitigation)
- **CONFIRMED** → `SuccessCard`: "Siparişiniz Alındı" + "Siparişimi Gör" CTA → `/siparislerim/<id>` + `invalidateQueries(cartQueryKey)` + `resetCheckout()`
- **PAYMENT_FAILED / STOCK_FAILED / CANCELLED** → `FailureCard`: "Ödemeniz Alınamadı" + "Tekrar Dene" → `/sepetim`
- **30s timeout still non-terminal** → `TimeoutCard`: "İşleminiz Hâlâ Kontrol Ediliyor" + "Siparişimi Görüntüle" → `/siparislerim/<id>`

## Full Demo Flow (End-to-End)

1. User in cart → "Siparişi Tamamla" → `/odeme/adres`
2. Address step: picks saved address (radio) or adds new one via inline form → "Devam Et" → `checkoutStore.setAddress(id)` → `/odeme/odeme`
3. Payment step: "Sipariş Ver" → `ensureIdempotencyKey()` → `POST /orders` (Idempotency-Key header) → `pollForPaymentPageUrl` (5 retries × 1s) → `window.location.assign(paymentPageUrl)` → Iyzico hosted form
4. User fills test card `5528 7900 0000 0008`, OTP "123456" on Iyzico hosted page
5. Iyzico redirects browser back to `/odeme/sonuc?orderId=<id>` (backend's hardcoded return URL)
6. Result page polls every 2s → `CONFIRMED` → SuccessCard "Siparişiniz Alındı" + "Siparişimi Gör" + cart invalidation + checkout state reset

## Retry-on-Pending Pattern (D-08)

`pollForPaymentPageUrl(orderId)` implements the D-08 client-side retry:
```typescript
for (let i = 0; i < 5; i++) {
  const status = await fetchPaymentForOrder(orderId);
  if (status.paymentPageUrl) return status.paymentPageUrl;
  if (terminal(status.status)) return null;
  await delay(1000);
}
return null;
```
The payment-service initializes the Iyzico checkout asynchronously after the `stock.reserved` saga event. The frontend gets `PENDING_INITIALIZATION` (202) or `PENDING` with null `paymentPageUrl` until initialization completes. If paymentPageUrl is still null after 5 retries, the result page's 30s polling loop takes over.

## Idempotency-Key Lifecycle

1. **Generate** (first "Sipariş Ver" click): `ensureIdempotencyKey()` calls `crypto.randomUUID()`, stores in `checkoutStore.idempotencyKey`
2. **Reuse** (retry within same session): `ensureIdempotencyKey()` returns existing key — same `POST /orders` body + same key → backend returns same idempotent result (D-13)
3. **Reset** (CONFIRMED): `checkoutStore.reset()` clears `{ addressId: null, paymentMethod: null, idempotencyKey: null }` — next checkout gets a fresh key

## Phase 6 Seam Confirmation

Backend's hardcoded return URL in payment-service points at the frontend's `/odeme/sonuc` path. The frontend does NOT need to pass a `returnUrl` in the order creation request — the plan verification confirmed no `returnUrl` field in `CreateOrderRequest`. The Iyzico callback `/api/v1/payments/iyzico/callback` is the server-to-server webhook; `/odeme/sonuc` is the user-redirect target. These are distinct concerns.

`PaymentStatus.status` enum extended with `PENDING_INITIALIZATION` to match the backend's `PaymentStatusResponse` 202-path (no payment row yet during saga initialization).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] createOrder returns OrderCreatedResponse not full Order**
- **Found during:** Task 1 read of OrderController.java
- **Issue:** `POST /orders` returns `OrderResponse { orderId, status }` (minimal DTO), not the full `Order` object with items/shippingAddress. Plan's code snippet typed it as `Promise<Order>`
- **Fix:** Defined `OrderCreatedResponse { orderId: string; status: Order['status'] }` as the createOrder return type; `CheckoutPaymentPage` uses `order.orderId` (not `order.id`) for the redirect
- **Files modified:** `frontend/src/api/orderApi.ts`, `frontend/src/pages/CheckoutPaymentPage.tsx`

**2. [Rule 1 - Bug] cancelOrder returns void (204 No Content)**
- **Found during:** Task 1 read of OrderController.java
- **Issue:** `POST /orders/{id}/cancel` is annotated `@ResponseStatus(HttpStatus.NO_CONTENT)` with `void` return
- **Fix:** `cancelOrder` returns `Promise<void>` instead of `Promise<Order>`
- **Files modified:** `frontend/src/api/orderApi.ts`

**3. [Rule 2 - Missing] PaymentStatus.PENDING_INITIALIZATION missing from types.ts**
- **Found during:** Task 1 read of PaymentStatusResponse.java
- **Issue:** Backend returns `"PENDING_INITIALIZATION"` status when no payment row exists yet (202 path). TypeScript would type-error on this unknown value in CheckoutPaymentPage
- **Fix:** Added `'PENDING_INITIALIZATION'` to `PaymentStatus.status` union in `types.ts` with explanatory comment
- **Files modified:** `frontend/src/lib/types.ts`

**4. [Rule 1 - Bug] exactOptionalPropertyTypes: NewAddressForm Field prop types**
- **Found during:** Task 2 npm run build
- **Issue:** TypeScript `exactOptionalPropertyTypes: true` in tsconfig rejected `error?: string` when passed `string | undefined` from react-hook-form's `errors.field?.message`
- **Fix:** All optional props in `FieldProps` type now use `?: T | undefined` explicitly
- **Files modified:** `frontend/src/components/checkout/NewAddressForm.tsx`

**5. [Rule 3 - Blocking] Worktree missing node_modules**
- **Found during:** Task 1 build attempt
- **Issue:** Worktree's frontend/node_modules was empty (.tmp only); prior builds in main branch used main repo's node_modules; symlink approach failed (tsc not on PATH)
- **Fix:** Ran `npm install` inside worktree's `frontend/` directory to install all deps locally
- **Files modified:** `frontend/package-lock.json`

## Known Stubs

None. All checkout pages are fully wired to real backend APIs. The `SHIPPING_FEE` stub in CartSummary (Plan 10-06) is out of scope here.

## Threat Flags

No new threat surfaces beyond the plan's `<threat_model>`:
- T-10-22 (orderId tampering): mitigated — backend GET /orders/{id} enforces ownership via X-User-Id
- T-10-23 (idempotency-key replay): mitigated — `ensureIdempotencyKey` reuses within session, reset on CONFIRMED
- T-10-24 (malicious paymentPageUrl): mitigated — URL comes from payment-service (JWT-gated backend)
- T-10-25 (UUID in URL): accepted — UUID v4 unguessable; server enforces ownership
- T-10-26 (DoS poll loop): mitigated — terminal-state stop + 30s timeout + refetchIntervalInBackground=false

## Self-Check: PASSED
