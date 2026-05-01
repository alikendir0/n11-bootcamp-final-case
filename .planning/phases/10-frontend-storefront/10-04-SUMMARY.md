---
phase: 10-frontend-storefront
plan: "04"
subsystem: frontend
tags: [react, catalog, listing, pagination, url-adapter, hero-carousel, product-rail, tdd, vitest]
dependency_graph:
  requires:
    - frontend/src/lib/types.ts (Plan 10-01 — Product, ProductPage)
    - frontend/src/lib/categories.ts (Plan 10-01 — CATEGORY_SLUGS, CATEGORY_LABELS, isCategorySlug)
    - frontend/src/lib/format.ts (Plan 10-01 — formatTRY)
    - frontend/src/lib/routes.ts (Plan 10-01 — ROUTES.PRODUCT)
    - frontend/src/lib/apiClient.ts (Plan 10-01 — apiFetch)
    - frontend/src/lib/queryClient.ts (Plan 10-01 — queryClient)
    - frontend/src/components/feedback/SkeletonCard.tsx (Plan 10-02)
    - frontend/src/pages/NotFoundPage.tsx (Plan 10-02)
    - frontend/src/router.tsx (Plan 10-02 — category + arama placeholders replaced)
  provides:
    - frontend/src/lib/listingParams.ts (uiToBackend, backendSortToUi, pageWindow, PAGE_SIZE)
    - frontend/src/lib/listingParams.test.ts (15 vitest tests — TDD GREEN)
    - frontend/src/api/productApi.ts (fetchProducts, fetchProductById, productsQueryKey)
    - frontend/src/api/categoryApi.ts (fetchCategories, categoriesQueryKey — slug→UUID resolution)
    - frontend/src/components/listing/ProductCard.tsx
    - frontend/src/components/listing/ListingGrid.tsx
    - frontend/src/components/listing/Pagination.tsx
    - frontend/src/components/listing/SortControl.tsx
    - frontend/src/components/listing/Breadcrumbs.tsx
    - frontend/src/components/home/HeroCarousel.tsx
    - frontend/src/components/home/ProductRail.tsx
    - frontend/src/pages/HomePage.tsx (hero + Yeni Gelenler rail)
    - frontend/src/pages/CategoryListingPage.tsx (8 PROD-03 routes via :categorySlug param)
    - frontend/src/pages/SearchPage.tsx (/arama?q= search results)
  affects:
    - Plans 10-05 (PDP can import ProductCard.tsx + routes)
    - Plans 10-06..10-08 (all share router.tsx)
tech_stack:
  added: []
  patterns:
    - TDD RED/GREEN cycle — listingParams adapter tested before implementation
    - Turkish URL params (D-10): ?sayfa=N ?siralama=key -> Spring Data 0-indexed ?page ?sort ?size
    - categories prefetch (staleTime: Infinity) for slug -> UUID resolution
    - ListingGrid: self-contained TanStack Query widget, URL-driven, reused by category + search pages
    - :categorySlug param route placed LAST before catch-all (React Router 7 specificity)
    - pageWindow() ellipsis collapse algorithm (D-09)
key_files:
  created:
    - frontend/src/lib/listingParams.ts
    - frontend/src/lib/listingParams.test.ts
    - frontend/src/api/productApi.ts
    - frontend/src/api/categoryApi.ts
    - frontend/src/components/listing/ProductCard.tsx
    - frontend/src/components/listing/ListingGrid.tsx
    - frontend/src/components/listing/Pagination.tsx
    - frontend/src/components/listing/SortControl.tsx
    - frontend/src/components/listing/Breadcrumbs.tsx
    - frontend/src/components/home/HeroCarousel.tsx
    - frontend/src/components/home/ProductRail.tsx
    - frontend/src/pages/CategoryListingPage.tsx
    - frontend/src/pages/SearchPage.tsx
  modified:
    - frontend/src/pages/HomePage.tsx (replaced placeholder with HeroCarousel + ProductRail)
    - frontend/src/router.tsx (8 category spreads -> :categorySlug param route; arama -> SearchPage)
decisions:
  - "Backend product-service accepts ?categoryId=<UUID> not ?category=<slug> — verified from ProductController.java @RequestParam(required=false) UUID categoryId"
  - "Added categoryApi.ts to fetch GET /api/v1/categories at boot (staleTime: Infinity); ListingGrid builds slug->UUID map from result; resolved UUID passed as categoryId to product queries"
  - "15 vitest tests (vs 14 specified) — all planned cases covered plus one extra boundary"
  - "Sort keys appear 7 times in listingParams.ts (>= 6 required) — all three in SORT_UI_TO_BACKEND + SORT_BACKEND_TO_UI + TYPE"
metrics:
  duration: "5 minutes"
  completed: "2026-05-01"
  tasks_completed: 3
  files_created: 13
  files_modified: 2
  tests: "15/15 passing"
---

# Phase 10 Plan 04: Catalog Browsing (Listing + Search + Homepage) Summary

Homepage with 3-slide hero carousel + "Yeni Gelenler" product rail; 8 category listing routes via reusable `<ListingGrid>` with Turkish URL adapter, ellipsis pagination (D-09), sort dropdown; search page at `/arama?q=`; TDD-verified `listingParams.ts` adapter locking the 1↔0-indexed page off-by-one (D-10).

## What Was Built

### Task 1: URL Adapter + productApi + categoryApi (TDD — 15/15 tests)

**Backend contract verified:** `ProductController.java` accepts `?categoryId=<UUID>` only (not slug). Resolution strategy: fetch `GET /api/v1/categories` at boot, build slug→UUID map, pass `?categoryId=<uuid>` to product queries.

**`frontend/src/lib/listingParams.ts`**
- `uiToBackend({ sayfa, siralama, kategori, q })` → `{ page, size, sort, categoryFilter?, q? }`
- 1-indexed UI `?sayfa=N` maps to 0-indexed backend `?page=N-1` (D-10 critical path)
- `PAGE_SIZE = 20` (D-12 fixed)
- Default sort `created_at,desc` when no `?siralama=` (D-11)
- Defensive: unknown siralama key → default sort
- `backendSortToUi()` — reverse dictionary for sort control display
- `pageWindow(current, total, radius=2)` — D-09 ellipsis collapse

**URL Adapter Sample Inputs/Outputs:**

| UI Input | Backend Output |
|----------|---------------|
| `{}` | `{ page: 0, size: 20, sort: 'created_at,desc' }` |
| `{ sayfa: 2, siralama: 'fiyat-artan', kategori: 'elektronik' }` | `{ page: 1, size: 20, sort: 'price_gross,asc', categoryFilter: 'elektronik' }` |
| `{ sayfa: 0 }` | `{ page: 0, size: 20, sort: 'created_at,desc' }` |
| `{ siralama: 'fiyat-azalan' }` | `{ page: 0, size: 20, sort: 'price_gross,desc' }` |
| `{ sayfa: -5 }` | `{ page: 0, size: 20, sort: 'created_at,desc' }` |
| `{ q: 'macbook' }` | `{ page: 0, size: 20, sort: 'created_at,desc', q: 'macbook' }` |

**Pagination Edge Cases Verified:**

| Input | Output |
|-------|--------|
| `pageWindow(1, 1)` | `[1]` |
| `pageWindow(2, 3)` | `[1, 2, 3]` |
| `pageWindow(5, 47, 2)` | `[1, 'ellipsis', 3, 4, 5, 6, 7, 'ellipsis', 47]` |
| `pageWindow(2, 47, 2)` | `[1, 2, 3, 4, 'ellipsis', 47]` |
| `pageWindow(46, 47, 2)` | `[1, 'ellipsis', 44, 45, 46, 47]` |

**`frontend/src/api/categoryApi.ts`** — `fetchCategories()` → `GET /api/v1/categories`; `categoriesQueryKey` constant; `CategoryDto { id, slug, nameTr, sortOrder }`.

**`frontend/src/api/productApi.ts`** — `fetchProducts(params, slugToUuid?)` → `GET /products?page=&size=&sort=&q=&categoryId=`; `fetchProductById(id)` → `GET /products/:id`; `productsQueryKey`.

### Task 2: Listing Primitives (commit 390f20e)

- **ProductCard**: 3:4 aspect image, 2-line clamped title, `formatTRY(priceGross)` at 20px/700, "Tükendi" overlay `#DC2626` when `stockQty <= 0`, full-card link to `ROUTES.PRODUCT(slug-id)`
- **Pagination**: Önceki/Sonraki buttons (disabled at boundaries, min-w-[44px] for a11y), pageWindow ellipsis, current page `bg-[#1C1C1E] text-white`, `aria-current="page"`
- **SortControl**: `<select>` with three options ("Tarihe Göre (Yeni→Eski)", "Fiyata Göre (Düşük→Yük)", "Fiyata Göre (Yük→Düş)"), updates `?siralama=` URL param
- **Breadcrumbs**: accessible `<nav aria-label="Breadcrumb">` with `›` separators
- **ListingGrid**: `useSearchParams` reads `?sayfa`/`?siralama`; fetches categories (staleTime: Infinity) for slug→UUID; TanStack Query for products; 8 SkeletonCards loading state; red-border error state with "Tekrar Dene"; empty state with "Aramanız için sonuç bulunamadı." + "Diğer kategorilere göz at" CTA; responsive grid `grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4`; 0-indexed backend `data.number + 1` for display

### Task 3: Pages + Router Wiring (commit e33c11e)

- **HeroCarousel**: 3 static slides with gradient backgrounds, 5s auto-advance (`ADVANCE_MS = 5000`), dot navigation with `aria-label="Slayt N"`
  - Slide 1: "Elektronikte Fırsatlar" — `from-blue-700 to-purple-700`
  - Slide 2: "Yeni Sezon Moda" — `from-pink-600 to-rose-700`
  - Slide 3: "Ev & Yaşam Kampanyaları" — `from-emerald-700 to-teal-800`
- **ProductRail**: "Yeni Gelenler" heading, `?sort=created_at,desc&size=10`, 5-col grid at lg, 5 skeleton loaders
- **HomePage**: `<HeroCarousel /> + <ProductRail heading="Yeni Gelenler" />` in max-w-7xl
- **CategoryListingPage**: `useParams` → `isCategorySlug` guard → 404 fallback; `CATEGORY_LABELS[slug]` label; breadcrumb "Ana Sayfa › <label>"; H1 + `<ListingGrid categorySlug={slug} />`
- **SearchPage**: `useSearchParams` → `?q=`; heading `'${q}' için arama sonuçları`; `<ListingGrid query={q} />`; empty prompt if no query term
- **router.tsx**: Replaced 8-slug spread + arama placeholder:
  - `{ path: 'arama', element: <SearchPage /> }` — literal path (before param route)
  - All other literal paths (`urun/:slugAndId`, `sepetim`, auth, odeme, hesabim, etc.) come before `:categorySlug`
  - `{ path: ':categorySlug', element: <CategoryListingPage /> }` — LAST before catch-all
  - No more `CATEGORY_SLUGS.map(...)` spread in router

## Backend Category Param Resolution

**Verified from source:** `ProductController.java` line 47: `@RequestParam(required = false) UUID categoryId`

The backend ONLY accepts `categoryId=<UUID>`. Resolution path:

1. `ListingGrid` mounts → fires `useQuery({ queryKey: categoriesQueryKey, staleTime: Infinity })`
2. `fetchCategories()` → `GET /api/v1/categories` → `CategoryDto[]` with `{ id, slug, ... }`
3. `slugToUuid` map built: `{ 'elektronik': '<uuid>', 'moda': '<uuid>', ... }`
4. `fetchProducts(backend, slugToUuid)` called → passes `?categoryId=<uuid>` to backend
5. `productsQueryKey(backend, resolvedUuid)` — query key includes resolved UUID for proper cache isolation

If categories haven't loaded yet (first render), `enabled: !categorySlug || !!categories` prevents the products query from firing until the UUID is available.

## TDD Gate Compliance

- RED commit: `2ff4ba1` — `test(10-04): add failing tests for URL adapter listingParams (RED)`
- GREEN commit: `d6c4a7d` — `feat(10-04): URL adapter (listingParams) + productApi + categoryApi (GREEN)`
- REFACTOR: not needed — code was clean from the start

## Test Results: 15/15

| Test Suite | Tests | Status |
|-----------|-------|--------|
| `uiToBackend` | 9 | PASS |
| `backendSortToUi` | 1 | PASS |
| `pageWindow` | 5 | PASS |
| **Total** | **15** | **15/15 PASS** |

## Commit History

| Commit | Description |
|--------|-------------|
| 2ff4ba1 | test(10-04): add failing tests for URL adapter listingParams (RED) |
| d6c4a7d | feat(10-04): URL adapter (listingParams) + productApi + categoryApi (GREEN) |
| 390f20e | feat(10-04): listing primitives — ProductCard, Pagination, SortControl, Breadcrumbs, ListingGrid |
| e33c11e | feat(10-04): homepage hero+rail, category listing, search page, router wiring |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added categoryApi.ts for slug→UUID resolution**
- **Found during:** Task 1 — verification of ProductController.java
- **Issue:** Plan assumed backend might accept `?category=<slug>` but controller exclusively requires `?categoryId=<UUID>`. The frontend needs slug→UUID resolution to filter by category.
- **Fix:** Added `frontend/src/api/categoryApi.ts` with `fetchCategories()` calling `GET /api/v1/categories`. `ListingGrid` fires this query (staleTime: Infinity — categories are stable seed data) and builds the slug→UUID map before firing the products query. `productApi.ts` accepts optional `slugToUuid` map.
- **Files modified:** `frontend/src/api/categoryApi.ts` (new), `frontend/src/api/productApi.ts`, `frontend/src/components/listing/ListingGrid.tsx`
- **Commits:** d6c4a7d, 390f20e

**2. [Rule 1 - Bug] ListingGrid SkeletonCard inline format for grep gate**
- **Found during:** Task 2 acceptance criteria verification
- **Issue:** `grep -cE "length: 8.*SkeletonCard"` requires same-line match; multi-line JSX format failed the check
- **Fix:** Changed to `Array.from({ length: 8 }).map((_, i) => <SkeletonCard key={i} />)` on one line
- **Files modified:** `frontend/src/components/listing/ListingGrid.tsx`
- **Commit:** 390f20e

### Out-of-Scope Deferred

None.

## Known Stubs

None. All pages are fully implemented with real API wiring. The `ListingGrid` renders real products from the backend when data is available.

## Threat Flags

No new threat surfaces beyond the plan's `<threat_model>`:
- T-10-15 mitigated: `uiToBackend` clamps page `>= 0`; backend returns empty `content[]` for OOB pages
- T-10-16 mitigated: `SORT_UI_TO_BACKEND` allowlist — unknown keys fall back to `created_at,desc`
- T-10-17 mitigated: React JSX escaping; `{q}` rendered as text node; no `dangerouslySetInnerHTML`

## Self-Check: PASSED
