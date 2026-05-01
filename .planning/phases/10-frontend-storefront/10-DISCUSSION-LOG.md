# Phase 10: Frontend Storefront - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-01
**Phase:** 10-frontend-storefront
**Areas discussed:** Auth & token handling, Routing & URL conventions, Pagination UX style, Checkout flow shape

---

## Auth & Token Handling

### Q1 — Where does the JWT live in the browser?

| Option | Description | Selected |
|--------|-------------|----------|
| localStorage | Persists across tabs and restarts. Simplest for SPA + JWT-at-gateway. XSS risk acknowledged — mitigated via React's default escaping + no `dangerouslySetInnerHTML` + strict CSP from gateway. AUTH-03 'survives browser refresh' is satisfied for free. | ✓ |
| sessionStorage | Token tied to tab session — closing the tab logs you out. Lower XSS exposure (no cross-tab leak). Trade-off: AUTH-03 'survives refresh' still works (refresh keeps tab), but multi-tab feels weird. | |
| In-memory only (Zustand state, no persist) | Most secure (no XSS readable storage). Trade-off: every page refresh logs you out — AUTH-03 fails unless we add a refresh-token endpoint (which Phase 3 didn't ship). Out of Phase 10 scope. | |

**User's choice:** localStorage (Recommended)
**Notes:** AUTH-03 satisfied without backend refresh-token work; v1 trade-off accepted.

### Q2 — When the gateway returns 401 (token expired or rejected), what does the user see?

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-redirect to /giris-yap + Turkish toast | Interceptor catches 401 → clears token → redirects to /giris-yap with toast 'Oturum süreniz doldu, lütfen tekrar giriş yapın.' Stores attempted URL for post-login redirect-back. | ✓ |
| Inline modal re-auth | Pops a modal in-place asking for password re-entry; on success, re-tries the failed request silently. Smoother UX, but more code. | |
| Silent clear, stay on page | Clear token, let the user discover via empty cart / 'giriş yap' CTAs. Simplest, worst UX. | |

**User's choice:** Auto-redirect to /giris-yap + Turkish toast (Recommended)
**Notes:** Pairs with `?redirectUrl=` carry-forward decision in Routing area below.

### Q3 — What does AUTH-04 'log out from any page' do?

| Option | Description | Selected |
|--------|-------------|----------|
| Clear token + redirect to homepage with toast | Frontend-only logout — wipe localStorage + Zustand auth state + TanStack Query cache, redirect to '/', show 'Çıkış yapıldı' toast. No backend /logout endpoint needed. | ✓ |
| Clear + redirect to /giris-yap | After logout, immediately show the login form. Cleaner if logout is rare. | |
| Clear + stay on current page | Just wipe state, no redirect. User stays on PDP/listing/cart and sees the anonymous version. | |

**User's choice:** Clear token + redirect to homepage with toast (Recommended)
**Notes:** Phase 3 didn't ship `/logout`; v1 frontend-only is sufficient.

### Q4 — On app load with a stored JWT, how do we validate it before showing authed UI?

| Option | Description | Selected |
|--------|-------------|----------|
| Decode exp claim client-side | Parse JWT (using jose or jwt-decode), check `exp` against `Date.now()`. If expired, clear token immediately. No network call. Fast. Server still validates on first authenticated request. | ✓ |
| Call GET /api/v1/identity/auth/me on boot | Hits identity-service to confirm the token is valid AND fetch fresh user data. One round-trip on every page load. Cleanest but adds latency. | |
| No upfront check — trust until 401 | Just keep the token and assume valid. First protected request that fails triggers the 401 flow. Lazy. | |

**User's choice:** Decode exp claim client-side (Recommended)
**Notes:** Identity (email/roles/name) fetched lazily via `/auth/me` only when account UI renders.

---

## Routing & URL Conventions

### Q1 — Overall URL slug posture — how Turkish should the URLs be?

| Option | Description | Selected |
|--------|-------------|----------|
| Full Turkish mirror | All routes Turkish, n11-faithful: /sepetim, /giris-yap, /uye-ol, /hesabim, /siparislerim, /urun/:slug-:id, /odeme, /adreslerim. Code identifiers stay English. | ✓ |
| Hybrid (English app surface, Turkish only for product paths) | App routes English: /cart, /login, /register, /account, /orders, /checkout. Product paths Turkish (locked): /urun/:slug-:id. | |
| English everywhere except FE-07 lock | Only /urun/:slug-:id is Turkish; everything else English. | |

**User's choice:** Full Turkish mirror (Recommended)
**Notes:** Recon-faithful, credibility win, demos as 'a real Turkish e-commerce site'.

### Q2 — Category listing URL pattern — where does the elektronik listing live?

| Option | Description | Selected |
|--------|-------------|----------|
| Root-level slug: /elektronik | Mirrors n11 exactly. Recon evidence: n11 uses this verbatim. Trade-off: namespace can collide with future top-level routes. | ✓ |
| Namespaced: /kategori/elektronik | Clear separation between content and app surface. No collision risk. Less n11-faithful. | |
| URL param: /urunler?kategori=elektronik | Single listing page, category as a filter. Less SEO/feel-friendly. | |

**User's choice:** Root-level slug: /elektronik (Recommended)
**Notes:** 8 top-level slugs reserved against future top-level routes; planner avoids /yardim etc. without checking the slug list.

### Q3 — Header search bar (FE-02) submits to which URL?

| Option | Description | Selected |
|--------|-------------|----------|
| /arama?q=... | Dedicated search results page in Turkish. Reuses listing grid component with q= param. Bookmarkable. | ✓ |
| /urunler?q=... | Search is just a filter on the products listing page. URL gets longer when combined with category filter. | |
| /search?q=... (English) | Less Turkish-faithful. | |

**User's choice:** /arama?q=... (Recommended)
**Notes:** Empty-state copy 'Aramanız için sonuç bulunamadı' + 'Diğer kategorilere göz at' CTA.

### Q4 — When an anonymous user clicks an auth-required nav item, what happens?

| Option | Description | Selected |
|--------|-------------|----------|
| Redirect to /giris-yap?redirectUrl=/hesabim | Recon-faithful — n11 does exactly this (recon §8 confirmed). On successful login, redirect back to originally-clicked page. | ✓ |
| Show the page with inline 'Giriş Yap' CTA in place of content | Less navigation but feels weird for /siparislerim and /hesabim. | |
| Hybrid: /sepetim shows inline CTA; /hesabim and /siparislerim redirect | Most-faithful to n11 but two patterns to maintain. | |

**User's choice:** Redirect to /giris-yap?redirectUrl=/hesabim (Recommended)
**Notes:** /sepetim is anonymous-allowed (cart-service requires X-User-Id, so anonymous sees empty cart + 'Hemen Giriş Yap' CTA in the empty state).

---

## Pagination UX Style

### Q1 — Pagination component shape on listing pages?

| Option | Description | Selected |
|--------|-------------|----------|
| Numbered pages with ellipsis: 1 2 3 ... 47 | n11-faithful (recon §6 confirms). 'Önceki / 1 2 3 ... 47 / Sonraki' with current page highlighted. ~30-line component. | ✓ |
| Prev/Next-only with 'Sayfa 2 / 47' indicator | Minimal shape. Just 'Önceki' and 'Sonraki' buttons + 'Sayfa 2 / 47' text. Brief floor. | |
| Infinite scroll / 'Daha Fazla Yükle' button | Modern feel. Trade-off: URL-bookmarkable page state is lost; jump-to-end impossible. Less brief-faithful. | |

**User's choice:** Numbered pages with ellipsis (Recommended)
**Notes:** Reusable `<Pagination>` component; current page rendered with primary-CTA color (#1C1C1E).

### Q2 — How does the page+sort+filter state encode in the URL?

| Option | Description | Selected |
|--------|-------------|----------|
| Turkish params, frontend translates: ?sayfa=2&siralama=fiyat-artan | Consistent with full-Turkish-mirror slug posture. Frontend has a tiny mapping layer that converts ?sayfa=2 → backend ?page=1 (note Spring's 0-based!) and ?siralama=fiyat-artan → ?sort=price_gross,asc. | ✓ |
| Pass-through English: ?page=2&sort=price_gross,asc | Frontend passes URL params straight to product-service. URLs leak Spring Data conventions. | |
| Hybrid: ?sayfa= but pass numeric value as-is | Confusing. Avoid. | |

**User's choice:** Turkish params, frontend translates (Recommended)
**Notes:** ~20-line URL adapter helper; sort key dictionary fixed (tarih-yeni/fiyat-artan/fiyat-azalan); critical: visiblePage − 1 = backendPage.

### Q3 — Default sort order when user lands on /elektronik or /arama?q=...?

| Option | Description | Selected |
|--------|-------------|----------|
| Newest first (date desc) | Standard e-commerce default. PROD-05 includes 'date (newest first)'. Maps to backend ?sort=created_at,desc. | ✓ |
| Price ascending | Cheapest-first feels bargain-focused. Less natural. | |
| Backend default (whatever Spring Data picks) | Risky: order could be unstable across requests. Avoid. | |

**User's choice:** Newest first (date desc) (Recommended)

### Q4 — Items per listing page — fixed or user-selectable?

| Option | Description | Selected |
|--------|-------------|----------|
| Fixed 20 | PROD-01 default. Matches grid: 4 columns × 5 rows on desktop = 20 cards. No selector UI to design. | ✓ |
| Fixed 24 | 4 × 6 fits taller laptops. Same simplicity. | |
| User-selectable selector (20 / 40 / 60) | Adds 'Gösterim' dropdown above grid. More UX surface. n11 doesn't expose this. | |

**User's choice:** Fixed 20 (Recommended)
**Notes:** Backend supports ?size= even if frontend doesn't expose it (PROD-01 'configurable size').

---

## Checkout Flow Shape

### Q1 — FE-09 multi-step (address → payment → confirmation) — how is the step machine implemented?

| Option | Description | Selected |
|--------|-------------|----------|
| Multi-route /odeme/adres → /odeme/odeme → /odeme/onay | Each step is its own route. Bookmarkable, refresh-safe. React Router 7 nested routes naturally express this. | ✓ |
| Single-page wizard /odeme with stepper component | One route /odeme. Step state lives in Zustand. Browser back exits checkout. Refresh resets to step 1. | |
| Progressive disclosure on /sepetim | No separate checkout page. Cart page expands inline. Cart page becomes very long; mobile UX awkward. | |

**User's choice:** Multi-route stepper (Recommended)
**Notes:** Each step's loader validates previous-step state in Zustand; lands on /odeme/odeme without /odeme/adres → redirect.

### Q2 — Address step UX — how does the user pick a delivery address?

| Option | Description | Selected |
|--------|-------------|----------|
| Dropdown of saved addresses + 'Yeni adres ekle' inline form | Pre-populated radio cards of existing addresses (AUTH-08). 'Yeni adres ekle' reveals inline form that POSTs to /api/v1/identity/addresses. | ✓ |
| Force address book — redirect to /adreslerim if empty | If user has no saved addresses, send them to /adreslerim with a callback. More clicks for first-time users. | |
| Inline form only (no saved-address picker for v1) | Always show a fresh address form. Loses the AUTH-08 address-book value. Avoid. | |

**User's choice:** Dropdown of saved addresses + 'Yeni adres ekle' inline form (Recommended)

### Q3 — After Iyzico redirect, how does the frontend detect success/failure?

| Option | Description | Selected |
|--------|-------------|----------|
| Dedicated /odeme/sonuc?orderId=... that polls order status | Iyzico's callbackUrl points here. Spinner + polls GET /orders/{id} every 2s up to 30s. | ✓ |
| Immediate jump to /siparislerim/:orderId, let status timeline show progress | Iyzico callbackUrl jumps straight to order detail. Risk: user lands on PENDING for 1-3 seconds. | |
| /odeme/onay confirmation page that auto-refreshes order status | Same as option (a) but URL fits the multi-step pattern naming. | |

**User's choice:** Dedicated /odeme/sonuc?orderId=... that polls order status (Recommended)
**Notes:** 30s timeout falls back to 'İşleminiz hâlâ kontrol ediliyor' with auto-retry button + link to /siparislerim/:orderId.

### Q4 — When payment fails, what does the user see?

| Option | Description | Selected |
|--------|-------------|----------|
| Same /odeme/sonuc shows 'Ödeme Başarısız' with retry to /sepetim | One result page handles success and failure. Failure card 'Ödemeniz alınamadı' + 'Tekrar Dene' button returns to /sepetim with cart preserved. | ✓ |
| Redirect to /sepetim with toast 'Ödemeniz başarısız oldu' | Skip the result page on failure. Slightly less informative. | |
| Dedicated /odeme/basarisiz page | Explicit failure URL. Cleanest separation but adds another route. | |

**User's choice:** Same /odeme/sonuc shows 'Ödeme Başarısız' with retry to /sepetim (Recommended)
**Notes:** Saga compensation handles inventory release server-side; cart line items preserved per Phase 5 (verify with planner).

---

## Claude's Discretion

The user did not defer any specific questions to Claude during the discussion (every selection was the recommended option). The "Claude's Discretion" section in CONTEXT.md was populated by Claude proactively for the layer-below decisions that don't change UX outcomes:

- Auth implementation library (jose vs jwt-decode)
- HTTP client choice (fetch vs ky vs axios)
- Toast library (sonner vs react-hot-toast)
- Token store helper module location
- Register-then-login behavior (auto-login recommended)
- Post-login redirect-back implementation
- 404 / not-found page Turkish copy
- Mobile breakpoints
- Page-out-of-range handling (clamp)
- Hero carousel mechanic + content (3-4 static slides; auto-advance + manual dots)
- Bestseller rail data source (reuse /products?sort=created_at,desc)
- Phase 11 chat-bubble placeholder posture (leave nothing)
- Image strategy for seed products
- Form validation tone (inline below field)
- Empty-state copy (recon-faithful Turkish voice)
- Skeleton loader visual style (Tailwind animate-pulse)
- Test posture (Vitest unit + smoke Playwright E2E)
- Tailwind 4 @theme paste from recon §4
- Open Sans font loading via @fontsource/open-sans
- Status-timeline copy (saga state → Turkish label mapping)
- Locale formatters (single src/lib/format.ts)

## Deferred Ideas

The user did not raise scope-creep ideas during the discussion. The deferred-ideas section in CONTEXT.md was populated from REQUIREMENTS Out-of-Scope, the v2 Requirements section, and natural extensions implied by the locked decisions:

- Refresh-token endpoint (v2)
- Items-per-page selector (v2)
- Inline modal re-auth on 401
- Backend /logout endpoint
- Hero carousel polish
- Sub-category drill-down navigation
- "Çok Satanlar" backed by real "ordered most" query (FE-V2-04)
- "Kargo Bedava" badge on every listing card (FE-V2-02)
- Kapıda Ödeme working payment method (FE-V2-03)
- Mid-stream chat cancellation (FE-V2-01, Phase 11)
- Empty-state illustrations
- Reviews / ratings (Out of Scope)
- Recommendations panel (Out of Scope)
- Newsletter / blog (Out of Scope)
- Address book CRUD polish
- Real shipping carrier integration (Out of Scope)
- A11y audit pass (v2)
