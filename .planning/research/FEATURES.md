# Feature Research

**Domain:** Turkish online marketplace (n11-style) with an agentic-commerce differentiator layer
**Researched:** 2026-04-28
**Confidence:** HIGH (Turkish marketplace patterns confirmed via Trendyol live recon + market commentary; MCP/Gemini agentic flow confirmed via official docs)

---

## Reading Notes — How To Use This File

This document maps the **feature landscape** for a Turkish e-commerce storefront and translates it into a graded, picker-friendly scope for the 6-day bootcamp window. Four bands:

- **Table Stakes** — non-negotiable; missing them makes the demo feel broken. Most are already in the bootcamp brief and `PROJECT.md` Active list.
- **Expected (Turkish)** — locale conventions Turkish users assume; cheap to add and earn high "looks legit" points.
- **Differentiating** — the agentic-commerce wedge (chat assistant + MCP shared toolset). Go deep here; this is where the candidate beats the cohort.
- **Anti-features** — explicit exclusions, mostly inherited from `PROJECT.md` Out of Scope.

Effort hints are calibrated for an **AI-assisted execution** workflow inside a 6-day window:

- **S** (Small) — ~hours of human review; AI can grind out the implementation in one or two passes.
- **M** (Medium) — half-day to a day of human-driven decisions; AI fills the body.
- **L** (Large) — multi-day; needs phase-level planning, integration glue, and verification.

---

## 1. Storefront Feature Landscape

### Table Stakes (Users Expect These)

Anyone visiting a Turkish e-commerce site assumes these exist. Penalty for missing is high; credit for having them is zero.

| # | Feature | User Expectation | Effort | Maps To | Notes |
|---|---------|------------------|--------|---------|-------|
| TS-1 | **Product listing with pagination** | Pagination is in the bootcamp brief verbatim. Users page through tens of thousands of SKUs on n11/Trendyol. | S | Brief Backend + Frontend | Spring Data `Page<T>`, query params `?page=&size=&sort=`. UI shows page numbers + "Önceki / Sonraki". |
| TS-2 | **Product detail page (PDP)** | Gallery, title, price, KDV-inclusive display, stock state, "Sepete Ekle" CTA, seller info. | M | Brief Frontend + product-service | Single PDP route `/urun/:slug-:id`. Image gallery + tabs (Açıklama, Özellikler, Kargo). |
| TS-3 | **Category browsing** | Top nav with main categories (Elektronik, Giyim, Ev & Yaşam, etc.); category landing pages with filters. | M | product-service category endpoint | Trendyol top-level: Giyim, Ayakkabı, Kozmetik, Elektronik, Ev Ürünleri, Süpermarket, Anne & Çocuk, Spor & Outdoor. n11 uses similar taxonomy (Elektronik, Moda, Ev & Yaşam, Anne & Bebek, Kozmetik, Spor & Outdoor, Otomotiv, Süpermarket, Kitap-Müzik-Film-Oyun). |
| TS-4 | **Text search with filters** | Search bar in header is the most-clicked element on Turkish marketplaces. Filters by category, price range, brand. | M | search-service | Brief doesn't mandate but missing it kills demo credibility. Plain Postgres `ILIKE` + GIN index is enough; semantic upgrade comes from chat assistant. |
| TS-5 | **Cart: add / remove / update qty** | Bootcamp brief explicit. | S | Brief Backend + Frontend / cart-service | "Sepete Ekle", "Adet", "Sil". Persist for logged-in users; localStorage fallback for guests is optional. |
| TS-6 | **Checkout: order creation** | Bootcamp brief explicit. Takes cart + address + payment selection → places order. | M | Brief Backend / order-service | SAGA-driven: `OrderCreated` → reserve stock → `StockReserved` → take payment → `PaymentCompleted`. Compensating events on each failure. |
| TS-7 | **Iyzico payment integration (sandbox)** | Bootcamp brief explicit. Iyzico is the de-facto Turkish payment gateway. | L | Brief Backend / payment-service | Iyzico Java SDK; sandbox keys; success/fail/3DS callback URL. Demo card "5528 7900 0000 0008" (sandbox Halkbank Bonus). |
| TS-8 | **JWT authentication** | Bootcamp brief explicit. Login/register, token in `Authorization: Bearer ...`, gateway validates. | M | Brief Backend / identity-service | Issued by identity-service, validated only at api-gateway, downstream services trust `X-User-Id` header. |
| TS-9 | **Order list & order detail** | Users expect to see "Siparişlerim" after they buy. | S | order-service + frontend | `/hesabim/siparislerim` route. Shows status (Onaylandı, Hazırlanıyor, Kargoda, Teslim Edildi, İptal Edildi). |
| TS-10 | **Address management** | Required for checkout; users expect a saved address book. | S | identity-service or address sub-entity | Mahalle/İlçe/İl + Türkiye-only; TC kimlik field optional (skip for v1). |
| TS-11 | **Error handling + loading states** | Bootcamp brief explicit. | S | Frontend cross-cutting | Skeleton loaders on PDP/list; toast or inline error on action failures. React Query handles most. |
| TS-12 | **Swagger / OpenAPI** | Bootcamp brief explicit. | S | Each service via Springdoc | `/swagger-ui.html` per service; gateway aggregates. |
| TS-13 | **Pagination UI component** | Bootcamp brief explicit. | S | Frontend | Numbered pages + prev/next; Turkish copy ("Önceki", "Sonraki", "Sayfa 1 / 24"). |
| TS-14 | **Tests (smoke unit + 1-2 integration per service)** | Bootcamp brief explicit; coordinator weighs SOLID + test discipline. | M | Each service | Per `PROJECT.md` Out-of-Scope: not chasing 70% coverage. Critical-path only. |
| TS-15 | **Logging mechanism** | Bootcamp brief explicit. | S | All services | SLF4J + Logback; structured JSON logs with correlation IDs (helps trace SAGA across services — listed as a `PROJECT.md` mitigation). |

### Expected (Turkish-Locale Conventions)

These aren't in the brief, but every Turkish marketplace has them. Each is small effort and earns "yes, this person knows the locale" credit.

| # | Feature | User Expectation | Effort | Notes |
|---|---------|------------------|--------|-------|
| EX-1 | **KDV-inclusive price display** | Turkish e-commerce *always* shows KDV-included price. No "+VAT" surprises. | S | Backend stores `price_gross` (KDV included) + `kdv_rate` (default 20% for most goods, 10% for some food/health, 1% for select). Frontend just renders `price_gross`. |
| EX-2 | **TRY currency, tr-TR locale formatting** | "1.299,90 ₺" — thousands `.`, decimals `,`, `₺` symbol after with NBSP. | S | `Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' })` does it for free in the browser. Backend returns raw `BigDecimal`. |
| EX-3 | **Taksit (installment) preview on PDP** | Trendyol/n11/Hepsiburada show "12 taksit ile 108,33 ₺" beside the main price. Iyzico offers 2/3/6/9/12 installment tiers across Turkish credit cards (Bonus, World, Maximum, CardFinans, Paraf, Axess, Advantage). | S | Compute client-side: `Math.ceil(price / installments)`. Real Iyzico installment quotes happen at checkout. PDP just shows preview text "12 taksit ile X ₺". |
| EX-4 | **Turkish UI copy throughout** | "Sepete Ekle", "Hemen Al", "Favorilere Ekle" (skip if favorites OOS), "Kargo Bedava", "Stokta", "Tükendi", "Siparişi Tamamla". | S | i18n not needed for v1 (Turkish-only); hard-code strings in components. Code identifiers stay English per `PROJECT.md`. |
| EX-5 | **"Kargo Bedava" badge over a price threshold** | Trendyol shows free-shipping badges on cards. Threshold typically 150-300 ₺. | S | Static badge on PDP/listing if `price >= FREE_SHIPPING_THRESHOLD` (config value). No real shipping calc. |
| EX-6 | **Stock indicator** | "Son 3 ürün!" or "Stokta" / "Tükendi". Builds urgency. | S | inventory-service exposes `stockQty` per SKU. Frontend renders red banner when `stockQty <= 5`. |
| EX-7 | **Kapıda Ödeme option (display only)** | Common on n11 and Hepsiburada. Trendyol does *not* offer it. Showing it as a payment option with "yakında" or grayed-out is fine for demo. | S | Render in checkout as disabled radio with tooltip "Demo aşamasında yalnızca kart ödemesi açıktır." OR fully implement as a no-op order status (`payment_method=COD`, order goes straight to `Hazırlanıyor`). The latter is a 30-min add and a free differentiator. |
| EX-8 | **Order status timeline** | "Sipariş Alındı → Hazırlanıyor → Kargoya Verildi → Teslim Edildi". Visual stepper on order detail. | S | Order entity has `status` enum + `statusHistory[]`. UI is a static stepper. SAGA already updates the status; this just visualizes it. |
| EX-9 | **Header layout match (logo + search + account + cart)** | n11/Trendyol/Hepsiburada all share: left-logo, center-search-bar (huge), right cluster (account, favorites, cart with item count badge). | S | Direct lift from Playwright recon. Sticky header on scroll. |
| EX-10 | **Footer with help links** | Standard "Hakkımızda", "İletişim", "Yardım", "Sözleşmeler", payment-method icon strip. | S | Static content; payment icons (Visa, Mastercard, Iyzico) as SVGs. |
| EX-11 | **Hero carousel on homepage** | All three marketplaces lead with a rotating banner. | S | 3-4 static slides; no real CMS. swiper.js or pure CSS. |
| EX-12 | **Bestseller / "Çok Satanlar" rail on homepage** | Standard pattern: horizontal-scroll product rail under hero. | S | Single endpoint `GET /products/bestsellers?limit=12`; backend can hard-code the seed for v1. |

### Differentiating (Agentic Commerce — The Wedge)

This is where the candidate stands out. Two surfaces, one toolset.

| # | Feature | Value Proposition | Effort | Notes |
|---|---------|-------------------|--------|-------|
| **DIFF-1** | **In-storefront AI Shopping Assistant ("Yapay Zeka Alışveriş Asistanı")** | Floating chat bubble, persistent across pages, Turkish-fluent. User can ask "kırmızı kazak göster bana", "bunu sepete ekle", "siparişimi tamamla" and the agent does it. | L | Powered by Gemini 3.0 Flash (verify model name at impl time per `PROJECT.md` decision row). Uses tool-use (function calling) to drive the same toolset as MCP. Streaming token output. SSE channel from `ai-service` to browser. |
| **DIFF-2** | **MCP server exposing the storefront to external agents** | Claude Desktop / any MCP client can plug in and shop the catalog. Read + cart + place order + payment link. Same tool definitions as DIFF-1. | L | `mcp-server` service; **stdio transport for local Claude Desktop demo** + **HTTP+SSE / Streamable HTTP for network demo**. Auth via API key in env. |
| **DIFF-3** | **Shared agent toolset (one set, two consumers)** | Single Java module defines tools; the chat assistant invokes them via Gemini function-call dispatch, the MCP server exposes them as `tools/list` + `tools/call`. Pure DRY on the most differentiating feature. | M | `ai-service` owns `AgentToolRegistry`; `mcp-server` imports it. Tool contract is a single Java interface with JSON-Schema-annotated DTOs. |
| **DIFF-4** | **Semantic search via Gemini Embeddings + pgvector** | "Bütçe dostu spor ayakkabı" beats keyword match. Embeddings stored on product upsert; cosine-similarity query in search-service. | M | Gemini embedding model + pgvector extension on Postgres. Query: embed user's text → `ORDER BY embedding <=> :queryEmbedding LIMIT N`. Falls back to text search if embedding service is down. |
| **DIFF-5** | **Conversation continuity across chat & web UI** | Cart added by chat is visible in the cart page; cart added by web is visible in chat context. One source of truth: cart-service. | S | Chat tools call cart-service via the same gateway as the React UI does. No special state — both surfaces are just clients of cart-service. The "magic" is architectural, not custom code. |
| **DIFF-6** | **Streaming chat UX (token-by-token)** | Modern chat UX expectation; static "wait for full response" feels stale. | M | SSE from `ai-service` to browser. Mid-stream tool-call interruption: assistant emits a "araç çalıştırılıyor..." indicator, tool runs, stream resumes. |
| **DIFF-7** | **Auth bridge for MCP (API key for external agents)** | External Claude Desktop user authenticates as a "demo merchant agent" via API key in env. Claims map to a synthetic user ID. | S | `mcp-server` reads `MCP_API_KEY`, exchanges for an internal JWT against identity-service `/agents/exchange` endpoint. Same gateway accepts the JWT downstream. Documented tradeoff: API key is a long-lived bearer; for production you'd want OAuth2 device-code flow per the MCP spec. |
| **DIFF-8** | **Provider-agnostic LLM abstraction** | `ChatProvider` / `EmbeddingProvider` ports + Gemini adapter. Open-Closed Principle demo. | S | Already in `PROJECT.md` Decisions. One Java interface per port; one adapter (`GeminiChatAdapter`, `GeminiEmbeddingAdapter`). v1 ships with Gemini only — abstraction is the SOLID artifact, not a feature. |

#### The Shared Tool Set (one definition, two consumers)

The same Java tool registry feeds both the chat assistant (via Gemini function calling) and the MCP server (via `tools/list` + `tools/call`). This list is the **canonical agent contract**:

| Tool | Purpose | Auth scope | Read/Write | Backing service |
|------|---------|------------|------------|-----------------|
| `search_products` | Text or semantic search; returns top-N products with id, title, price, image | Public | R | search-service |
| `get_product` | Full product detail by id (gallery, specs, price, stock, KDV) | Public | R | product-service |
| `list_categories` | Category tree (top-level + immediate children) | Public | R | product-service |
| `add_to_cart` | Add `(productId, quantity)` to the caller's cart | Auth required | W | cart-service |
| `view_cart` | Returns current cart with totals, KDV breakdown, taksit options | Auth required | R | cart-service |
| `update_cart_item` | Change quantity of a cart line | Auth required | W | cart-service |
| `remove_from_cart` | Remove a line from the cart | Auth required | W | cart-service |
| `create_order` | Convert cart → pending order; returns `orderId` and triggers SAGA | Auth required | W | order-service |
| `get_payment_link` | Returns Iyzico checkout URL for a pending order | Auth required | W (initiates payment) | payment-service |
| `get_order_status` | Returns order status + status history by `orderId` | Auth required | R | order-service |

**Auth bridge:** Tools tagged `Auth required` need a JWT; the gateway injects `X-User-Id`. For chat-assistant invocations the user is the logged-in storefront session. For MCP invocations the user is a synthetic "agent user" mapped from the API key.

**Effort budget for the toolset:** ~M total. Each tool is a thin wrapper over an existing REST endpoint; the work is contract design (JSON Schema), centralized error mapping, and the registry.

#### Out-of-the-Box Differentiator Wins (Not in Brief, Cheap)

These are agentic-flavored features that fall out *for free* once the chat assistant exists. List them in the README as "no-cost wins" so the grader notices.

- **Natural-language navigation** — "ana sayfaya dön", "sepete bak" → assistant returns deep links.
- **Conversational PDP summaries** — assistant can summarize a product's reviews-free description in 2-3 bullets when asked.
- **Compare-products Q&A** — "X mi daha iyi yoksa Y mi?" — assistant uses `get_product` twice and reasons.
- **Cart-aware suggestions** — "bunu da almak ister misin?" — read cart + call `search_products` for complements.

### Anti-Features (Explicit Exclusions)

These mirror `PROJECT.md` Out of Scope. Naming them defends scope and shows the grader that exclusions are *deliberate*, not omissions.

| Feature | Why Tempting | Why Excluded | Alternative |
|---------|--------------|--------------|-------------|
| **Reviews and ratings** | Trendyol/n11 show stars on every product card — feels missing without it. | Not in bootcamp brief. No AI summarization angle requested. Time-bound to 6 days. | Render seed data with a static "yıldız sayısı" number on product cards; no review system underneath. (Even cheaper: omit entirely.) |
| **Real email/SMS sending** | "Notification" implies it. | No SMTP/Twilio account; not in brief. | `notification-service` exists, consumes RabbitMQ events, **logs** the email/SMS body. Full SAGA participant, no real send. |
| **Multi-vendor seller dashboards** | n11 is an open marketplace — sellers feel core. | Not in brief; 5x scope explosion. | All seed products belong to a single fictional seller "n11 Pazaryeri". |
| **Real-time WebSocket cart sync** | Modern feel; chat assistant might benefit. | Not in brief. SSE for chat streaming is the only real-time we ship. Polling is fine for cart updates between web tab + chat. | After a chat-driven cart action, frontend invalidates React Query cache; cart UI re-fetches. |
| **Multiple LLM providers (OpenAI, Claude adapters)** | The abstraction begs for it. | Per `PROJECT.md`: the abstraction itself is the SOLID demo, not the second adapter. | Ship `GeminiChatAdapter` only; document `OpenAIChatAdapter` as a 30-LOC future extension in README. |
| **Separate NL-search bar** | Easy "AI feature" to add. | Per `PROJECT.md`: don't dilute AI investment across shallow features. | Semantic search is *available through the chat assistant*; the visible search bar is plain text. |
| **Recommendations panel** | Trendyol/n11 have "Senin için" rails everywhere. | Per `PROJECT.md`: emerges from chat tool-use, not a separate panel. | Chat assistant can answer "öner bana"; homepage carries one static "Çok Satanlar" rail. |
| **Admin dashboard / merchant tools** | Traditional e-com expectation. | Not in brief. | Postman collection + Swagger + DB seed scripts demonstrate everything an admin would touch. |
| **Mobile app** | "Real" Turkish marketplaces have one. | Brief is React-only. | Responsive web; mobile breakpoint tested but no PWA install. |
| **Production observability stack (ELK / Loki / tracing)** | Looks impressive. | 6 days. | Structured JSON logs with correlation IDs. Grader can grep. |
| **Strict DB-per-service on separate Postgres instances** | Microservices purist signal. | AWS cost. | One Postgres host, one schema per service. The boundary is at the schema, not the instance. |
| **Real-time stock updates (live "Son 1 kaldı!")** | Adds urgency. | Not in brief, distracts. | Stock checked at page load + at order time (SAGA reservation step). |
| **Test coverage > 70%** | Quality signal. | 6-day window. | Smoke unit + 1-2 integration per service on critical path. Visible in every service. |

---

## 2. Feature Dependencies

```
identity-service (JWT)
    └──required-by──> ALL Auth-required tools (add_to_cart, view_cart, create_order, ...)

product-service
    ├──required-by──> search-service (indexes products)
    ├──required-by──> cart-service (validates SKU + price snapshot)
    └──required-by──> get_product / search_products / list_categories tools

cart-service
    ├──required-by──> order-service (cart → order conversion)
    └──required-by──> add_to_cart / view_cart / update_cart_item / remove_from_cart tools

inventory-service
    └──required-by──> SAGA step 1 (reserve stock on OrderCreated)

order-service
    ├──required-by──> payment-service (initiates payment for pending order)
    └──required-by──> create_order / get_order_status tools

payment-service
    ├──depends-on──> Iyzico SDK (sandbox)
    └──required-by──> get_payment_link tool, SAGA step 2 (charge)

notification-service
    └──consumes──> OrderCreated / OrderConfirmed / OrderFailed events (log-only)

ai-service
    ├──depends-on──> ALL agent tools (calls them via gateway)
    ├──depends-on──> ChatProvider (Gemini adapter)
    └──depends-on──> EmbeddingProvider (Gemini adapter, for semantic search)

mcp-server
    └──depends-on──> ai-service.AgentToolRegistry (shared definitions)

search-service
    ├──depends-on──> product-service (catalog source)
    └──depends-on──> EmbeddingProvider (for semantic mode)

api-gateway
    └──fronts──> ALL services; validates JWT once; injects X-User-Id

eureka-server + config-server
    └──required-by──> ALL services at boot
```

### Dependency Notes

- **Every agent tool ultimately depends on api-gateway + identity-service.** Agent surfaces (chat, MCP) re-use the storefront's auth path; no parallel auth tree. This is the simplification that makes "shared toolset" cheap.
- **Semantic search depends on embeddings being populated.** Phase ordering: bring up product-service + seed data → run embedding backfill → wire search-service. If embeddings lag, search falls back to ILIKE — graceful degradation.
- **MCP server is downstream of `ai-service`.** Build `ai-service.AgentToolRegistry` first, then expose it via the MCP wire format. Don't build the MCP server first and re-implement tools.
- **`notification-service` is an event consumer only.** No synchronous deps. Build last; it's pure SAGA observation.
- **Conflict to avoid: do not let MCP and chat assistant build *separate* tool definitions.** That would double maintenance and silently drift.

---

## 3. MVP Definition (Graded by Risk Tier)

The bootcamp's grading rubric weighs **brief compliance** above all. Scoping is therefore three-layered:

### Tier 1 — Must Land (Demo Day Floor)

If absolutely nothing else works, this list passes the rubric.

- [x] All TS-1 through TS-15 (Table Stakes) — entire bootcamp brief
- [x] EX-1 KDV-inclusive pricing (free; just labeling)
- [x] EX-2 TRY currency formatting (free; `Intl.NumberFormat`)
- [x] EX-4 Turkish UI copy (free; the whole UI is Turkish anyway per brief)
- [x] EX-9 / EX-10 / EX-11 Header / footer / hero (free; standard layout)
- [x] DIFF-2 MCP server with read-only tools (`search_products`, `get_product`, `list_categories`) — required by `PROJECT.md` "Self-Imposed Differentiators"; minimum viable agentic story

### Tier 2 — Strong Submission (Realistic Target)

Adds the differentiating wedge in working form.

- [x] Tier 1 +
- [x] DIFF-1 Chat assistant with the full toolset (read + cart + checkout)
- [x] DIFF-2 MCP server with **full** toolset (matches chat)
- [x] DIFF-3 Shared toolset module (the SOLID artifact)
- [x] DIFF-7 Auth bridge for MCP via API key
- [x] DIFF-8 ChatProvider port + Gemini adapter
- [x] EX-3 Taksit preview on PDP
- [x] EX-6 Stock indicator
- [x] EX-8 Order status timeline

### Tier 3 — Stretch (If There's Air)

Pure polish; only attempt if Tier 2 is solid.

- [ ] DIFF-4 Semantic search with pgvector (requires embeddings backfill; medium risk)
- [ ] DIFF-6 Streaming chat UX (SSE; needs careful cancellation handling)
- [ ] EX-5 Free-shipping badge logic
- [ ] EX-7 Kapıda Ödeme as a working order path
- [ ] EX-12 Bestseller rail on homepage

### Always Out (v1)

- All anti-features in section 1.
- Reviews, real email, multi-vendor, WebSockets, multi-LLM, recommendations panel, admin UI, mobile app, observability stack, production DB-per-instance.

---

## 4. Feature Prioritization Matrix

Top items only — fuller list above.

| Feature | User Value | Impl Cost | Tier | Priority |
|---------|------------|-----------|------|----------|
| TS-1..15 (full brief) | HIGH | M-L overall | 1 | P1 |
| DIFF-1 Chat assistant | HIGH | L | 2 | P1 |
| DIFF-2 MCP server | HIGH (graders' wow) | M | 1+2 | P1 |
| DIFF-3 Shared toolset | MEDIUM (SOLID artifact) | M | 2 | P1 |
| DIFF-8 LLM provider port | MEDIUM (SOLID artifact) | S | 2 | P1 |
| EX-3 Taksit preview | MEDIUM | S | 2 | P2 |
| EX-2 / EX-4 TR locale | HIGH (looks legit) | S | 1 | P1 |
| DIFF-4 Semantic search | MEDIUM | M | 3 | P2 |
| DIFF-6 Streaming UX | MEDIUM | M | 3 | P2 |
| EX-7 Kapıda Ödeme path | LOW | S | 3 | P3 |
| Reviews / SMS / multi-vendor | LOW (anti) | — | — | OUT |

---

## 5. Competitor Feature Cross-Reference

How n11 / Trendyol / Hepsiburada handle the same surfaces, and what we ship.

| Surface | n11 | Trendyol | Hepsiburada | Our Approach |
|---------|-----|----------|-------------|--------------|
| Top categories | Elektronik, Moda, Ev & Yaşam, Anne & Bebek, Kozmetik, Spor, Otomotiv, Süpermarket, Kitap-Müzik | Giyim, Ayakkabı, Kozmetik, Elektronik, Ev Ürünleri, Süpermarket, Anne & Çocuk, Spor & Outdoor | Elektronik, Moda, Ev & Yaşam, Süpermarket, Kozmetik, Anne & Bebek, Hobi, Otomotiv | Seed 6-8 top categories matching n11; ~30 SKUs/category. |
| Search bar | Center-header, large, autocomplete | Center-header, autocomplete + popular-search dropdown | Center-header, autocomplete | Plain text search v1; chat assistant is the "smart" surface. |
| Add-to-cart copy | "Sepete Ekle" | "Sepete Ekle" | "Sepete Ekle" | "Sepete Ekle" (universal). |
| Buy now copy | "Hemen Al" | "Hemen Al" | "Şimdi Al" | "Hemen Al" (more common). |
| Installments | Visible on PDP under price | "12 taksit ile X ₺/ay" prominent on PDP | Same as Trendyol | EX-3: PDP + cart show "12 taksit ile ... ₺" |
| Cash on delivery | Yes (Kapıda Ödeme) | **No** | Yes | EX-7: render as option; either disabled or as a no-op order path. |
| Reviews | Stars + count | Stars + count + photo reviews | Stars + count | **Excluded** per PROJECT.md. |
| Free-shipping badge | Sometimes | "Hızlı Teslimat" + "Kargo Bedava" | "Hızlı Kargo" | EX-5: static threshold-based "Kargo Bedava" badge. |
| Trust signals | Stock count, seller rating | "En Çok Satan", "925k kişi favoriledi" | "1.5M+ değerlendirme" | Stock count only (EX-6). |
| Cart icon w/ badge | Yes | Yes | Yes | Yes — TS-5 + header. |
| Account / orders menu | "Hesabım" → "Siparişlerim" | "Hesabım" → "Siparişlerim" | "Hesabım" → "Siparişlerim" | Same; matches recon. |
| AI assistant | None (as of recon) | None (as of recon) | None (as of recon) | **DIFF-1**: this is the wedge — no incumbent has it on the storefront. |
| External agent / MCP | None | None | None | **DIFF-2**: also unique; lifts the agentic story above the cohort. |

---

## 6. Sources

### Authoritative (HIGH confidence)
- [Model Context Protocol — Server Concepts](https://modelcontextprotocol.io/docs/learn/server-concepts) — official tool/resource/prompt primitives, `tools/list` + `tools/call`, JSON Schema validation, transports.
- [Model Context Protocol — Introduction](https://modelcontextprotocol.io/introduction) — clients (Claude, ChatGPT, VS Code, Cursor), open protocol scope.
- [Gemini API — Function Calling](https://ai.google.dev/gemini-api/docs/function-calling) — declaration shape, multi-turn loop, tool dispatch.
- [Gemini Models — ai.google.dev](https://ai.google.dev/gemini-api/docs/models) — current model lineup; verify exact `gemini-3-flash` ID at impl time per `PROJECT.md` policy.
- [Iyzico — Personal Installments](https://www.iyzico.com/en/personal/maximum-arti-taksit-campaign) — taksit value support (1, 2, 3, 6, 9, 12).
- [Iyzico Docs — Payment integration](https://docs.iyzico.com/en/payment-methods/tokenization/tokenization-integration/initialize-payment-with-session/balance-payment) — payment session API.

### Marketplace Recon (MEDIUM confidence — live-fetched 2026-04-28)
- [Trendyol homepage](https://www.trendyol.com) — categories: Giyim, Ayakkabı, Kozmetik, Elektronik, Ev Ürünleri, Süpermarket, Anne & Çocuk, Spor & Outdoor; CTAs: "Sepete Ekle", "Favorilere Ekle", "Hemen Al", "Tümünü Gör"; trust strings: "En Çok Satan", "Hızlı Teslimat yapılıyor!", "Trendyol Plus'a Özel".
- n11.com — fetch returned 403 (anti-bot); category list cross-referenced via [Crunchbase profile](https://www.crunchbase.com/organization/n11-com), [Turkpidya guide](https://turkpidya.com/n11-english/), [SlideShare overview](https://www.slideshare.net/slideshow/presentation-of-n11-turkeys-ecommerce-giant/232505811). Recommend a Playwright pass during the Frontend phase to capture exact layout (already a `PROJECT.md` decision row).
- hepsiburada.com — fetch returned 403; defer to Playwright recon.

### Industry Context (MEDIUM confidence — single-source web search)
- [Deloitte — Agentic Commerce Guide](https://www.deloitte.com/us/en/Industries/consumer/articles/agentic-commerce-ai-shopping-agents-guide.html) — market sizing ($3-5T by 2030 per McKinsey).
- [JP Morgan — Agentic Commerce future](https://www.jpmorgan.com/payments/newsroom/agentic-commerce-ai-future-shopping) — protocol landscape (ACP, A2A, MCP).
- [commercetools — 7 AI Trends Shaping Agentic Commerce in 2026](https://commercetools.com/blog/ai-trends-shaping-agentic-commerce) — implementation patterns.
- [Shopify Storefront MCP server docs](https://shopify.dev/docs/apps/build/storefront-mcp/servers/storefront) — reference implementation: `search_catalog`, `lookup_catalog`, `get_product`, cart operations. Validates our toolset shape against an industry baseline.
- [Shopify Engineering — MCP UI](https://shopify.engineering/mcp-ui-breaking-the-text-wall) — pattern of returning rich UI in chat (out of scope for v1; documented as a v2 idea).
- [AWS — Agentic Commerce blog](https://aws.amazon.com/blogs/industries/decoding-the-future-of-retail-embracing-ai-shopping-agents/) — backend-as-storefront framing.

### Locale Confirmation (LOW-MEDIUM confidence)
- [Kamil Keleş — "Sepete Ekle" button best practices](https://www.kamilkeles.com/sepete-ekle-butonu/) — confirms "Sepete Ekle" / "Hemen Al" as the universal Turkish e-com CTAs.
- [Haberler.com — Kapıda Ödeme on Turkish marketplaces](https://www.haberler.com/soguk-haber/kapida-odeme-olan-siteler-neresi-kapida-odeme-14102468-haberi/) — confirms n11 + Hepsiburada offer Kapıda Ödeme; Trendyol does not.
- [Ticimax — Trendyol payment options 2026](https://www.ticimax.com/blog/trendyol-odeme-yontemleri) — confirms 12-month max installments on Trendyol; bank-card vs credit-card distinction.

---
*Feature research for: Turkish e-commerce marketplace + agentic commerce layer*
*Researched: 2026-04-28*
