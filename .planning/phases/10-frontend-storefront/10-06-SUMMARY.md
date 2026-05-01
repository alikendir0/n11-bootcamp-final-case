---
phase: 10-frontend-storefront
plan: "06"
subsystem: frontend
tags: [react, cart, optimistic-updates, tanstack-query, undo-toast, tdd, turkish-copy]
dependency_graph:
  requires:
    - frontend/src/api/cartApi.ts (Plans 10-02 + 10-05 — fetchCart + addToCart; extended here)
    - frontend/src/lib/types.ts (Plan 10-01 — Cart, CartLineItem)
    - frontend/src/lib/format.ts (Plan 10-01 — formatTRY)
    - frontend/src/lib/freeShipping.ts (Plan 10-05 — qualifiesForFreeShipping, FREE_SHIPPING_THRESHOLD)
    - frontend/src/lib/routes.ts (Plan 10-01 — ROUTES.LOGIN, ROUTES.HOME, ROUTES.CHECKOUT_ADDRESS, ROUTES.PRODUCT)
    - frontend/src/store/authStore.ts (Plan 10-01 — useAuthStore)
    - frontend/src/hooks/useCart.ts (Plan 10-02 — useCart, cartQueryKey)
    - frontend/src/lib/apiClient.ts (Plan 10-01 — ApiError)
  provides:
    - frontend/src/api/cartApi.ts (extended — now exports fetchCart + addToCart + updateCartItem + removeCartItem)
    - frontend/src/components/cart/QtyStepper.tsx
    - frontend/src/components/cart/CartLineItemRow.tsx
    - frontend/src/components/cart/CartSummary.tsx
    - frontend/src/components/cart/EmptyCart.tsx
    - frontend/src/pages/CartPage.tsx
    - frontend/src/router.tsx (/sepetim -> CartPage)
  affects:
    - Plan 10-07 (checkout flow proceeds from /sepetim via "Siparişi Tamamla" CTA)
    - Plan 10-08 (cart badge stays in sync via shared cartQueryKey — unchanged from Plan 10-02)
tech_stack:
  added: []
  patterns:
    - Optimistic update pattern (onMutate cancelQueries + setQueryData + rollback on error)
    - Sonner toast.action for undo with 5000ms duration window
    - qty < 1 triggers remove mutation instead of patch (ergonomic stepper behaviour)
    - PATCH /cart/items/{productId} deployed verb verified against CartController.java
    - encodeURIComponent on productId in both PATCH and DELETE paths (T-10-20 mitigation)
key_files:
  created:
    - frontend/src/components/cart/QtyStepper.tsx
    - frontend/src/components/cart/CartLineItemRow.tsx
    - frontend/src/components/cart/CartSummary.tsx
    - frontend/src/components/cart/EmptyCart.tsx
    - frontend/src/pages/CartPage.tsx
  modified:
    - frontend/src/api/cartApi.ts (added updateCartItem + removeCartItem)
    - frontend/src/router.tsx (/sepetim -> CartPage)
decisions:
  - "PATCH verb confirmed in CartController.java @PatchMapping before writing updateCartItem"
  - "encodeURIComponent wraps productId in both PATCH and DELETE paths to handle UUID chars safely"
  - "qty < 1 in handleQtyChange fires removeMutation instead of qtyMutation — ergonomic stepper edge case"
  - "CartSummary SHIPPING_FEE stub = 29.90 TRY per plan (planner discretion) — shown only when subtotal < threshold"
  - "undoRemove uses addToCart (UPSERT) to restore — cart-service POST /cart/items is idempotent per Phase 5"
metrics:
  duration: "~3 minutes"
  completed: "2026-05-01"
  tasks_completed: 3
  files_created: 5
  files_modified: 2
  tests: "n/a (UI component tasks — build verification used)"
---

# Phase 10 Plan 06: Cart Page Summary

Cart page at `/sepetim` with optimistic qty-update + remove mutations (PATCH/DELETE against deployed cart-service), 5s undo toast with "Geri al" action, two-column populated layout (line items + Sipariş Özeti), and recon-faithful Turkish empty-state copy.

## What Was Built

### Task 1: cartApi extended + QtyStepper + CartLineItemRow (commit 1ddd752)

**`frontend/src/api/cartApi.ts`** extended — now exports all four verbs:

```typescript
export function updateCartItem(productId: string, qty: number): Promise<void> {
  return apiFetch<void>(`/cart/items/${encodeURIComponent(productId)}`, {
    method: 'PATCH',
    body: JSON.stringify({ qty }),
  });
}

export function removeCartItem(productId: string): Promise<void> {
  return apiFetch<void>(`/cart/items/${encodeURIComponent(productId)}`, {
    method: 'DELETE',
  });
}
```

PATCH verb verified against `CartController.java` `@PatchMapping("/items/{productId}")` before writing.

**`QtyStepper`:**
- `−` / qty / `+` button row with `border-[var(--color-border)]` styling
- `MAX_QTY = 99` per Phase 5 CD-10; `MIN_QTY = 1`
- Turkish aria-labels: `aria-label="Adedi azalt"` and `aria-label="Adedi artır"`
- `aria-live="polite"` on qty span

**`CartLineItemRow`:**
- 64px square image + title (PDP link via `ROUTES.PRODUCT`) + unit price + `QtyStepper` + line total + trash icon button
- `aria-label="Ürünü kaldır"` on trash button (UI-SPEC v2 a11y fix)
- `aria-busy={mutating}` on article element
- `formatTRY` for unit price and line total (LOC-01/02)

### Task 2: CartSummary sidebar + EmptyCart state (commit b907225)

**`CartSummary`:**
- Computes `subtotal = sum(unitPriceSnapshot * qty)` across items
- Uses `qualifiesForFreeShipping(subtotal)` from Plan 10-05 helper
- Kargo line: "Kargo Bedava" (green `#34A853`) when threshold met; otherwise `29.90 TRY` stub
- Threshold gap hint: "{formatTRY(threshold - subtotal)} daha eklerseniz kargo bedava!"
- "KDV Dahil" label (server-side price_gross; no breakdown computation)
- "Siparişi Tamamla" CTA: navigates to `/odeme/adres` when authed; `/giris-yap?redirectUrl=%2Fodeme%2Fadres` when anonymous
- `sticky top-20` sidebar; disabled when no items

**`EmptyCart`:**
- "Sepetin Boş Görünüyor" heading (recon phrase 73, verbatim)
- "Alışverişe Başla" → `ROUTES.HOME`
- "Hemen Giriş Yap" → `ROUTES.LOGIN` shown only when anonymous (recon phrase 76)

### Task 3: CartPage + router wire-up (commit fb13d4c)

**Optimistic qty-change mutation:**
```typescript
onMutate: async ({ productId, qty }) => {
  await qc.cancelQueries({ queryKey: cartQueryKey });
  const previous = qc.getQueryData<Cart>(cartQueryKey);
  qc.setQueryData<Cart>(cartQueryKey, {
    ...previous,
    items: previous.items.map(i => i.productId === productId ? { ...i, qty } : i),
  });
  return { previous };
},
onError: (err, _, context) => {
  if (context?.previous) qc.setQueryData(cartQueryKey, context.previous);
  // show Turkish error toast
},
onSettled: () => qc.invalidateQueries({ queryKey: cartQueryKey }),
```

**Optimistic remove + undo toast:**
```typescript
onSuccess: (_, _productId, context) => {
  toast('Ürün sepetten çıkarıldı.', {
    action: { label: 'Geri al', onClick: () => undoRemove(removed) },
    duration: 5000,
  });
},
```
`undoRemove` calls `addToCart` (UPSERT via POST) to restore the item, then invalidates cartQueryKey.

**Ergonomic qty edge case:**
```typescript
function handleQtyChange(productId: string, qty: number) {
  if (qty < 1) {
    removeMutation.mutate(productId);  // decrement from 1 → remove
    return;
  }
  qtyMutation.mutate({ productId, qty });
}
```

**Page layout:**
- Loading: `<CartSkeleton />` — 3 pulse-animated rows + sidebar placeholder
- Error: inline retry block with "Tekrar Dene" button
- Empty cart: `<EmptyCart />` (anonymous or authed-empty both show same component)
- Populated: `grid grid-cols-1 lg:grid-cols-[1fr_360px]` — items list left + `<CartSummary>` right

**`router.tsx`:** `import CartPage from './pages/CartPage'` + `/sepetim` route changed from `<PlaceholderPage name="Sepetim" />` to `<CartPage />`.

## Cart Page UX Flow

1. **Anonymous visit to /sepetim:** `useCart` is disabled (not authenticated); `cart = placeholderData = empty`. EmptyCart renders "Sepetin Boş Görünüyor" + "Alışverişe Başla" + "Hemen Giriş Yap".

2. **Authenticated with items:** Populated two-column layout. Each row shows image + title + qty stepper + unit price + line total + trash button.

3. **Qty change:** User clicks + → `onMutate` immediately updates cache → UI reflects new qty → `updateCartItem` PATCHes backend → `onSettled` invalidates and refetches to confirm. On error: rollback + toast.

4. **Remove item:** User clicks trash → `onMutate` immediately removes from cache → `removeCartItem` DELETEs backend → `onSuccess` shows 5s undo toast "Ürün sepetten çıkarıldı." with "Geri al" action. Clicking "Geri al" calls `addToCart` (UPSERT) to restore. On error: rollback + toast.

5. **Header badge:** `useCartItemCount()` in `Header.tsx` reads from the same `cartQueryKey` — invalidation from mutations automatically keeps it in sync.

6. **"Siparişi Tamamla":** Authed → `/odeme/adres`. Anonymous → `/giris-yap?redirectUrl=%2Fodeme%2Fadres`.

## Optimistic Mutation Behavior (Rollback Verified)

Both `qtyMutation` and `removeMutation` implement:
1. `cancelQueries` — prevents in-flight refetch from overwriting optimistic state
2. `getQueryData` → `setQueryData` — immediate UI update
3. `return { previous }` from `onMutate` — stores snapshot in context
4. `if (context?.previous) qc.setQueryData(cartQueryKey, context.previous)` in `onError` — restores on failure
5. `onSettled` invalidation — always syncs from server after settle

T-10-20 mitigated: backend's `@Max(99)` validation rejects out-of-range qty with 400 → `onError` rolls back the optimistic update. UI never permanently shows an invalid qty.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

| File | Line | Content | Reason |
|------|------|---------|--------|
| `frontend/src/components/cart/CartSummary.tsx` | 8 | `SHIPPING_FEE = 29.90` | Planner-discretion stub per plan spec; only displayed when subtotal < FREE_SHIPPING_THRESHOLD. A real shipping-fee calculation would require a shipping-service endpoint (out of v1 scope). Does not prevent the plan goal — Kargo Bedava path is fully functional. |

## Threat Flags

No new threat surfaces beyond the plan's `<threat_model>`:
- T-10-20 mitigated: optimistic updates roll back on backend 400 error; backend `@Max(99)` rejects invalid qty
- T-10-21 accepted: undo-then-add pattern is manual clicks; cart-service UPSERT is idempotent; no retry storm possible

## Commit History

| Commit | Description |
|--------|-------------|
| 1ddd752 | feat(10-06): extend cartApi with updateCartItem + removeCartItem; add QtyStepper + CartLineItemRow |
| b907225 | feat(10-06): CartSummary sidebar + EmptyCart state |
| fb13d4c | feat(10-06): CartPage with optimistic mutations + undo toast + router wire-up |

## Self-Check: PASSED
