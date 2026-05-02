# Phase 11: Frontend Chat Assistant + DevOps Deploy - Pattern Map

**Mapped:** 2026-05-02  
**Files analyzed:** 15 new/modified file groups  
**Analogs found:** 14 / 15

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `frontend/src/components/layout/Layout.tsx` | component | request-response | `frontend/src/components/layout/Layout.tsx` | exact-modification |
| `frontend/src/components/chat/ChatAssistant.tsx` | component | streaming | `frontend/src/components/layout/Header.tsx` + `frontend/src/components/feedback/ToastBridge.tsx` | role-match |
| `frontend/src/components/chat/ChatDrawer.tsx` | component | streaming | `frontend/src/components/layout/Header.tsx` | role-match |
| `frontend/src/components/chat/ChatTranscript.tsx` | component | streaming | `frontend/src/pages/CartPage.tsx` | partial-flow |
| `frontend/src/components/chat/ToolStatusChip.tsx` | component | event-driven | `frontend/src/components/account/OrderStatusBadge.tsx` or `frontend/src/components/pdp/StockBadge.tsx` | role-match |
| `frontend/src/components/chat/ChatProductCard.tsx` | component | request-response | `frontend/src/components/listing/ProductCard.tsx` | exact-role |
| `frontend/src/components/chat/ChatHandoffCard.tsx` | component | request-response | `frontend/src/components/cart/CartSummary.tsx` | role-match |
| `frontend/src/api/chatApi.ts` | service | streaming | `frontend/src/lib/apiClient.ts` + `ai-service/.../ChatStreamController.java` | flow-match |
| `frontend/src/hooks/useChatAssistant.ts` | hook | streaming | `frontend/src/hooks/useCart.ts` + `frontend/src/pages/ProductDetailPage.tsx` | role-match |
| `frontend/src/lib/chatEvents.ts` | utility | transform | `frontend/src/lib/types.ts` + `ai-service/.../SseEvents.java` | flow-match |
| `frontend/src/lib/types.ts` | model | transform | `frontend/src/lib/types.ts` | exact-modification |
| `.github/workflows/ci.yml` | config | batch | `.github/workflows/ci.yml` | exact-modification |
| `.github/workflows/release.yml` | config | batch | `.github/workflows/ci.yml` | role-match |
| `docker-compose.yml` | config | request-response | `docker-compose.yml` | exact-modification |
| `README.md` | docs | batch | `payment-service/README.md` | role-match |
| `.env.example`, `frontend/.env.example` | config | request-response | existing env example files | exact-modification |

## Pattern Assignments

### `frontend/src/components/layout/Layout.tsx` (component, request-response)

**Analog:** `frontend/src/components/layout/Layout.tsx`

**Imports pattern** (lines 1-6):
```typescript
import { Outlet } from 'react-router-dom';
import { Header } from './Header';
import { CategoryNav } from './CategoryNav';
import { Footer } from './Footer';
import { ToastBridge } from '../feedback/ToastBridge';
import { AuthEventBridge } from '../feedback/AuthEventBridge';
```

**Cross-cutting bridge mount pattern** (lines 8-20):
```typescript
export function Layout() {
  return (
    <div className="min-h-screen flex flex-col">
      <Header />
      <CategoryNav />
      <main className="flex-1">
        <Outlet />
      </main>
      <Footer />
      <ToastBridge />
      <AuthEventBridge />
    </div>
  );
}
```

**Apply:** mount the chat assistant after `ToastBridge`/`AuthEventBridge` so the component survives route changes under the same layout.

---

### `frontend/src/components/chat/ChatAssistant.tsx` (component, streaming)

**Analog:** `frontend/src/components/layout/Header.tsx`

**Imports pattern** (lines 1-8):
```typescript
import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Search, ShoppingCart, User, LogOut, ChevronDown } from 'lucide-react';
import { ROUTES } from '../../lib/routes';
import { useAuthStore } from '../../store/authStore';
import { useCartItemCount } from '../../hooks/useCart';
```

**Stateful shell pattern** (lines 10-19, 61-82):
```typescript
export function Header() {
  return (
    <header className="sticky top-0 z-50 bg-[var(--color-surface-header)] border-b border-[var(--color-border)] h-16">
      <div className="mx-auto max-w-7xl h-full flex items-center gap-4 px-4">
        <Logo />
        <SearchBar />
        <AccountCluster />
      </div>
    </header>
  );
}

function AccountCluster() {
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);
  const cartCount = useCartItemCount();
```

**Toast bridge pattern** (`frontend/src/components/feedback/ToastBridge.tsx` lines 1-7):
```typescript
import { Toaster } from 'sonner';

export function ToastBridge() {
  return (
    <Toaster richColors position="top-right" duration={4000} visibleToasts={3} closeButton />
  );
}
```

**Apply:** use component-local `open` state for drawer visibility, lucide icons, Turkish labels, Tailwind token classes, and `toast.success/error` for confirmed tool results.

---

### `frontend/src/components/chat/ChatDrawer.tsx` (component, streaming)

**Analog:** `frontend/src/components/layout/Header.tsx`

**Interactive control pattern** (lines 116-127):
```typescript
<button
  type="button"
  onClick={() => setOpen(o => !o)}
  aria-haspopup="menu"
  aria-expanded={open}
  className="inline-flex items-center gap-1 px-3 py-2 min-h-[44px] hover:underline"
>
  <User size={18} />
  <span className="text-sm">Hesabım</span>
  <ChevronDown size={14} />
</button>
```

**Overlay/dropdown pattern** (lines 127-143):
```typescript
{open && (
  <div role="menu" className="absolute right-0 top-full mt-1 w-56 bg-white border border-[var(--color-border)] rounded shadow-lg py-1 z-50">
    <div className="px-3 py-2 text-xs text-gray-600 border-b border-[var(--color-border)] truncate">
      {user?.email}
    </div>
    <Link to={ROUTES.ACCOUNT} role="menuitem" onClick={() => setOpen(false)}
          className="block px-3 py-2 hover:bg-gray-50 text-sm">Hesabım</Link>
  </div>
)}
```

**Apply:** keep accessibility attributes (`aria-expanded`, `aria-label`), fixed z-index overlay, tokenized border/background, and separate trigger/shell subcomponents. Adapt from dropdown to fixed right drawer: `fixed right-0 top-0 h-[100dvh] w-[420px]` desktop and full-screen mobile.

---

### `frontend/src/components/chat/ChatTranscript.tsx` (component, streaming)

**Analog:** `frontend/src/pages/CartPage.tsx`

**Loading/error/retry state pattern** (lines 97-113):
```typescript
if (isLoading) return <CartSkeleton />;
if (isError) {
  return (
    <div className="mx-auto max-w-7xl my-12 px-4">
      <div className="bg-white border border-[#DC2626] rounded p-6 text-center">
        <p className="mb-4">Sepet yüklenirken bir hata oluştu.</p>
        <button
          onClick={() => refetch()}
          type="button"
          className="bg-[#1C1C1E] text-white px-6 py-2 rounded font-bold"
        >
          Tekrar Dene
        </button>
      </div>
    </div>
  );
}
```

**Skeleton/pending visual pattern** (lines 146-175):
```typescript
function CartSkeleton() {
  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <div className="h-8 w-40 bg-gray-200 rounded animate-pulse mb-6" />
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_360px] gap-8">
        <div className="bg-white border border-[var(--color-border)] rounded p-4 space-y-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="flex gap-4 items-start py-4">
```

**Apply:** transcript should use the same white cards, destructive border for inline errors, Turkish retry copy, and `animate-pulse`/small pending label for streaming state. Add `role="log"`, `aria-live="polite"`, and bottom-padding per UI spec.

---

### `frontend/src/components/chat/ChatProductCard.tsx` (component, request-response)

**Analog:** `frontend/src/components/listing/ProductCard.tsx`

**Imports pattern** (lines 1-5):
```typescript
import { Link } from 'react-router-dom';
import { ROUTES } from '../../lib/routes';
import { formatTRY } from '../../lib/format';
import type { Product } from '../../lib/types';
import { productUrlSegment } from '../../lib/productUrls';
```

**Product display/link pattern** (lines 7-35):
```typescript
export function ProductCard({ product }: { product: Product }) {
  const outOfStock = product.stockQty <= 0;
  const displayName = product.name?.trim() || 'Ürün';
  return (
    <Link
      to={ROUTES.PRODUCT(productUrlSegment(product))}
      aria-label={displayName}
      className="block bg-white border border-[var(--color-border)] rounded overflow-hidden hover:shadow-md transition-shadow"
    >
      <div className="p-3">
        <h3 className="text-sm line-clamp-2 min-h-[2.5em] mb-2">{displayName}</h3>
        <p className="text-xl font-bold">{formatTRY(product.priceGross)}</p>
      </div>
    </Link>
  );
}
```

**Apply:** compact chat product cards should reuse `ROUTES.PRODUCT(productUrlSegment(product))`, `formatTRY`, `line-clamp-2`, white surface, and border tokens. Do not copy the full image-heavy listing card; keep chat card compact.

---

### `frontend/src/components/chat/ChatHandoffCard.tsx` (component, request-response)

**Analog:** `frontend/src/pages/CartPage.tsx` and `frontend/src/lib/routes.ts`

**Cart summary layout pattern** (`CartPage.tsx` lines 125-141):
```typescript
return (
  <div className="mx-auto max-w-7xl px-4 py-8">
    <h1 className="text-2xl font-bold mb-6">Sepetim ({cart.items.length} ürün)</h1>
    <div className="grid grid-cols-1 lg:grid-cols-[1fr_360px] gap-8">
      <section className="bg-white border border-[var(--color-border)] rounded p-4">
        {cart.items.map(item => (
          <CartLineItemRow
            key={item.productId}
            item={item}
```

**Route constants pattern** (`routes.ts` lines 1-16):
```typescript
export const ROUTES = {
  HOME: '/',
  CATEGORY: (slug: string) => `/${slug}`,
  SEARCH: '/arama',
  PRODUCT: (slugAndId: string) => `/urun/${slugAndId}`,
  CART: '/sepetim',
  CHECKOUT_ADDRESS: '/odeme/adres',
  CHECKOUT_PAYMENT: '/odeme/odeme',
  CHECKOUT_RESULT: '/odeme/sonuc',
  LOGIN: '/giris-yap',
```

**Apply:** handoff cards should be bordered white cards with explicit `Link` CTAs to `ROUTES.CART`, `ROUTES.CHECKOUT_ADDRESS`, or external payment URL. Never auto-navigate after tool results.

---

### `frontend/src/api/chatApi.ts` (service, streaming)

**Analog:** `frontend/src/lib/apiClient.ts` + `ai-service/src/main/java/com/n11/ai/interfaces/sse/ChatStreamController.java`

**Gateway base/auth/error pattern** (`apiClient.ts` lines 1-4, 21-43):
```typescript
import { getToken, clearToken } from './tokenStore';

const API_BASE = import.meta.env.VITE_API_BASE_URL;
const API_PREFIX = '/api/v1';

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {}
): Promise<T> {
  const token = getToken();
  const headers = new Headers(init.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  headers.set('Accept', 'application/json, application/problem+json');

  const url = `${API_BASE}${API_PREFIX}${path}`;
  const res = await fetch(url, { ...init, headers });

  if (res.status === 401) {
    clearToken();
    const redirectUrl = window.location.pathname + window.location.search;
    window.dispatchEvent(
      new CustomEvent('auth:unauthorized', { detail: { redirectUrl } })
    );
    throw new ApiError(401, await safeProblem(res));
  }
```

**Backend POST SSE contract** (`ChatStreamController.java` lines 38-57, 72-77):
```java
@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(HttpServletRequest req, @Valid @RequestBody ChatRequest body) {
    UUID userId = resolveOptionalUserId(req);
    String correlationId = req.getHeader(HEADER_CORRELATION_ID);
    String cid = (correlationId == null || correlationId.isBlank())
        ? UUID.randomUUID().toString() : correlationId;

    SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
    AtomicBoolean completed = new AtomicBoolean(false);
    ...
    useCase.handleStream(body.conversationId(), body.message(), userId, cid,
        (eventName, payload) -> sendIfOpen(emitter, completed, eventName, payload));
    sendIfOpen(emitter, completed, SseEvents.DONE,
        Map.of("conversationId", body.conversationId().toString()));
}

private static void sendIfOpen(SseEmitter emitter, AtomicBoolean completed,
                               String eventName, Object data) {
    emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
}
```

**Request DTO** (`ChatRequest.java` lines 8-11):
```java
public record ChatRequest(
    @NotNull UUID conversationId,
    @NotBlank String message
) {}
```

**Apply:** implement POST-capable streaming with `fetch`, `Accept: text/event-stream`, JSON body `{ conversationId, message }`, token injection from `getToken`, 401 dispatch matching `apiClient`, and a small SSE frame parser for named events.

---

### `frontend/src/hooks/useChatAssistant.ts` (hook, streaming)

**Analog:** `frontend/src/hooks/useCart.ts` + `frontend/src/pages/ProductDetailPage.tsx`

**Query key / auth-gated hook pattern** (`useCart.ts` lines 1-15):
```typescript
import { useQuery } from '@tanstack/react-query';
import { fetchCart } from '../api/cartApi';
import { useAuthStore } from '../store/authStore';
import type { Cart } from '../lib/types';

export const cartQueryKey = ['cart'] as const;

export function useCart() {
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);
  return useQuery<Cart>({
    queryKey: cartQueryKey,
    queryFn: fetchCart,
    enabled: isAuthenticated,
    placeholderData: { userId: '', items: [], updatedAt: '' } as Cart,
  });
}
```

**Cart invalidation/toast after confirmed success** (`ProductDetailPage.tsx` lines 38-48):
```typescript
const addMutation = useMutation({
  mutationFn: () => addToCart({ productId: productId!, qty: 1 }),
  onSuccess: () => {
    qc.invalidateQueries({ queryKey: cartQueryKey });
    toast.success('Ürün sepete eklendi.');
  },
  onError: (err) => {
    const detail = err instanceof ApiError ? err.problem?.detail : undefined;
    toast.error(detail ?? 'Bir hata oluştu. Lütfen tekrar deneyiniz.');
  },
});
```

**Login redirect pattern** (`ProductDetailPage.tsx` lines 59-66):
```typescript
function handleAddToCart() {
  if (!isAuthenticated) {
    const redirectUrl = encodeURIComponent(location.pathname);
    navigate(`${ROUTES.LOGIN}?redirectUrl=${redirectUrl}`);
    return;
  }
  addMutation.mutate();
}
```

**Apply:** persist `conversationId` in browser storage, append transcript events in hook state, invalidate `cartQueryKey` only after confirmed `tool_result.ok === true` for cart mutations, and reuse login redirect shape for auth-required inline CTAs.

---

### `frontend/src/lib/chatEvents.ts` and `frontend/src/lib/types.ts` additions (utility/model, transform)

**Analog:** `frontend/src/lib/types.ts` + `ai-service/src/main/java/com/n11/ai/interfaces/sse/SseEvents.java`

**Frontend DTO style** (`types.ts` lines 30-41, 90-102, 122-152):
```typescript
export interface Product {
  id: string;
  name: string;
  description: string;
  priceGross: number;
  kdvRate: number;
  imageUrl: string;
  categoryId: string;
  categoryLabel: string;
  stockQty: number;
  createdAt: string;
}

export interface Cart {
  userId: string;
  items: CartLineItem[];
  updatedAt: string;
}

export type OrderStatus =
  | 'PENDING'
  | 'STOCK_RESERVED'
  | 'PAID'
  | 'CONFIRMED'
  | 'STOCK_FAILED'
  | 'PAYMENT_FAILED'
  | 'CANCELLED';
```

**Backend event names** (`SseEvents.java` lines 3-9):
```java
public final class SseEvents {
    public static final String DELTA = "delta";
    public static final String TOOL_CALL = "tool_call";
    public static final String TOOL_RESULT = "tool_result";
    public static final String DONE = "done";
    public static final String ERROR = "error";
    private SseEvents() {}
}
```

**Backend emitted payload shape** (`ChatService.java` lines 87-97, 119-132, 140-142):
```java
emit.accept("delta", Map.of(
    "text", delta,
    "conversationId", store.conversationId().toString()));
...
emit.accept("tool_call", Map.of(
    "name", call.name(),
    "callId", call.callId(),
    "argsJson", call.argsJson()));
...
emit.accept("tool_result", Map.of(
    "callId", call.callId(),
    "ok", ok,
    "summary", summary.length() > 200 ? summary.substring(0, 200) + "…" : summary));
...
emit.accept("done", Map.of(
    "conversationId", store.conversationId().toString(),
    "finalText", finalText.toString()));
```

**Apply:** model a discriminated union for `delta | tool_call | tool_result | done | error`; keep DTOs neutral and aligned with backend strings. Add transform helpers from tool names to Turkish chips.

---

### `.github/workflows/ci.yml` (config, batch)

**Analog:** `.github/workflows/ci.yml`

**Existing trigger/job pattern** (lines 1-10):
```yaml
name: ci
on:
  push:
    branches: [main, master, develop]
  pull_request:
    branches: [main, master, develop]

jobs:
  build:
    runs-on: ubuntu-latest
```

**Java setup/cache/build pattern** (lines 12-25):
```yaml
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: corretto
          java-version: '21'
      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-${{ hashFiles('**/*.gradle.kts','**/libs.versions.toml') }}
      - name: Build & Test
        run: ./gradlew build --no-daemon
```

**Dependent infra-test pattern** (lines 27-37):
```yaml
  infra-tests:
    runs-on: ubuntu-latest
    needs: build
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: corretto
          java-version: '21'
      - name: Run boundary smoke
        run: ./gradlew :infra-tests:test --no-daemon
```

**Apply:** extend rather than replace. Add Node setup and `npm ci && npm run build && npm test` in a frontend job, preserve Gradle build and infra tests, add Slack notification steps gated by `if: always()` and secret presence.

---

### `.github/workflows/release.yml` (config, batch)

**Analog:** `.github/workflows/ci.yml` + Gradle Jib module configs

**Jib plugin pattern** (`build.gradle.kts` lines 1-5):
```kotlin
plugins {
    id("org.springframework.boot") version "3.5.14" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    id("com.google.cloud.tools.jib") version "3.5.3" apply false
}
```

**Module Jib target pattern** (`ai-service/build.gradle.kts` lines 58-65):
```kotlin
jib {
    from { image = "eclipse-temurin:21-jre" }
    to { image = "n11/ai-service:dev" }
    container {
        ports = listOf("8088")
        jvmFlags = listOf("-XX:+UseG1GC", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0")
    }
}
```

**Apply:** create a release-tag workflow for `v*` tags that logs in to GHCR using `GITHUB_TOKEN`, publishes each Spring Boot service with Jib to `ghcr.io/<owner>/<repo>/<service>:<tag>` and `:latest`, and sends Slack success/failure notifications. Keep secrets in GitHub Actions secrets only.

---

### `docker-compose.yml` (config, request-response)

**Analog:** `docker-compose.yml`

**Service/env/healthcheck pattern** (lines 472-500):
```yaml
  ai-service:
    image: n11/ai-service:dev
    container_name: n11-ai-service
    env_file:
      - .env
    environment:
      SPRING_CONFIG_IMPORT: configserver:http://config-server:8888?fail-fast=true&max-attempts=10&max-interval=1500&multiplier=1.2&initial-interval=1100
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_PROFILES_ACTIVE: docker
      GATEWAY_PATH_PREFIX: /api/v1
      GEMINI_API_KEY: ${GEMINI_API_KEY:-}
    depends_on:
      postgres:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
      config-server:
        condition: service_healthy
      identity-service:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O- http://localhost:8088/actuator/health | grep -q '\"status\":\"UP\"'"]
      interval: 5s
      timeout: 3s
      retries: 6
      start_period: 25s
```

**Gateway-only port pattern** (lines 208-216, 217-247):
```yaml
  # identity-service -- first business service (Phase 3, D-21).
  # Port 8081 (gateway is 8080; 808x convention per ARCHITECTURE.md).
  # Image built by: ./gradlew :identity-service:jibDockerBuild
  #
  # IMPORTANT: NO `ports:` mapping -- Pitfall #14 (gateway path validation /
  # direct-service bypass). Identity-service is reachable ONLY through the
  # gateway on port 8080 from outside the Docker network.
  identity-service:
    image: n11/identity-service:dev
```

**Apply:** preserve gateway-only service exposure. Add/verify `full` profile and frontend/demo services without exposing internal service ports. Use env variables for GHCR image tags and tunnel tokens; no hardcoded secrets.

---

### `.env.example` and `frontend/.env.example` (config, request-response)

**Analog:** existing env examples

**Root no-secrets placeholder pattern** (`.env.example` lines 39-50, 57-71):
```dotenv
# Iyzico sandbox credentials — from https://sandbox.iyzipay.com merchant dashboard.
# NEVER commit real keys. Use sandbox (sandbox-api.iyzipay.com) for Phase 6.
IYZICO_API_KEY=
IYZICO_SECRET_KEY=

# Public HTTPS base URL of the API gateway exposed via Cloudflare Tunnel or ngrok.
PUBLIC_BASE_URL=

# ── Phase 8 (ai-service) ───────────────────────────────────────────────
GEMINI_API_KEY=

# ─── Phase 9: MCP Server ──────────────────────────────────────────────
MCP_API_KEY=

# ── Notifications ──────────────────────────────────────────────────────
# SLACK_WEBHOOK_URL=
```

**Frontend public env pattern** (`frontend/.env.example` lines 1-2):
```dotenv
VITE_API_BASE_URL=http://localhost:8080
VITE_FREE_SHIPPING_THRESHOLD=500
```

**Apply:** add Slack/tunnel/registry/demo placeholders with `<set-in-env>` or blank values only. Put public frontend config in `frontend/.env.example`; keep secrets in root `.env.example` and GitHub Actions secrets documentation.

---

### `README.md` (docs, batch)

**Analog:** `payment-service/README.md`

**Environment matrix pattern** (lines 8-22):
```markdown
## Environment

All secrets live in the root `.env` file (gitignored) and are injected at runtime.
**Never commit real keys.**

| Variable | Required | Description |
|---|---|---|
| `IYZICO_API_KEY` | yes | Iyzico sandbox API key (from Iyzico Sandbox Dashboard) |
| `IYZICO_SECRET_KEY` | yes | Iyzico sandbox secret key (from Iyzico Sandbox Dashboard) |
| `PUBLIC_BASE_URL` | yes | Public HTTPS URL of the API gateway — Cloudflare Tunnel hostname or ngrok forwarding URL ... |
```

**Cloudflare/ngrok runbook pattern** (lines 30-37, 81-104):
```markdown
## Cloudflare Tunnel (primary)

Cloudflare Tunnel creates a permanent HTTPS hostname routed to your local API gateway on port 8080.
This is the preferred path for live Iyzico sandbox callbacks and for the interview demo URL.

## ngrok fallback

Use ngrok if Cloudflare setup fails or the domain is not available.
ngrok gives a random subdomain on every free-tier start — update `PUBLIC_BASE_URL` each time.
```

**Troubleshooting pattern** (lines 152-170):
```markdown
## Callback troubleshooting

**Callback not received (payment stays PENDING)**
- Verify `PUBLIC_BASE_URL` is reachable: `curl -v https://<your-tunnel-url>/actuator/health`
- Confirm the tunnel is running and shows connected (cloudflared log or ngrok web UI)
- Check Iyzico dashboard sandbox logs for callback delivery status
```

**Apply:** root README should start with the one-command demo path, then env matrix, GHCR image pinning, Cloudflare primary/ngrok fallback, Slack workflow notes, MCP demo snippet, and troubleshooting. Commands stay English; storefront labels/copy are Turkish.

## Shared Patterns

### Frontend gateway-only auth and 401 handling
**Source:** `frontend/src/lib/apiClient.ts` lines 21-43  
**Apply to:** `frontend/src/api/chatApi.ts`, any chat product/cart actions
```typescript
const token = getToken();
const headers = new Headers(init.headers);
if (token) headers.set('Authorization', `Bearer ${token}`);
...
if (res.status === 401) {
  clearToken();
  const redirectUrl = window.location.pathname + window.location.search;
  window.dispatchEvent(
    new CustomEvent('auth:unauthorized', { detail: { redirectUrl } })
  );
  throw new ApiError(401, await safeProblem(res));
}
```

### Cart refresh after confirmed mutations
**Source:** `frontend/src/pages/ProductDetailPage.tsx` lines 38-48; `frontend/src/hooks/useCart.ts` lines 6-15  
**Apply to:** chat `add_to_cart`, `update_cart_item`, `remove_cart_item` tool-result handlers
```typescript
export const cartQueryKey = ['cart'] as const;

const addMutation = useMutation({
  mutationFn: () => addToCart({ productId: productId!, qty: 1 }),
  onSuccess: () => {
    qc.invalidateQueries({ queryKey: cartQueryKey });
    toast.success('Ürün sepete eklendi.');
  },
});
```

### Turkish login redirect CTA
**Source:** `frontend/src/pages/ProductDetailPage.tsx` lines 59-66; `frontend/src/components/feedback/AuthEventBridge.tsx` lines 14-31  
**Apply to:** guest auth-required chat inline card
```typescript
if (!isAuthenticated) {
  const redirectUrl = encodeURIComponent(location.pathname);
  navigate(`${ROUTES.LOGIN}?redirectUrl=${redirectUrl}`);
  return;
}
```

### Typed SSE event names and payloads
**Source:** `ai-service/src/main/java/com/n11/ai/interfaces/sse/SseEvents.java` lines 3-9; `ChatService.java` lines 87-142  
**Apply to:** `chatEvents.ts`, `chatApi.ts`, transcript renderer
```java
public static final String DELTA = "delta";
public static final String TOOL_CALL = "tool_call";
public static final String TOOL_RESULT = "tool_result";
public static final String DONE = "done";
public static final String ERROR = "error";
```

### Tailwind/token visual style
**Source:** `frontend/src/components/layout/Header.tsx` lines 12-18, 47-52, 75-79  
**Apply to:** all chat components
```typescript
className="sticky top-0 z-50 bg-[var(--color-surface-header)] border-b border-[var(--color-border)] h-16"
className="w-full h-10 pl-4 pr-12 rounded border border-[var(--color-border)] focus:outline-2 focus:outline-[#1C1C1E]"
className="absolute -top-1 -right-1 bg-[#1C1C1E] text-white text-xs rounded-full min-w-[20px] h-5 px-1.5 inline-flex items-center justify-center font-bold"
```

### Jib container image pattern
**Source:** `ai-service/build.gradle.kts` lines 58-65; `api-gateway/build.gradle.kts` lines 107-118  
**Apply to:** release workflow image publishing
```kotlin
jib {
    from { image = "eclipse-temurin:21-jre" }
    to { image = "n11/ai-service:dev" }
    container {
        ports = listOf("8088")
        jvmFlags = listOf("-XX:+UseG1GC", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0")
    }
}
```

### Compose healthcheck and internal-only service pattern
**Source:** `docker-compose.yml` lines 472-500 and 208-216  
**Apply to:** full-profile service hardening and frontend/demo compose additions
```yaml
env_file:
  - .env
environment:
  SPRING_CONFIG_IMPORT: configserver:http://config-server:8888?fail-fast=true&max-attempts=10&max-interval=1500&multiplier=1.2&initial-interval=1100
  EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
  SPRING_PROFILES_ACTIVE: docker
healthcheck:
  test: ["CMD-SHELL", "wget -q -O- http://localhost:8088/actuator/health | grep -q '\"status\":\"UP\"'"]
```

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `frontend/src/api/chatApi.ts` POST SSE parser internals | service | streaming | Backend emits SSE, but no existing browser-side POST `text/event-stream` parser exists. Use `apiClient.ts` auth posture plus `ChatStreamController` event contract. |

## Metadata

**Analog search scope:** `frontend/src/**/*.{ts,tsx,css}`, `ai-service/src/main/java/**/*.java`, `.github/workflows/*.yml`, `docker-compose.yml`, `*.env.example`, service `README.md`, Gradle build files  
**Files scanned:** 90+ frontend/backend/devops candidates; 19 analog files read  
**Pattern extraction date:** 2026-05-02
