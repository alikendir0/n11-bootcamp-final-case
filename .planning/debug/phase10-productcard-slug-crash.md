# Phase 10 Debug: ProductCard Slug Crash

## Symptom

UAT Tests 1 and 4 crash the storefront with:

`Cannot read properties of undefined (reading 'toLocaleLowerCase')` at `productSlug` in `frontend/src/components/listing/ProductCard.tsx`.

## Root Cause

The frontend listing contract is wrong. `ProductCard` expects `product.name`, but `fetchProducts()` returns backend `Page<ProductSummaryDto>` unchanged, and the backend listing DTO exposes `nameTr`, not `name`.

## Evidence

- `ProductCard.tsx` unconditionally calls `p.name.toLocaleLowerCase('tr-TR')`.
- `ProductRail.tsx` passes `data?.content` directly from `fetchProducts()` into `ProductCard`.
- Backend listing DTO fields are `id`, `sku`, `nameTr`, `priceGross`, `kdvRate`, `slug`, `categoryName`, `firstImageUrl`; no `name` field exists.

## Suggested Fix Direction

Add an explicit frontend API mapping layer or align frontend types to backend DTOs. For listing cards, map `nameTr -> name`, `firstImageUrl -> imageUrl`, and `categoryName -> categoryLabel`, then make unsupported fields optional or derive them safely.
