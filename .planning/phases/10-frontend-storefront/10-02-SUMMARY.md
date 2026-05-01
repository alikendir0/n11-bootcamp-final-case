---
phase: 10-frontend-storefront
plan: "02"
subsystem: frontend
tags: [react, router, layout, header, auth-guard, toasts, error-boundary, tanstack-query]
dependency_graph:
  requires:
    - frontend/src/lib/routes.ts (Plan 10-01)
    - frontend/src/lib/categories.ts (Plan 10-01)
    - frontend/src/lib/apiClient.ts (Plan 10-01)
    - frontend/src/lib/queryClient.ts (Plan 10-01)
    - frontend/src/store/authStore.ts (Plan 10-01)
    - frontend/src/lib/types.ts (Plan 10-01)
  provides:
    - frontend/src/App.tsx (provider stack + auth hydration)
    - frontend/src/router.tsx (React Router 7 createBrowserRouter, all 14 routes)
    - frontend/src/components/layout/Layout.tsx (shell wrapper)
    - frontend/src/components/layout/Header.tsx (sticky Turkish header, FE-02)
    - frontend/src/components/layout/CategoryNav.tsx (8-slug nav, PROD-03)
    - frontend/src/components/layout/Footer.tsx (FE-03 help links + payment icons)
    - frontend/src/components/layout/RequireAuth.tsx (D-08 auth route guard)
    - frontend/src/components/layout/RedirectIfAuthed.tsx (auth-page redirect)
    - frontend/src/components/feedback/ToastBridge.tsx (sonner Toaster)
    - frontend/src/components/feedback/AuthEventBridge.tsx (401 -> redirect + toast)
    - frontend/src/components/feedback/ErrorBoundary.tsx (Turkish fallback, T-10-09)
    - frontend/src/components/feedback/SkeletonCard.tsx (pulse placeholder)
    - frontend/src/api/cartApi.ts (fetchCart)
    - frontend/src/hooks/useCart.ts (useCart, useCartItemCount, cartQueryKey)
    - frontend/src/pages/HomePage.tsx (placeholder — Plan 10-04 fills)
    - frontend/src/pages/NotFoundPage.tsx (Turkish 404, FE-13)
    - frontend/src/pages/PlaceholderPage.tsx (wave-route holder)
  affects:
    - Plans 10-03 through 10-08 (drop page bodies into routes table without touching layout)
tech_stack:
  added: []
  patterns:
    - React Router 7 createBrowserRouter with nested layouts
    - RequireAuth/RedirectIfAuthed nested route guards
    - CustomEvent auth:unauthorized listener pattern (D-02)
    - sonner Toaster positioned top-right, 4s auto-dismiss, 3 visible
    - ErrorBoundary class component with Turkish fallback (T-10-09 mitigated)
    - TanStack Query useCart hook with placeholderData for anonymous users
key_files:
  created:
    - frontend/src/App.tsx
    - frontend/src/router.tsx
    - frontend/src/components/layout/Layout.tsx
    - frontend/src/components/layout/Header.tsx
    - frontend/src/components/layout/CategoryNav.tsx
    - frontend/src/components/layout/Footer.tsx
    - frontend/src/components/layout/RequireAuth.tsx
    - frontend/src/components/layout/RedirectIfAuthed.tsx
    - frontend/src/components/feedback/ToastBridge.tsx
    - frontend/src/components/feedback/AuthEventBridge.tsx
    - frontend/src/components/feedback/ErrorBoundary.tsx
    - frontend/src/components/feedback/SkeletonCard.tsx
    - frontend/src/api/cartApi.ts
    - frontend/src/hooks/useCart.ts
    - frontend/src/pages/HomePage.tsx
    - frontend/src/pages/NotFoundPage.tsx
    - frontend/src/pages/PlaceholderPage.tsx
  modified:
    - frontend/src/App.tsx (replaced placeholder with full provider stack)
decisions:
  - "ToastBridge props on single JSX line to satisfy grep gate (Toaster.*position.*top-right)"
  - "node_modules symlinked to main repo in worktree (worktree doesn't have its own node_modules)"
  - "useCart placeholderData returns empty Cart to avoid null check in useCartItemCount while query loads"
metrics:
  duration: "5 minutes"
  completed: "2026-05-01"
  tasks_completed: 3
  files_created: 17
  tests: "n/a (UI component tasks — build verification used)"
---

# Phase 10 Plan 02: App Shell, Header, Layout, Auth Guards Summary

React Router 7 data router wired with all 14 Turkish routes, sticky header (logo + search + account cluster + cart badge), category nav, footer, RequireAuth/RedirectIfAuthed guards, sonner toast surface, 401-redirect AuthEventBridge, and Turkish ErrorBoundary fallback.

## What Was Built

### Task 1: React Router 7 data router + Layout + RequireAuth (commit e44859c)

- `router.tsx`: `createBrowserRouter` with 22 total route nodes — 1 root Layout, 1 index, 8 category slugs via `CATEGORY_SLUGS.map`, 12 named routes (`arama`, `urun/:slugAndId`, `sepetim`, `giris-yap`, `uye-ol`, `odeme/adres`, `odeme/odeme`, `odeme/sonuc`, `hesabim`, `siparislerim`, `siparislerim/:orderId`, `adreslerim`), 1 catch-all
- `RequireAuth.tsx`: reads `isAuthenticated` from authStore; redirects to `/giris-yap?redirectUrl=<encodeURIComponent(pathname+search)>` when anonymous
- `RedirectIfAuthed.tsx`: redirects authenticated users away from `/giris-yap` and `/uye-ol` to `/`
- `Layout.tsx`: `<Header> + <CategoryNav> + <main><Outlet/></main> + <Footer> + <ToastBridge> + <AuthEventBridge>`
- `App.tsx`: `<ErrorBoundary><QueryClientProvider><RouterProvider>` + `useEffect(hydrateFromStorage)` on mount
- `NotFoundPage.tsx`: Turkish copy "Aradığınız sayfayı bulamadık." + "Ana Sayfaya Dön" CTA → `/`
- Stub components created to satisfy import graph while Task 2+3 land real implementations

### Task 2: Sticky Header + CategoryNav + Footer + cartApi + useCart (commit 4d606e9)

- `Header.tsx`: sticky `top-0 z-50`, 64px height; Logo → `/`; SearchBar with placeholder "Aradığınız ürün, kategori veya markayı yazınız" + `aria-label="Ara"` submit; AccountCluster (anonymous: Giriş Yap / Üye Ol; authed: Hesabım dropdown with Çıkış Yap + Sepetim with badge)
- Logout: `logout()` + `qc.clear()` + `navigate(HOME)` + `toast.success('Çıkış yapıldı.')` (D-03)
- Cart badge: live via `useCartItemCount()`, visible only when > 0, touch target ≥ 44px
- `CategoryNav.tsx`: `CATEGORY_SLUGS.map` → `NavLink` items; active state: `border-[#1C1C1E] font-bold text-[#1C1C1E]`
- `Footer.tsx`: 4-column grid (n11.com/Yardım/Müşteri Hizmetleri/Ödeme Yöntemleri), Visa/MasterCard/Troy/Amex icons, KVKK notice
- `cartApi.ts`: `fetchCart()` → `apiFetch<Cart>('/cart')`
- `useCart.ts`: exports `cartQueryKey`, `useCart()` (enabled only when authenticated, placeholderData = empty Cart), `useCartItemCount()` (reduces `item.qty`)

### Task 3: ToastBridge + AuthEventBridge + ErrorBoundary (commit ee6d8bf)

- `ToastBridge.tsx`: `<Toaster richColors position="top-right" duration={4000} visibleToasts={3} closeButton />`
- `AuthEventBridge.tsx`: mounts inside `<RouterProvider>`, listens for `auth:unauthorized` CustomEvent, calls `useAuthStore.getState().logout()`, shows D-02 toast "Oturum süreniz doldu, lütfen tekrar giriş yapın." (skipped if already on login/register pages), navigates to `/giris-yap?redirectUrl=<encoded>`, cleans up on unmount
- `ErrorBoundary.tsx`: class component, `getDerivedStateFromError` + `componentDidCatch(console.error)`, Turkish fallback UI "Bir hata oluştu" + "Sayfa yüklenirken..." + "Sayfayı Yenile" button; no stack trace in DOM (T-10-09 mitigated)

## Component Tree

```
App.tsx
  ErrorBoundary                 ← T-10-09: generic Turkish fallback, no stack leak
  QueryClientProvider
  RouterProvider (createBrowserRouter)
    /  → Layout
         Header                 ← FE-02: sticky, logo, search, account cluster, cart badge
         CategoryNav            ← PROD-03: 8 category slugs with active underline
         <main><Outlet /></main>
           RequireAuth          ← D-08: wraps /odeme/* + /hesabim + /siparislerim + /adreslerim
           RedirectIfAuthed     ← wraps /giris-yap + /uye-ol
         Footer                 ← FE-03: help links + payment icons
         ToastBridge            ← sonner Toaster, top-right, 4s, 3 visible
         AuthEventBridge        ← D-02: auth:unauthorized → logout + toast + redirect
```

## Final Route Table

| Route | Guard | Page | Ships in |
|-------|-------|------|----------|
| `/` | — | HomePage | 10-02 (skeleton) / 10-04 |
| `/elektronik` through `/kitap-muzik-film-oyun` (×8) | — | PlaceholderPage | 10-04 |
| `/arama` | — | PlaceholderPage | 10-04 |
| `/urun/:slugAndId` | — | PlaceholderPage | 10-05 |
| `/sepetim` | — | PlaceholderPage | 10-06 |
| `/giris-yap` | RedirectIfAuthed | PlaceholderPage | 10-03 |
| `/uye-ol` | RedirectIfAuthed | PlaceholderPage | 10-03 |
| `/odeme/adres` | RequireAuth | PlaceholderPage | 10-07 |
| `/odeme/odeme` | RequireAuth | PlaceholderPage | 10-07 |
| `/odeme/sonuc` | RequireAuth | PlaceholderPage | 10-07 |
| `/hesabim` | RequireAuth | PlaceholderPage | 10-08 |
| `/siparislerim` | RequireAuth | PlaceholderPage | 10-08 |
| `/siparislerim/:orderId` | RequireAuth | PlaceholderPage | 10-08 |
| `/adreslerim` | RequireAuth | PlaceholderPage | 10-08 |
| `*` | — | NotFoundPage | 10-02 |

## Manual Smoke (expected behavior)

- `/` → Layout renders with n11 logo header + 8-category nav + footer
- `/hesabim` (anonymous) → redirected to `/giris-yap?redirectUrl=%2Fhesabim`
- `/giris-yap` (authenticated) → redirected to `/`
- `/unknown` → NotFoundPage "Aradığınız sayfayı bulamadık." + "Ana Sayfaya Dön" CTA

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Adjusted ToastBridge JSX to single line for grep gate**
- **Found during:** Task 3 acceptance criteria run
- **Issue:** Plan acceptance criterion `grep -cE "Toaster.*position.*top-right|..."` requires Toaster and position on the same line; the multi-line JSX format failed the grep
- **Fix:** Moved all `<Toaster>` props onto one line (functionally identical)
- **Files modified:** `frontend/src/components/feedback/ToastBridge.tsx`
- **Commit:** ee6d8bf

**2. [Rule 3 - Blocking] Symlinked node_modules in worktree**
- **Found during:** Task 1 build verification
- **Issue:** Git worktrees share the Git object store but not `node_modules`. `tsc` not found because `frontend/node_modules` doesn't exist in the worktree path
- **Fix:** `ln -s /hey/projects/n11-bootcamp-final-case/frontend/node_modules frontend/node_modules` (symlink to main repo's installed deps)
- **Files modified:** none (symlink only)

## Known Stubs

The following are intentional wave-route placeholders — not data stubs that prevent the plan's goal:

| File | Reason |
|------|--------|
| `frontend/src/pages/PlaceholderPage.tsx` | Route slot holder; Plans 10-03 through 10-08 replace individual routes |
| `frontend/src/pages/HomePage.tsx` | Body placeholder; Plan 10-04 fills hero carousel + bestseller rail |

Both are documented in the plan as explicit wave placeholders. They do NOT prevent this plan's goal (routing, layout, auth guards, feedback layer) from being achieved.

## Threat Flags

No new threat surfaces beyond what the plan's `<threat_model>` covers. T-10-07, T-10-08, and T-10-09 mitigations are all present:
- T-10-07 (forged auth:unauthorized): event only clears token and redirects — fail-safe outcome
- T-10-08 (redirectUrl param manipulation): Plan 10-03 will enforce `^/[^/]` allowlist on redirectUrl in login form
- T-10-09 (ErrorBoundary stack leak): fallback UI shows generic Turkish copy only; stack logged to `console.error` only

## Self-Check: PASSED
