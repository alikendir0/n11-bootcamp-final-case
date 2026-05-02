<![CDATA[# frontend — Turkish React Storefront

> **Phase 2 (toolchain) · Phase 10 (storefront) · Phase 11 (chat assistant)**

Full Turkish-language e-commerce storefront modeled after [n11.com](https://www.n11.com), built from Playwright reconnaissance of the real site. Includes a floating AI shopping assistant powered by Gemini with SSE streaming.

## Tech Stack

| Technology | Version | Purpose |
|-----------|---------|---------|
| React | 19 | UI framework |
| Vite | 8 | Build tool + dev server |
| TypeScript | strict (`noUncheckedIndexedAccess` + `exactOptionalPropertyTypes`) | Type safety |
| Tailwind CSS | 4 (`@theme` directive) | Styling |
| Zustand | 5 | Auth + checkout state management |
| TanStack Query | 5 | Server state + caching |
| React Router | 7 | Client-side routing (14 routes) |
| react-hook-form + zod | 7 + 4 | Form handling + Turkish validation |

## Pages & Routes

| Route | Page | Purpose |
|-------|------|---------|
| `/` | Homepage | Hero carousel, "Yeni Gelenler" rail, category nav |
| `/kategori/:slug` | Category Listing | Paginated product grid with filters |
| `/arama` | Search | Free-text search results |
| `/urun/:slug-:id` | PDP | Image gallery, KDV-inclusive price, taksit table, "Sepete Ekle" |
| `/sepetim` | Cart | Line items, qty stepper, KDV breakdown, "Siparişi Tamamla" |
| `/odeme/adres` | Checkout — Address | Address picker + new address form |
| `/odeme/odeme` | Checkout — Payment | Iyzico Checkout Form redirect |
| `/odeme/sonuc` | Checkout — Result | Order confirmation |
| `/giris-yap` | Login | Email + password |
| `/uye-ol` | Register | Registration with Turkish validation messages |
| `/hesabim` | Account Hub | User dashboard |
| `/siparislerim` | Orders | Order list with status |
| `/siparislerim/:orderId` | Order Detail | Status timeline + cancel |
| `/adreslerim` | Address Book | CRUD for Türkiye addresses |

## Turkish Localization

- All visible strings in Turkish — "Sepete Ekle", "Stokta", "Tükendi", "Kargo Bedava", etc.
- Prices: `Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' })` → `1.299,90 ₺`
- Dates: `28 Nisan 2026` format
- Taksit (installment) preview: 1/2/3/6/9/12 months
- Form validation messages in Turkish

## AI Shopping Assistant (Phase 11)

Floating chat bubble (bottom-right, every page):
- **SSE token streaming** — responses appear character-by-character
- **Tool-use chips** — visual indicators during function calls ("araç çalıştırılıyor...")
- **Compact product cards** — inline results with "Sepete Ekle" CTA
- **Cart bridge** — adding via chat updates header badge within 1 second
- **Conversation persistence** — survives page navigation and browser refresh

## Environment

| Variable | Default | Purpose |
|----------|---------|---------|
| `VITE_API_BASE_URL` | `http://localhost:9090` | Gateway base URL (no trailing slash) |

## Development

```bash
# Install dependencies
npm install

# Start dev server (HMR)
npm run dev

# Run unit tests
npm test

# Run Playwright E2E
npx playwright test

# Production build
npm run build
```

## Testing

| Tool | Command | Coverage |
|------|---------|----------|
| Vitest | `npm test` | 32 unit tests (format, URL adapter, API normalization) |
| Playwright | `npx playwright test` | E2E smoke (login → browse → cart → checkout) |

## Design Decisions

- **SPA over SSR** — n11 PDP is fully client-rendered; JWT-at-gateway makes SSR-side auth dead weight
- **No direct service URLs** — all API calls go through `VITE_API_BASE_URL` (gateway)
- **Skeleton loaders** — shown on PDP + listing fetches
- **Turkish-first** — copy table from Phase 2 recon prevents drift
- **React Query** — cache + auth header injection survives 401-retry via `AuthEventBridge`
]]>
