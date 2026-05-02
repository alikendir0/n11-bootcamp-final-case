# Phase 11: Frontend Chat Assistant + DevOps Deploy - Context

**Gathered:** 2026-05-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Add the user-facing AI shopping assistant to the existing Turkish React storefront and complete the demo/deploy posture. This phase owns the floating chat bubble, streaming transcript UI, chat-to-cart/order handoff, Jib image publishing, GitHub Actions build/test/release workflow, Slack build notifications, local docker-compose demo path, public tunnel documentation, Jenkins comparison doc, and root README demo instructions.

This phase does not own new backend AI capabilities, new tools, a separate recommendation UI, semantic-search UI, multiple LLM providers, new payment methods, or a new deploy target. The assistant consumes the Phase 8 ai-service and shared agent-toolset; deployment stays local docker-compose on the candidate machine with public exposure through the already-chosen Cloudflare Tunnel preferred / ngrok fallback posture.

</domain>

<decisions>
## Implementation Decisions

### Chat Panel Shape

- **D-01:** The assistant opens as a **right-side drawer** from a bottom-right floating bubble, not a centered modal or tiny popover. The component should live at the `Layout.tsx` level so it is present on every storefront page.
- **D-02:** Desktop drawer width is **420px**. This is large enough for transcript/tool cards without covering too much of PDP/cart content during the demo.
- **D-03:** Mobile opens as a **full-screen sheet** with its own header and close control. Do not squeeze a desktop drawer into narrow screens.
- **D-04:** The chat panel **stays open across route navigation**. Navigation from product links, cart CTAs, or checkout CTAs must not discard the conversation UI.

### Streaming Transcript

- **D-05:** Tool-use events render as **inline Turkish status chips** in the transcript. Map `tool_call` / `tool_result` events to concise labels like `Ürünler aranıyor...`, `Sepet güncelleniyor...`, and `Ödeme bağlantısı hazırlanıyor...`.
- **D-06:** Assistant answers can include **Turkish text plus compact structured cards** when tool results contain product/cart/order data. Product cards should stay compact and not become a full listing grid inside chat.
- **D-07:** SSE or Gemini failures render as an **inline retry state** inside the transcript with a Turkish error message and `Tekrar dene` action that re-sends the same message.
- **D-08:** Guest auth-required replies render an **inline login CTA**. The assistant explains in Turkish that login is needed, and the CTA goes to `/giris-yap` with the current page as `redirectUrl` using the existing Phase 10 auth pattern.

### Cart and Checkout Handoff

- **D-09:** After chat successfully adds/updates/removes a cart item, the frontend waits for backend/tool confirmation, then invalidates the cart query and updates the header counter within 1 second. Show a Turkish success toast; do not auto-open the cart page.
- **D-10:** Chat product cards include direct **`Ürünü Gör`** and **`Sepete Ekle`** actions. `Ürünü Gör` routes to the existing PDP URL; `Sepete Ekle` invokes the same cart mutation path as the storefront.
- **D-11:** For order/payment handoff, keep the user in chat and show explicit CTAs to checkout/payment instead of auto-navigation. The user controls when to go to `/odeme/adres` or open the Iyzico payment link.
- **D-12:** No optimistic header-cart updates for chat mutations. Show pending state in chat if useful, but update the real header/cart state only after `tool_result.ok === true` and a cart query refresh succeeds.

### Release and Deploy Posture

- **D-13:** Release-tag image publishing uses **GHCR**, not Docker Hub. This keeps credentials/secrets minimal because the project already runs on GitHub Actions.
- **D-14:** Release images publish both the immutable release tag and `latest`: `ghcr.io/<owner>/<repo>/<service>:<vX.Y.Z>` plus `:latest`. The local compose/demo flow may use `latest`; the README should mention pinning a `v*` tag for reproducibility.
- **D-15:** Slack notifications fire for both **CI build/test results** and **release image-publish results**, with success and failure messages. `SLACK_WEBHOOK_URL` stays a secret/env var only.
- **D-16:** README deploy flow is optimized around a **one-command primary demo path** first, with the env-var matrix and troubleshooting below. The quick path should lead with build/pull, `docker compose --profile full up`, tunnel check, and the external `curl https://<tunnel-hostname>/api/v1/products` proof.

### OpenCode's Discretion

- Exact component/file names for the chat widget, drawer, transcript, cards, and hooks.
- Whether to implement the current `POST /chat/stream` backend stream with `fetch` + SSE parsing or to adjust the backend contract to a GET-compatible stream. The planner must choose the smallest correct path after inspecting the current backend and browser support.
- Exact Turkish microcopy for status chips, empty state, retry state, and chat intro, as long as it follows n11-recon voice and uses `Yapay Zeka Alışveriş Asistanı` as the assistant label.
- Exact GHCR namespace expression in workflow YAML, as long as it works for the repository owner and keeps secrets out of source.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Scope and Requirements

- `.planning/ROADMAP.md` — Phase 11 goal, dependencies, success criteria, and risks.
- `.planning/REQUIREMENTS.md` — FE-12 and DEV-01 through DEV-09 are the requirement IDs owned by this phase.
- `.planning/PROJECT.md` — local docker-compose deploy target, Cloudflare Tunnel preferred / ngrok fallback, no-secrets policy, frontend stack lock, AI differentiator strategy.
- `.planning/STATE.md` — current handoff and accumulated decisions from completed phases.
- `CLAUDE.md` — non-negotiables: Turkish frontend copy, gateway-only auth posture, no secrets, shared agent-toolset, provider-agnostic AI boundary.

### Prior-Phase Contracts

- `.planning/phases/08-ai-port-adapter-agent-toolset/08-CONTEXT.md` — ai-service chat backend, typed SSE events (`delta`, `tool_call`, `tool_result`, `done`, `error`), conversation ID persistence, tool auth behavior, shared agent-toolset.
- `.planning/phases/09-mcp-server/09-CONTEXT.md` — shared gateway tunnel posture for `/mcp/**`, GHCR/Jib image continuity for stdio demo, MCP runbook expectations that Phase 11 README should fold in.
- `.planning/phases/10-frontend-storefront/10-CONTEXT.md` — frontend toolchain, routes, auth/token handling, no chat placeholder, cart query/header badge patterns, gateway-only API base URL.
- `.planning/api-contracts.md` §2, §3, §6 — gateway route table, public allowlist, SSE response-timeout/no-buffering caveat.
- `.planning/saga-contracts.md` — order/payment saga names used only for README/demo explanation; frontend still talks REST/SSE only.

### Design and Copy

- `.planning/intel/n11-recon.md` — Phase 11 chat label phrase #644 `Yapay Zeka Alışveriş Asistanı`; §7 confirms no live n11 chat panel exists, so this widget is greenfield; §4/§6 carry visual tokens and n11 layout constraints.
- `.planning/intel/screenshots/` — existing storefront visual baseline from n11 recon.

### Live Code Touchpoints

- `frontend/src/components/layout/Layout.tsx` — chat widget integration point; already wraps all routed pages plus `ToastBridge` and `AuthEventBridge`.
- `frontend/src/components/layout/Header.tsx` — existing cart badge uses `useCartItemCount`; chat mutations must refresh this path.
- `frontend/src/hooks/useCart.ts` — `cartQueryKey = ['cart']`; invalidate this after confirmed chat cart mutations.
- `frontend/src/lib/apiClient.ts` — gateway-only API client with token injection and 401 event dispatch; chat REST/SSE client should follow this auth/error posture.
- `frontend/src/router.tsx` and `frontend/src/lib/routes.ts` — Turkish route names and auth guards for login/cart/checkout/account.
- `ai-service/src/main/java/com/n11/ai/interfaces/sse/ChatStreamController.java` — current stream endpoint is `POST /chat/stream` returning `text/event-stream` via `SseEmitter`.
- `ai-service/src/main/java/com/n11/ai/domain/chat/ChatService.java` — emits `delta`, `tool_call`, `tool_result`, `done`, and `error` event payloads.
- `ai-service/src/main/java/com/n11/ai/interfaces/rest/dto/ChatRequest.java` — frontend must send `conversationId` UUID plus `message`.
- `config-server/src/main/resources/config/api-gateway.yml` — already has `/api/v1/chat/stream/**` route with `response-timeout: -1` and `/mcp/**` route.
- `docker-compose.yml` — currently lists the backend services and internal-only service ports; Phase 11 must add/verify `full` profile behavior and frontend/demo posture.
- `.github/workflows/ci.yml` and `.github/workflows/security.yml` — existing CI/security workflow baseline to extend with frontend build/test, Jib image publish on release tags, and Slack notifications.
- `.env.example` and `frontend/.env.example` — env-var matrix sources; add Slack/tunnel/registry/demo variables without secrets.

### External Docs To Research Before Planning DevOps Tasks

- `https://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin` — Jib Gradle publishing to registries and tag configuration.
- `https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry` — GHCR auth, package names, permissions.
- `https://docs.github.com/en/actions` — release-tag workflows, permissions, secrets, matrix jobs.
- `https://api.slack.com/messaging/webhooks` — incoming webhook payload shape and secret handling.
- `https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/` — Cloudflare Tunnel quickstart and token/env handling.
- `https://ngrok.com/docs` — fallback tunnel setup and auth token handling.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- `Layout.tsx` is the clean place to mount the chat widget because it wraps every storefront route and already hosts cross-cutting UI bridges.
- `Header.tsx` + `useCartItemCount()` already implement the cart badge; chat does not need its own cart count state.
- `useCart.ts` exposes `cartQueryKey`; confirmed chat mutations should invalidate this query to satisfy the 1-second header-counter requirement.
- `apiClient.ts` centralizes `VITE_API_BASE_URL`, Bearer token injection, RFC-7807 parsing, and 401 redirect event dispatch. New chat API code should reuse the same base URL and token source.
- `ChatStreamController` and `ChatService` already emit the typed stream contract needed by the UI; Phase 11 mainly needs a robust browser consumer and rendering layer.
- Existing `.github/workflows/ci.yml` already runs Gradle build/test and infra-tests; extend rather than replace.

### Established Patterns

- Frontend copy is Turkish; identifiers stay English.
- All frontend calls go through `${VITE_API_BASE_URL}/api/v1/...`; no direct service URLs.
- Auth is gateway-only: browser sends `Authorization: Bearer <token>`; services receive `X-User-Id` from the gateway.
- Chat conversations require a frontend-generated UUID `conversationId`; guests keep it in browser storage, authed users get DB persistence through ai-service.
- Gateway SSE route already disables response timeout and avoids body-modifying filters.
- Jib is the only backend containerization mechanism; no Dockerfiles.
- Secrets belong in `.env`, GitHub Actions secrets, or runtime env vars only. Never commit Gemini, Iyzico, JWT, Slack, registry, Cloudflare, or ngrok secrets.

### Integration Points

- Chat widget mounts in frontend layout; its state should persist across React Router route changes.
- Chat stream request targets `/api/v1/chat/stream` with `{ conversationId, message }`. Because current backend uses POST SSE, the frontend cannot blindly use native `EventSource` without a contract change or a POST-capable streaming implementation.
- `tool_call` events drive inline status chips; `tool_result` events drive confirmation/error state and optional compact cards.
- Cart mutation tool success invalidates `cartQueryKey` and shows a Turkish toast.
- Product card `Ürünü Gör` uses existing PDP URL helpers; `Sepete Ekle` uses existing cart mutation API.
- Release workflow publishes 13 Jib images to GHCR on `v*` tags; compose/demo docs consume those images.
- README should fold in Phase 6 Iyzico card/runbook, Phase 9 MCP demo snippet, the public tunnel URL check, and the one-command local demo path.

</code_context>

<specifics>
## Specific Ideas

- Assistant label: **`Yapay Zeka Alışveriş Asistanı`**.
- Desktop: bottom-right bubble opens a 420px right-side drawer.
- Mobile: full-screen sheet.
- Tool chips should use direct Turkish action language: `Ürünler aranıyor...`, `Sepete ekleniyor...`, `Sepet güncelleniyor...`, `Sipariş hazırlanıyor...`, `Ödeme bağlantısı hazırlanıyor...`.
- Chat product cards should be compact: title, price, stock/free-shipping hint if available, `Ürünü Gör`, `Sepete Ekle`.
- Demo README should emphasize the wow path: storefront search/PDP/cart/checkout plus assistant-driven cart update plus MCP external-agent flow.

</specifics>

<deferred>
## Deferred Ideas

- Mid-stream cancellation / stop generation remains FE-V2-01, not a Phase 11 requirement unless trivial.
- Conversational PDP summaries, compare-products Q&A, cart-aware suggestions, and recommendations panel remain AI-V2 / out of scope.
- Working Kapıda Ödeme remains FE-V2-03; Phase 11 does not add payment methods.
- Multi-environment cloud deployment remains out of scope; local docker-compose plus tunnel is the locked deploy target.
- Docker Hub publishing is deferred; GHCR is the v1 release registry.

</deferred>

---

*Phase: 11-frontend-chat-assistant-devops-deploy*
*Context gathered: 2026-05-02*
