---
phase: 10-frontend-storefront
plan: "05"
subsystem: frontend
tags: [react, pdp, product-detail, taksit, free-shipping, add-to-cart, tdd, vitest]
dependency_graph:
  requires:
    - frontend/src/lib/types.ts (Plan 10-01 — Product, Cart, CartLineItem)
    - frontend/src/api/productApi.ts (Plan 10-04 — fetchProductById, productByIdQueryKey)
    - frontend/src/api/cartApi.ts (Plan 10-02 — fetchCart; THIS plan extended with addToCart)
    - frontend/src/lib/format.ts (Plan 10-01 — formatTRY)
    - frontend/src/lib/categories.ts (Plan 10-01 — CATEGORY_LABELS for breadcrumb)
    - frontend/src/lib/routes.ts (Plan 10-01 — ROUTES.LOGIN, ROUTES.HOME)
    - frontend/src/store/authStore.ts (Plan 10-01 — useAuthStore isAuthenticated)
    - frontend/src/hooks/useCart.ts (Plan 10-02 — cartQueryKey for invalidation)
    - frontend/src/components/listing/Breadcrumbs.tsx (Plan 10-04)
    - frontend/src/pages/NotFoundPage.tsx (Plan 10-02)
    - frontend/src/router.tsx (Plan 10-04 — /urun/:slugAndId placeholder replaced)
  provides:
    - frontend/src/lib/taksit.ts (computeTaksit, TAKSIT_TIERS)
    - frontend/src/lib/taksit.test.ts (5 vitest tests — TDD RED/GREEN)
    - frontend/src/lib/freeShipping.ts (qualifiesForFreeShipping, FREE_SHIPPING_THRESHOLD)
    - frontend/src/api/cartApi.ts (extended — now exports fetchCart + addToCart)
    - frontend/src/components/pdp/ImageGallery.tsx
    - frontend/src/components/pdp/StockBadge.tsx
    - frontend/src/components/pdp/FreeShippingBadge.tsx
    - frontend/src/components/pdp/TaksitTable.tsx
    - frontend/src/components/pdp/PdpTabs.tsx
    - frontend/src/pages/ProductDetailPage.tsx
    - frontend/src/router.tsx (/urun/:slugAndId -> ProductDetailPage)
  affects:
    - Plan 10-06 (cart page can extend cartApi.ts, use same addToCart pattern)
    - Plan 10-07 (checkout flow depends on add-to-cart working correctly)
    - Header cart badge increments via cartQueryKey invalidation
tech_stack:
  added: []
  patterns:
    - TDD RED/GREEN cycle — taksit.test.ts written before taksit.ts
    - UUID extraction from last 36 chars of slugAndId param (T-10-18 mitigation)
    - useMutation for add-to-cart with onSuccess/onError toast pattern
    - anonymous redirect via ROUTES.LOGIN?redirectUrl=encodeURIComponent(pathname)
    - cartQueryKey invalidation on add-to-cart success to update header badge
    - VITE_FREE_SHIPPING_THRESHOLD env-config with safe 500 TRY default (Pitfall #23)
key_files:
  created:
    - frontend/src/lib/taksit.ts
    - frontend/src/lib/taksit.test.ts
    - frontend/src/lib/freeShipping.ts
    - frontend/src/components/pdp/ImageGallery.tsx
    - frontend/src/components/pdp/StockBadge.tsx
    - frontend/src/components/pdp/FreeShippingBadge.tsx
    - frontend/src/components/pdp/TaksitTable.tsx
    - frontend/src/components/pdp/PdpTabs.tsx
    - frontend/src/pages/ProductDetailPage.tsx
  modified:
    - frontend/src/api/cartApi.ts (added addToCart + AddToCartRequest interface)
    - frontend/src/router.tsx (/urun/:slugAndId -> ProductDetailPage)
    - frontend/.env.example (added VITE_FREE_SHIPPING_THRESHOLD=500)
    - frontend/src/vite-env.d.ts (added VITE_FREE_SHIPPING_THRESHOLD optional key)
decisions:
  - "UUID extracted from last 36 chars of slugAndId (T-10-18: slug is decorative, UUID is canonical product identifier)"
  - "FREE_SHIPPING_THRESHOLD defaults to 500 TRY when VITE_FREE_SHIPPING_THRESHOLD env var absent"
  - "PdpTabs 'Özellikler' content is intentionally static ('yakında eklenecek') — Product type v1 has only description field"
  - "Breadcrumbs use product.categoryLabel from API response (not client-side CATEGORY_LABELS lookup) for accuracy"
metrics:
  duration: "4 minutes"
  completed: "2026-05-01"
  tasks_completed: 3
  files_created: 9
  files_modified: 4
  tests: "5/5 passing"
---

# Phase 10 Plan 05: Product Detail Page (PDP) Summary

PDP at `/urun/:slugAndId` with image gallery, breadcrumbs, KDV-inclusive price (Display 24px/700), taksit table (1/2/3/6/9/12 per LOC-03), Kargo Bedava badge above 500 TRY threshold (LOC-04), stock badge (PROD-06), black "Sepete Ekle" CTA, and Açıklama/Özellikler/Kargo tabs; add-to-cart wires through cart-service POST /cart/items, invalidates cart query, and shows Turkish success toast.

## What Was Built

### Task 1: addToCart API + taksit + freeShipping helpers (TDD — 5/5 tests)

**`frontend/src/api/cartApi.ts`** extended — now exports both `fetchCart` (from Plan 10-02) and `addToCart`:

```typescript
export function addToCart(req: AddToCartRequest): Promise<void> {
  return apiFetch<void>('/cart/items', { method: 'POST', body: JSON.stringify(req) });
}
```

**`frontend/src/lib/taksit.ts`** (LOC-03):
- `TAKSIT_TIERS: readonly number[] = [1, 2, 3, 6, 9, 12]` — locked to UI-SPEC
- `computeTaksit(priceGross)` → `TaksitRow[]` using `Math.ceil(priceGross / n)` per LOC-03

**`frontend/src/lib/freeShipping.ts`** (LOC-04):
- `FREE_SHIPPING_THRESHOLD` reads from `VITE_FREE_SHIPPING_THRESHOLD` env, defaults to `500`
- `qualifiesForFreeShipping(priceGross): boolean`

**`.env.example` + `vite-env.d.ts`**: `VITE_FREE_SHIPPING_THRESHOLD=500` added.

TDD Gate:
- RED commit: `5f70c34` — failing tests before taksit.ts existed
- GREEN commit: `6925473` — taksit.ts implementation passes all 5 tests

### Task 2: PDP Primitives (commit 3c2ef78)

| Component | Purpose |
|-----------|---------|
| `ImageGallery` | Primary image + thumbnail strip; click to swap; `aria-current` on active thumbnail; v1 single-image fallback (forward-compat `additionalUrls?`) |
| `StockBadge` | Green dot + "Stokta" (qty>0) / Red dot + "Tükendi" (qty=0) — PROD-06 |
| `FreeShippingBadge` | Green pill "Kargo Bedava" shown only when `qualifiesForFreeShipping(priceGross)` — LOC-04 |
| `TaksitTable` | "Taksit Seçenekleri" heading; 6-row table from `computeTaksit`; `formatTRY(monthly)` per row — LOC-03 |
| `PdpTabs` | Açıklama / Özellikler / Kargo; `role="tablist/tab/tabpanel"`; active underline `border-[#1C1C1E]` — UI-SPEC |

### Task 3: ProductDetailPage + router wiring (commit 781e0da)

**UUID Extraction:**
```typescript
function extractUuid(slugAndId: string | undefined): string | null {
  if (!slugAndId || slugAndId.length < 36) return null;
  const uuid = slugAndId.slice(-36);
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(uuid)) return null;
  return uuid;
}
```
The slug before the UUID is decorative — only the UUID is sent to the backend. Invalid/missing UUID renders `<NotFoundPage />`.

**Add-to-cart flow:**
1. Authenticated user clicks "Sepete Ekle" → `useMutation` fires `addToCart({ productId, qty: 1 })`
2. `onSuccess`: `qc.invalidateQueries({ queryKey: cartQueryKey })` + `toast.success('Ürün sepete eklendi.')`
3. `onError`: toast with RFC-7807 `detail` field or generic Turkish fallback
4. Anonymous user: `navigate(ROUTES.LOGIN + '?redirectUrl=' + encodeURIComponent(pathname))`
5. Out-of-stock: button disabled, label switches to "Tükendi"

**PDP Component Tree:**
```
ProductDetailPage
  <Breadcrumbs> — Ana Sayfa › {categoryLabel} › {product.name}
  <div grid lg:grid-cols-2>
    <ImageGallery primaryUrl={product.imageUrl} altText={product.name} />
    <div>
      <h1>title</h1>
      <p>formatTRY(priceGross)</p>     ← Display 24px/700
      <p>KDV Dahil</p>
      <FreeShippingBadge priceGross={...} />
      <TaksitTable priceGross={...} />
      <StockBadge qty={product.stockQty} />
      <button bg-[#1C1C1E]>Sepete Ekle</button>   ← CTA
    </div>
  </div>
  <PdpTabs description={product.description} />   ← below fold
```

**`router.tsx`**: `/urun/:slugAndId` route changed from `<PlaceholderPage>` to `<ProductDetailPage />`.

## Add-to-Cart Success Behavior Verified

- `useMutation.onSuccess` fires `qc.invalidateQueries({ queryKey: cartQueryKey })` which triggers `useCart` refetch
- `useCart` is in `Header.tsx` via `useCartItemCount()` — badge increments automatically
- `toast.success('Ürün sepete eklendi.')` appears top-right via sonner (Plan 10-02 ToastBridge)

## FREE_SHIPPING_THRESHOLD Default

`500` TRY — consistent with typical Türkiye free-shipping floor. Override via `VITE_FREE_SHIPPING_THRESHOLD` env var in `.env.local` or production env.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

| File | Line | Content | Reason |
|------|------|---------|--------|
| `frontend/src/components/pdp/PdpTabs.tsx` | 40 | "Ürün özellikleri yakında eklenecek." | Intentional: Product v1 type has only `description`; "Özellikler" tab content requires structured product attributes (out of v1 scope per PROD-09 single-field seed). Plan explicitly documents this stub. |

This stub does NOT prevent the plan's goal — the PDP renders correctly with Açıklama and Kargo tabs showing real content. "Özellikler" is a forward-compat placeholder.

## Threat Flags

No new threat surfaces beyond the plan's `<threat_model>`:
- T-10-18 mitigated: UUID extracted from last 36 chars + regex-validated; invalid UUIDs render NotFoundPage. Slug is never executed or passed to backend.
- T-10-19 accepted: `description` rendered as JSX text node (no `dangerouslySetInnerHTML`); React's default escaping prevents XSS.

## TDD Gate Compliance

- RED commit: `5f70c34` — `test(10-05): add failing tests for taksit helpers (RED)` — tests fail (taksit.ts absent)
- GREEN commit: `6925473` — `feat(10-05): addToCart API + taksit + freeShipping helpers + env config (GREEN)` — 5/5 tests pass

## Commit History

| Commit | Description |
|--------|-------------|
| 5f70c34 | test(10-05): add failing tests for taksit helpers (RED) |
| 6925473 | feat(10-05): addToCart API + taksit + freeShipping helpers + env config (GREEN) |
| 3c2ef78 | feat(10-05): PDP primitives — ImageGallery, StockBadge, FreeShippingBadge, TaksitTable, PdpTabs |
| 781e0da | feat(10-05): ProductDetailPage PDP + router wiring |

## Self-Check: PASSED
