# Phase 10: Frontend Storefront - Context

**Gathered:** 2026-05-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Build the Turkish React storefront — every visible page a user navigates to before opening the chat bubble (which is Phase 11). Phase 10 owns:

1. **Vite 8 + React 19 SPA scaffold** in `frontend/` — `npm create vite@latest frontend -- --template react-ts`, then layered with Tailwind 4 (`@theme` block from recon §4), Zustand 5, React Router 7, TanStack Query 5, react-hook-form 7 + zod 4, `@fontsource/open-sans` (recon §5).
2. **All visible pages** — header / footer / hero carousel / category nav / paginated listing / PDP / cart / multi-step checkout / account hub / Siparişlerim / Adreslerim / login / register. All copy Turkish (recon §2 verbatim where applicable). KDV-inclusive prices, taksit preview, `Intl.NumberFormat('tr-TR', ..., 'TRY')` rendering, `tr-TR` date locale.
3. **Auth lifecycle on the frontend** — AUTH-03 (token persistence across refresh) + AUTH-04 (logout from any page) — explicit Phase 3 → Phase 10 deferrals.
4. **Gateway-only API integration** — every request goes to `${VITE_API_BASE_URL}/api/v1/...`; no direct service URLs. RFC-7807 problem+json error shape rendered as Turkish toast / inline messages (FE-15).
5. **Iyzico hosted-page handoff** — Phase 6 ships `paymentPageUrl`; Phase 10 redirects users to it and lands them on `/odeme/sonuc` post-3DS.

Phase 10 does **not** own:
- The floating chat-assistant bubble (Phase 11 — `<frontend chat assistant + DevOps deploy>`)
- Jib image / GitHub Actions / docker-compose / Slack webhook / Cloudflare Tunnel (Phase 11 DevOps half)
- Backend endpoints (all locked by Phases 3–8)
- Real semantic search UI (AI-V2 / chat-only in v1)
- Reviews, ratings, recommendations, newsletter, blog (Out of Scope)

</domain>

<decisions>
## Implementation Decisions

### Auth & Token Handling

- **D-01:** **JWT lives in `localStorage`** under key `n11_auth_token`. Persists across tabs and restarts; AUTH-03 satisfied without backend refresh-token work. XSS mitigation: React's default escaping + zero `dangerouslySetInnerHTML` in the codebase + strict CSP header from gateway (planner wires the gateway header if not already present). Token is never logged. `localStorage` reads/writes go through a single `tokenStore` helper module — no scattered `localStorage.getItem('n11_auth_token')` calls.
- **D-02:** **401 → auto-redirect to `/giris-yap` with Turkish toast.** A single fetch/axios interceptor (TanStack Query default fetch wrapper or whichever client the planner picks) catches HTTP 401 from the gateway, clears `tokenStore`, navigates to `/giris-yap?redirectUrl=<currentPath>`, and shows a sonner-style toast: **"Oturum süreniz doldu, lütfen tekrar giriş yapın."** Login page reads `?redirectUrl=` and post-login routes back. Identical handling whether token expired or signing key rotated — the user experience is the same.
- **D-03:** **Logout = clear + redirect to `/` with toast.** `logout()` in the auth store wipes `localStorage` token, resets Zustand auth slice, calls `queryClient.clear()` to drop authed cache entries, navigates to `/`, shows toast **"Çıkış yapıldı."** No backend `/logout` endpoint required — Phase 3 didn't ship one and we don't need server-side revocation in v1. Logout button is in the account dropdown in the header right cluster.
- **D-04:** **Boot validation = decode `exp` claim client-side.** On app mount, read token from `localStorage`; if present, decode (use `jose` package's `decodeJwt` or `jwt-decode` npm — planner picks; both are tiny). If `exp * 1000 <= Date.now() + 5_000` (5-second safety margin matching Phase 3's 30s clock-skew tolerance), clear token immediately and treat as anonymous. **No** `/auth/me` round-trip on boot — server still validates on the first authenticated request, belt-and-braces. User identity (email, roles, name) is fetched lazily via `/auth/me` only when account UI renders.

### Routing & URL Conventions

- **D-05:** **Full Turkish slug mirror.** All routes are Turkish, n11-faithful. Code identifiers, component names, route constant keys, file names all stay English (per CLAUDE.md non-negotiable). Canonical map:

  | Page | Route | Source |
  |---|---|---|
  | Homepage | `/` | — |
  | Category listing | `/elektronik`, `/moda`, `/ev-yasam`, `/anne-bebek`, `/kozmetik`, `/spor-outdoor`, `/supermarket`, `/kitap-muzik-film-oyun` | PROD-03 + recon §3 (root-level slug) |
  | Search results | `/arama?q=...` | D-07 |
  | PDP | `/urun/:slug-:id` | FE-07 lock |
  | Cart | `/sepetim` | recon §2 verbatim |
  | Checkout (3 steps) | `/odeme/adres`, `/odeme/odeme`, `/odeme/onay` | D-13 |
  | Iyzico return | `/odeme/sonuc?orderId=...` | D-15 |
  | Login | `/giris-yap` | recon §8 carry-forward (canonical, NOT `/giris`) |
  | Register | `/uye-ol` | recon §2 phrase #2 verbatim |
  | Account hub | `/hesabim` | recon §2 phrase #633 |
  | Orders list | `/siparislerim` | recon §2 phrase #632 (top-level, not nested under /hesabim) |
  | Order detail | `/siparislerim/:orderId` | extends D-05 |
  | Address book | `/adreslerim` | extends D-05 |
  | 404 / not-found | `*` catch-all → custom `<NotFound>` page in Turkish | planner discretion |

- **D-06:** **Category listing at root-level slug** (e.g., `/elektronik` not `/kategori/elektronik`). Mirrors n11 verbatim. The 8 top-level slugs are reserved against future top-level routes (we don't add `/yardim` etc. without checking the slug list).
- **D-07:** **Header search submits to `/arama?q=...`** — dedicated search results page. Reuses the listing grid component with the `q=` query param mapped to product-service's `?q=`. Fully shares pagination / sort / breadcrumb logic with category listing. Empty-state ("Aramanız için sonuç bulunamadı") + "Diğer kategorilere göz at" CTA when zero results.
- **D-08:** **Auth-required nav redirects with `?redirectUrl=`.** Anonymous click on Hesabım, Siparişlerim, Adreslerim, or `/odeme/*` → React Router 7 route loader/guard intercepts → redirects to `/giris-yap?redirectUrl=<originalPath>`. Login page reads param, post-login `navigate(redirectUrl ?? '/')`. **Cart `/sepetim` is anonymous-allowed** (Phase 5 cart-service requires `X-User-Id`, so anonymous cart is empty + a "Sepete eklemek için giriş yap" CTA in the empty state — planner discretion on the exact copy, recon §2 phrase #76 "Hemen Giriş Yap" is canonical).

### Pagination UX

- **D-09:** **Numbered pages with ellipsis.** "Önceki / 1 2 3 ... 47 / Sonraki" — recon §6 confirms n11 ships this. Reusable `<Pagination>` component (~30-line collapse logic: show first/last/current ± 2). Current page rendered with primary-CTA color (`#1C1C1E`); other pages link-styled. Component lives at the bottom of the listing grid. Matches FE-06 + FE-14 (accessible keyboard nav).
- **D-10:** **Turkish URL params with frontend-side translation.** User-facing URL: `?sayfa=2&siralama=fiyat-artan&kategori=elektronik`. A small URL adapter helper translates to product-service: `?page=1&sort=price_gross,asc&categoryId=<id>`. Critical: Spring Data is 0-based, visible page numbers are 1-based — the adapter handles `visiblePage − 1 = backendPage`. Sort key dictionary:

  | Visible (`?siralama=`) | Backend (`?sort=`) |
  |---|---|
  | `tarih-yeni` (default) | `created_at,desc` |
  | `fiyat-artan` | `price_gross,asc` |
  | `fiyat-azalan` | `price_gross,desc` |

  PROD-05 requires only these three.

- **D-11:** **Default sort = newest first.** When no `?siralama=` in URL, frontend treats it as `tarih-yeni` and sends `?sort=created_at,desc` to backend. Standard e-commerce default.
- **D-12:** **Items per page = fixed 20.** PROD-01 spec default. Maps to backend `?size=20`. 4 columns × 5 rows on the desktop 1440px breakpoint (recon §6). No selector UI in v1 (Tier 1 floor); deferred to v2 if grading feedback asks.

### Checkout Flow Shape

- **D-13:** **Multi-route stepper with React Router 7 nested routes.** Three steps, three URLs:
  1. `/odeme/adres` — address picker (D-14)
  2. `/odeme/odeme` — payment method picker; "Kredi Kartı" + disabled "Kapıda Ödeme" radio (FE-V2-03 deferral, Pitfall #11 mitigation). Submit triggers `POST /api/v1/orders` with `addressId`, `paymentMethod`, and a fresh UUID `Idempotency-Key` header (Phase 5 ORD contract). Then frontend fetches `paymentPageUrl` (Phase 6 endpoint) and `window.location.assign(paymentPageUrl)` redirects to Iyzico's hosted page.
  3. `/odeme/onay` — only used as a transient confirmation if needed; the actual post-payment landing is `/odeme/sonuc` (D-15).

  Step indicator strip at the top of all three steps shows progress ("Adres › Ödeme › Onay"). Bookmarkable, refresh-safe. Each step's loader validates the previous step's state lives in Zustand; if a user lands on `/odeme/odeme` without passing through `/odeme/adres`, redirect to `/odeme/adres`.

- **D-14:** **Address step UX = saved addresses + inline new-address form.** Top of `/odeme/adres` shows radio cards of existing addresses (from `GET /api/v1/identity/addresses`); each card displays "Mahalle / İlçe / İl" per AUTH-08. "Yeni adres ekle" button reveals an inline form (react-hook-form + zod schema with Turkish error messages) that POSTs to `/api/v1/identity/addresses` and auto-selects the new address on success. Validation: Mahalle/İlçe/İl required, Türkiye-only.
- **D-15:** **Iyzico return URL = `/odeme/sonuc?orderId=<id>`.** The frontend includes this URL when initializing checkout (Phase 6 contract: payment-service uses `PUBLIC_BASE_URL` for the webhook callback; the user-redirect target is the frontend origin + `/odeme/sonuc`). This is the page the user's browser lands on post-3DS. The page polls `GET /api/v1/orders/{orderId}` every 2s, max 30s. Outcomes:
  - **Status `CONFIRMED`** within 30s → success card: "Siparişiniz alındı 🎉 Sipariş No: {id}" + CTA "Siparişimi Gör" → `/siparislerim/:orderId`. Cart auto-cleared (TanStack Query invalidation since Phase 5 publishes `cart.q.order-confirmed` and cart-service handles it server-side).
  - **Status `CANCELLED` / `FAILED`** → render failure variant per D-16.
  - **30s timeout, still `PENDING` / `STOCK_RESERVED`** → render "İşleminiz hâlâ kontrol ediliyor" with auto-retry button + link to `/siparislerim/:orderId` so user can monitor saga.

  **No emoji** in production copy unless the user asks; the success-card emoji above is a placeholder for the planner — strip if reviewing the copy contract.

- **D-16:** **Payment failure UX = same `/odeme/sonuc` page in failure variant.** When polling reveals `CANCELLED` / `FAILED`, page renders: heading "Ödemeniz alınamadı", body "Lütfen tekrar deneyiniz veya farklı bir kart kullanınız.", CTA "Tekrar Dene" → `/sepetim`. Cart line items are preserved server-side (saga compensation via `payment.failed` releases inventory but does NOT clear cart per Phase 5 — verify with planner). Inline support hint: "Sorun devam ederse `iletişim` sayfamızdan bize ulaşabilirsiniz." (planner skips the link if /iletişim is out of scope; replace with Turkish toast).

### Claude's Discretion

The following are deferred to the planner / researcher and do not require user input:

- **Auth implementation library** — `jose` vs `jwt-decode` for client-side decode (D-04); both work, planner picks the lighter one
- **HTTP client** — fetch + TanStack Query default vs ky vs axios. TanStack Query is locked, the underlying client is planner choice (fetch is enough; axios is extra dep)
- **Toast library** — sonner vs react-hot-toast vs custom; whichever has the lightest dep weight
- **Token store helper module location** — likely `src/lib/auth/tokenStore.ts`; planner shape
- **Register-then-login behavior** — register endpoint returns 201, then frontend immediately logs in (POST /auth/login with same credentials), or asks user to log in afterwards. Lean toward auto-login for UX
- **Post-login redirect-back implementation** — `useSearchParams()` + `navigate(redirectUrl ?? '/')`; trivial
- **404 / not-found page copy** — Turkish, simple, "Aradığınız sayfayı bulamadık" + link home
- **Mobile breakpoints** — Tailwind 4 defaults are fine; recon §6 captured at 1440px desktop, planner derives mobile (4 cols → 2 cols → 1 col responsive)
- **Page-out-of-range handling** — if `?sayfa=999` exceeds total, clamp to last page (don't 404); product-service returns empty array, frontend renders empty state + auto-redirect to last valid page
- **Hero carousel mechanic** — 3-4 static slides per FE-04; auto-advance every 5s with manual dots; can be a static 3-tile grid if carousel feels heavy. Generic Turkish promo copy ("Elektronikte Fırsatlar", "Anne & Bebek Yenileri") — no fake percent-discounts (recon anti-pattern flags)
- **Bestseller rail (FE-05) data source** — REQUIREMENTS says "single endpoint, hard-coded seed for v1". Pick simplest: reuse `GET /api/v1/products?sort=created_at,desc&size=10` and label the rail "Yeni Gelenler" or "Çok Satanlar" on the homepage. Don't add a new endpoint
- **Phase 11 chat-bubble placeholder** — leave **nothing**. Phase 11 plops the bubble onto a stable storefront. No dummy div, no reserved slot, no "Coming Soon" card
- **Image strategy for seed products** — Phase 4 PROD-09 ships `image URL` field. Planner picks: placeholder service (`https://picsum.photos/...`), committed dummy assets in `frontend/public/`, or per-product Pexels-style stock URLs. Avoid hotlinking n11 images (legally questionable)
- **Form validation tone** — react-hook-form + zod with Turkish error messages via zod's `errorMap`. Inline below each field; no top-of-form banner. Specific copy planner-discretion ("E-posta zorunludur", "Şifre en az 8 karakter olmalı", etc.)
- **Empty-state copy** — recon §2 phrase #73 "Sepetin Boş Görünüyor" verbatim for `/sepetim`. Other empty states ("Aramanız için sonuç bulunamadı", "Henüz siparişiniz yok", "Henüz adresiniz yok") — planner uses recon-faithful Turkish voice
- **Skeleton loader visual style** — Tailwind 4 `animate-pulse` on gray-200 placeholder blocks for FE-15. Shimmer-style is overkill
- **Test posture** — Vitest unit tests on the URL adapter (D-10), `tokenStore` helper, currency formatter, and a smoke Playwright E2E for "land on /elektronik → click product → add to cart → log in → checkout → land on /odeme/sonuc". Don't chase coverage; the demo flow IS the test
- **Tailwind 4 `@theme` color paste** — recon §4 token table gets pasted verbatim into `frontend/src/index.css`. CTA primary = `#1C1C1E` (NOT orange — recon-locked)
- **Open Sans font loading** — `@fontsource/open-sans` per recon §5 carry-forward; install + import in `main.tsx`
- **Status-timeline copy** — "Sipariş Alındı → Hazırlanıyor → Kargoya Verildi → Teslim Edildi" (ORD-04). Map saga states: PENDING/STOCK_RESERVED → "Sipariş Alındı"; PAID → "Hazırlanıyor"; CONFIRMED → "Kargoya Verildi" (mock — no real shipping in v1; v1 stops here); FAILED/CANCELLED → "İptal Edildi"
- **Locale formatters** — single `src/lib/format.ts` exports `formatTRY(amount)` (Intl.NumberFormat) and `formatTRDate(d)` (Intl.DateTimeFormat). All currency / date renders go through these (LOC-02, LOC-05)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Scope and Requirements

- `.planning/ROADMAP.md` — Phase 10 entry: goal, dependencies (Phase 2 + Phase 6), success criteria (5/5), risks, research need (LOW).
- `.planning/REQUIREMENTS.md` — FE-01 (toolchain, complete in Phase 2) through FE-16 + LOC-01..05 + AUTH-03/04 (Phase 3 → Phase 10 deferrals) + AUTH-08 (address book in checkout).
- `.planning/PROJECT.md` — "Frontend toolchain: Vite 8 + React 19 SPA + ..." Key Decisions row (locked Phase 2); "Localization" Constraint row; Out-of-Scope rows ("Multiple LLM providers", "Reviews and ratings", "Mobile app / native clients", "Real-time WebSocket cart sync"); "Carry-forward to Phase 10" note (`VITE_API_BASE_URL` env var, no hardcoded URLs — Pitfall #23 prevention).
- `.planning/STATE.md` — handoff after Phase 8; Validated row "JWT-based authentication (validated at API gateway)" with note "AUTH-03 + AUTH-04 deferred to Phase 10".
- `CLAUDE.md` — Non-negotiable Rule #6 (Frontend Turkish, identifiers English); deploy target = local docker-compose; AI library split note (Phase 11 not Phase 10).

### Design Contract (the most important Phase 10 artifact)

- `.planning/intel/n11-recon.md` — **THE design contract**. Read end-to-end before planning.
  - §1 Page Inventory + screenshot paths
  - §2 Turkish Copy Catalog (644 phrases, used verbatim — Pitfall #20 prevention)
  - §3 Category Taxonomy (8 top-level slugs locked to PROD-03)
  - §4 Color Token Table (25 tokens; CTA primary = `#1C1C1E`, NOT orange)
  - §5 Typography Notes (Open Sans / Arial / Helvetica; `@fontsource/open-sans` carry-forward)
  - §6 Layout Patterns (sticky header, 4-col grid, items-left/summary-right cart, image-left/info-right PDP, breadcrumbs on PDP)
  - §7 Anti-pattern flags (NO countdown timers, NO sticky-pricing dark patterns, NO 5-card coupon stack, NO promo-banner overload)
  - §8 Open n11 questions (PDP "Son N ürün!" out of scope, Kapıda Ödeme disabled radio, login path = `/giris-yap` not `/giris`)
  - Decision Matrix subsection (Vite 65 / Next 45 — locks toolchain)
- `.planning/intel/screenshots/` — 7 fullpage captures (homepage, category-elektronik, pdp, cart, checkout-step1, account, login) + 3 element-zooms (homepage-header, cart-cart, pdp-cta, account-login). Read directly when designing each page.

### API Contracts and Backend Surface

- `.planning/api-contracts.md` §1 (gateway routing — every `/api/v1/*` route the frontend talks to), §3 (public allowlist — anonymous browsing, login/register, search), §4 (`Authorization` strip + `X-User-Id`/`X-User-Roles` injection — frontend ALWAYS sends `Authorization: Bearer <token>`, never `X-User-Id`), §5 (correlation ID propagation), §7 (RFC-7807 problem+json error shape — frontend renders `detail` field as Turkish toast, falls back to `title` if `detail` empty).
- `.planning/saga-contracts.md` — saga event names (referenced in order timeline; frontend doesn't subscribe).

### Prior-Phase Contexts (read sections relevant to Phase 10 only)

- `.planning/phases/03-identity-gateway-auth/03-CONTEXT.md` — JWT shape (RS256, 24h, claims `sub`/`email`/`roles`); `POST /api/v1/identity/auth/login` + `register`; `GET /api/v1/identity/auth/me`; `GET /POST /api/v1/identity/addresses` (AUTH-08); `/.well-known/jwks.json` (gateway-only consumer, frontend doesn't read).
- `.planning/phases/04-catalog-inventory/` (no CONTEXT.md per filesystem; reference ROADMAP Phase 4 entry) — `GET /api/v1/products?page=&size=&sort=&q=&categoryId=`; `GET /api/v1/products/{id}`; `GET /api/v1/inventory/{productId}`; ILIKE+GIN search; sort by `price_gross` / `created_at`; pagination 0-based per Spring Data.
- `.planning/phases/05-cart-order-skeleton/05-CONTEXT.md` — cart-service REST shape (`GET /cart`, `POST /cart/items`, `PUT /cart/items/{productId}`, `DELETE /cart/items/{productId}`); order-service `POST /orders` requires `Idempotency-Key` UUID header per checkout attempt; saga state timeline (PENDING → STOCK_RESERVED → PAID → CONFIRMED, plus FAILED/CANCELLED branches); cart auto-clears on `order.confirmed` event server-side; `cart.q.order-confirmed` consumer pattern.
- `.planning/phases/06-payment-iyzico/06-CONTEXT.md` — `paymentPageUrl` exposure (D-07: hosted page only, no embedded SDK); `PUBLIC_BASE_URL` is for the **webhook**; the user-redirect target is the **frontend origin** + `/odeme/sonuc` — verify this seam with the planner; payment retrieve-driven status finalization; payment-fail compensation releases inventory.
- `.planning/phases/08-ai-port-adapter-agent-toolset/08-CONTEXT.md` — chat backend lives at `/api/v1/chat/**` (Phase 11 frontend consumer); Phase 10 leaves no chat-bubble placeholder.

### External Library Documentation (verify-before-implement, CLAUDE.md Rule #4)

- `https://react.dev/` — React 19 (Suspense, transitions, useOptimistic for cart updates, useActionState for forms).
- `https://reactrouter.com/` — React Router 7 (data routers, nested routes, lazy loading, `useSearchParams`, route loaders, action functions).
- `https://tanstack.com/query/latest` — TanStack Query 5 (`useQuery`, `useMutation`, `useInfiniteQuery` if pivoting from D-09, query invalidation patterns).
- `https://zustand.docs.pmnd.rs/` — Zustand 5 (`create`, `persist` middleware NOT for auth token).
- `https://tailwindcss.com/docs/v4-beta` — Tailwind 4 (`@theme` directive, no config file, `@import "tailwindcss"` in `index.css`).
- `https://react-hook-form.com/` — react-hook-form 7 (Controller, useForm, register).
- `https://zod.dev/` — zod 4 (schema validation, `errorMap` for Turkish messages).
- `https://www.npmjs.com/package/@fontsource/open-sans` — Open Sans font self-hosted bundle.
- `https://github.com/panva/jose` or `https://www.npmjs.com/package/jwt-decode` — client-side JWT decode (D-04).
- `https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/NumberFormat` — `Intl.NumberFormat('tr-TR', ..., 'TRY')` (LOC-02).
- `https://developer.iyzico.com/` (Iyzico Checkout Form docs) — for the user-redirect callback URL contract semantics; cross-reference Phase 6 CONTEXT.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`.planning/intel/n11-recon.md`** — THE design contract for this phase. Color tokens (§4) paste into Tailwind 4 `@theme` block; Turkish copy phrases (§2) get used verbatim across all pages; layout patterns (§6) constrain grid + header + cart + PDP shapes; anti-pattern flags (§7) tell us what NOT to ship.
- **VITE_API_BASE_URL pattern** — locked by PROJECT.md Key Decision (Pitfall #23 prevention). All REST calls go through this base; `.env.example` will need a frontend section showing `VITE_API_BASE_URL=http://localhost:8080`.
- **Backend endpoints behind gateway prefix** — every call is `${VITE_API_BASE_URL}/api/v1/...`. Phases 3–8 already shipped these; frontend never hits service URLs directly.
- **Phase 6 `paymentPageUrl`** — Phase 10 just navigates `window.location.assign(paymentPageUrl)`. No SDK, no iframe, no checkout JS dependency.
- **Phase 5 cart auto-clears on order success** — cart-service's `cart.q.order-confirmed` consumer handles wipe. Frontend just needs to invalidate the cart query on landing on `/odeme/sonuc` with status=CONFIRMED.
- **Phase 3 address book endpoints** — `GET /api/v1/identity/addresses` returns the saved-address list for D-14's address picker; `POST` creates new.

### Established Patterns

- **Turkish UI copy + English code identifiers** (CLAUDE.md non-negotiable Rule #6). Component names `<ProductCard>` not `<UrunKarti>`; route constant keys `CART` not `SEPETIM`; literal strings rendered to the user are Turkish.
- **KDV-inclusive pricing** stored as `price_gross` server-side (LOC-01). Frontend never computes KDV; it just renders `formatTRY(price_gross)`.
- **Spring Data pagination is 0-based** (Phase 4 lesson — see Plan 04-01 native @Query notes). User-facing pages are 1-based. `URLAdapter` (D-10) handles the off-by-one.
- **Order status timeline** maps saga states to Turkish labels (ORD-04). Single rendering helper used in `/siparislerim/:orderId`.
- **8 top-level categories** (PROD-03 + recon §3) — slug list is hardcoded constant in `src/lib/categories.ts`. No category-tree fetch needed at render time (categories are stable seed data).
- **Iyzico Checkout Form is hosted** (Phase 6 D-07) — frontend redirects, no embed.
- **Idempotency-Key on POST /orders** (Phase 5) — frontend generates fresh UUID per checkout attempt; reuse same UUID on retry within the same checkout session to make replay safe.
- **RFC-7807 problem+json** (api-contracts §7) — frontend's error handler reads `detail` field for Turkish toast, falls back to `title`, with a generic "Bir hata oluştu" if both empty.
- **Phase 11 owns the chat bubble** — Phase 10 ships a clean storefront; no placeholder div, no reserved slot.

### Integration Points

- **`VITE_API_BASE_URL`** — single env var; default `http://localhost:8080` in `.env.example`; deployed value is the gateway origin behind Cloudflare Tunnel (Phase 11). All HTTP clients read from this constant.
- **Identity** — `POST /api/v1/identity/auth/login`, `POST /api/v1/identity/auth/register`, `GET /api/v1/identity/auth/me`, `GET /POST /api/v1/identity/addresses` (Phase 3).
- **Catalog** — `GET /api/v1/products?page=&size=&sort=&q=&categoryId=`, `GET /api/v1/products/{id}`, `GET /api/v1/inventory/{productId}` (Phase 4).
- **Cart** — `GET /api/v1/cart`, `POST /api/v1/cart/items`, `PUT /api/v1/cart/items/{productId}`, `DELETE /api/v1/cart/items/{productId}` (Phase 5).
- **Order** — `POST /api/v1/orders` (with Idempotency-Key + addressId + paymentMethod), `GET /api/v1/orders`, `GET /api/v1/orders/{id}`, `POST /api/v1/orders/{id}/cancel` (Phase 5).
- **Payment** — `GET /api/v1/payments/by-order/{orderId}` returns `paymentPageUrl` (Phase 6 — verify exact path with Phase 6 CONTEXT and planner).
- **Iyzico user-redirect target** — `${frontend_origin}/odeme/sonuc?orderId=<id>` is the URL frontend includes when initializing checkout. Phase 6's `/api/v1/payments/iyzico/callback` is the **webhook** — different concern. Verify the seam during planning.
- **No SSE in this phase** — chat streaming is Phase 11.
- **No AMQP in frontend ever** — all messaging is server-side; frontend only knows REST.

</code_context>

<specifics>
## Specific Ideas

- **Recon-faithful URLs** — the `/sepetim`, `/giris-yap`, `/uye-ol`, `/hesabim`, `/siparislerim` route names come straight from recon §2 and §8. Mirror n11 verbatim; do not invent variants.
- **Black CTA, not orange** — recon §6: PDP "Sepete Ekle" is `#1C1C1E` black with white text. n11's brand orange shows only in promo badges and discount ribbons, NEVER on the primary CTA. The Tailwind `@theme` token `--color-cta-primary-bg` is recon-locked.
- **Anonymous-cart-protect-checkout flow** — recon §8 carry-forward: do NOT model n11's quirky redirect-to-`/genel/odeme-secenekleri-393251` for empty-cart anonymous checkout. We gate `/odeme/*` on auth (D-08) and surface a "Hemen Giriş Yap" CTA in the cart sidebar when anonymous (recon §2 phrase #76).
- **Anti-patterns explicitly skipped** (recon §7): no countdown timers, no "10 günün en düşük fiyatı!" sticky-pricing dark pattern, no 5-card stacked coupon strip, no 8+ stacked merchant-banner blocks above the grid.
- **Breadcrumbs on PDP** — recon §8 confirms (phrases 477-478). PDP shows category trail above the title.
- **Sub-categories from recon §3** — the recon table notes sub-categories need confirmation; for v1 we ship only the 8 top-level slugs (PROD-03 lock), no sub-category nav. v2 can add sub-cat drill-down.
- **Demo moment for graders** — full demo flow: land on `/`, search "macbook" → `/arama?q=macbook` → click product → `/urun/...` → add to cart → click cart → `/sepetim` → click "Siparişi Tamamla" → redirect to `/giris-yap?redirectUrl=/odeme/adres` (anonymous) → log in → land on `/odeme/adres` → pick address → `/odeme/odeme` → submit → Iyzico hosted page → 3DS test card `5528 7900 0000 0008` → callback → `/odeme/sonuc?orderId=...` → polls → `CONFIRMED` → "Siparişimi Gör" → `/siparislerim/:orderId`. End-to-end Turkish, end-to-end gateway-only. This is the README's demo-script anchor.

</specifics>

<deferred>
## Deferred Ideas

These came up during discussion but belong in other phases or v2:

- **Refresh-token endpoint** — Phase 3 didn't ship one; v2 work. v1 forces re-login on 24h expiry per D-02.
- **Items-per-page selector** (G̈österim: 20 / 40 / 60) — fixed 20 in v1. Add in v2 if grading feedback asks.
- **Inline modal re-auth on 401** — nicer UX but more code; deferred. v1 does redirect-to-login.
- **Backend `/logout` endpoint with token revocation** — out of v1 scope; logout is frontend-only (D-03).
- **Hero carousel auto-advance + manual dots polish** — planner discretion in v1; v2 can add transitions, focus management, ARIA improvements.
- **Sub-category drill-down navigation** — v2; v1 ships only the 8 PROD-03 top-level slugs.
- **"Çok Satanlar" rail backed by real "ordered most" query** — FE-V2-04 explicit v2; v1 reuses `/products?sort=created_at,desc&size=10`.
- **"Kargo Bedava" badge on every listing card** — FE-V2-02; v1 ships PDP-only (FE-04).
- **Kapıda Ödeme as a working payment method** — FE-V2-03 explicit v2; v1 ships disabled radio (Pitfall #11 mitigation in `/odeme/odeme`).
- **Mid-stream chat cancellation, conversational PDP summaries, compare-products, cart-aware suggestions** — Phase 11 + AI-V2 territory.
- **Empty-state illustrations** — Out of scope; v1 ships text + CTA per recon voice.
- **404 page custom illustration** — planner discretion; simple text fine for v1.
- **Reviews / ratings / star placeholders** — Out of Scope per REQUIREMENTS (mitigation: optional static "yıldız" placeholder noted, planner skips entirely if it adds work).
- **Recommendations panel** — Out of Scope; emerges via chat assistant.
- **Newsletter signup, blog, store-rating** — Out of Scope.
- **Address book CRUD polish** (set-default, delete, edit) on `/adreslerim` — v1 ships CRUD floor; UX polish is v2.
- **Order tracking beyond "Sipariş Alındı → CONFIRMED"** — no real shipping carrier integration (Out of Scope); status timeline stops at CONFIRMED in v1.
- **Hot-reload dev experience polish** — Vite default is fine; no extra HMR plumbing.
- **A11y audit pass** (axe-core, screen-reader testing) — v2 polish; v1 ships Tailwind defaults + semantic HTML.

</deferred>

---

*Phase: 10-frontend-storefront*
*Context gathered: 2026-05-01*
