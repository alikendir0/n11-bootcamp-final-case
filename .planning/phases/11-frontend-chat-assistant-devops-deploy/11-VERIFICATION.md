---
phase: 11-frontend-chat-assistant-devops-deploy
verified: 2026-05-02T17:30:00Z
status: human_needed
score: 17/20 must-haves verified
overrides_applied: 0
gaps:
  - truth: "Chat conversation persists across page refresh (visible transcript rehydrates)"
    status: partial
    reason: "Backend conversation state persists in Postgres (AI-09), and conversationId is stored in localStorage and reused. However, the frontend transcript (messages[]) is not persisted or rehydrated from the backend on refresh — the chat panel appears empty after a browser reload."
    artifacts:
      - path: "frontend/src/hooks/useChatAssistant.ts"
        issue: "Only conversationId is stored in localStorage; messages useState initializes to [] with no rehydration useEffect or backend history fetch."
    missing:
      - "Add useEffect in useChatAssistant to load previous messages from backend GET endpoint, OR persist messages[] in localStorage alongside conversationId."
human_verification:
  - test: "Live public tunnel reaches products API"
    expected: "scripts/verify-demo-tunnel.sh prints 'DEV-05 tunnel proof passed: https://$HOSTNAME/api/v1/products returned 200' and exits 0"
    why_human: "Requires a live Cloudflare Tunnel or ngrok session with a real external hostname outside the repository."
  - test: "Chat cart badge updates within 1 second of adding via chat"
    expected: "Click 'Sepete Ekle' on a ChatProductCard inside the chat panel; the header cart counter increments within 1 second."
    why_human: "Timing claim depends on network latency, React Query refetch interval, and running backend services — not verifiable statically."
  - test: "Chat transcript persistence across browser refresh"
    expected: "Send a message in chat, refresh the browser, reopen chat — previous messages should still be visible."
    why_human: "Current implementation only persists conversationId; whether backend history is fetched or localStorage stores messages requires runtime verification."
---

# Phase 11: Frontend Chat Assistant + DevOps Deploy Verification Report

**Phase Goal:** Add the floating Turkish chat-assistant bubble to the storefront (persistent across pages, SSE token streaming, tool-use round-trips visible in UI), and ship the deploy: Jib for every service, GitHub Actions CI for build + test (with image-publish on release tags), local docker-compose stack on the candidate's machine running all 13 services + Postgres + RabbitMQ, public demo URL via Cloudflare Tunnel (preferred) or ngrok, Slack notifications on CI build success/failure, plus the Jenkins comparison doc and a complete README.

**Verified:** 2026-05-02T17:30:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #   | Truth   | Status     | Evidence       |
| --- | ------- | ---------- | -------------- |
| 1   | Frontend POST SSE client consumes ai-service stream through gateway | ✓ VERIFIED | `frontend/src/api/chatApi.ts` uses `fetch` POST with `Accept: text/event-stream` to `${VITE_API_BASE_URL}/api/v1/chat/stream`; no EventSource used |
| 2   | Tool result events expose structured data for card rendering | ✓ VERIFIED | `ChatService.java` emits `tool_result` with `toolName`, `resultType`, `data`; `chatEvents.ts` parses them; `ChatTranscript.tsx` renders cards via type guards |
| 3   | Conversation ID persists in browser storage across navigation/refresh | ✓ VERIFIED | `useChatAssistant.ts` stores `n11.chat.conversationId` in `localStorage` and reuses it; `crypto.randomUUID()` only on first use |
| 4   | Cart query invalidates only after confirmed `tool_result.ok === true` | ✓ VERIFIED | `useChatAssistant.ts` calls `queryClient.invalidateQueries({ queryKey: cartQueryKey })` only inside `event.type === 'tool_result'` with `event.ok && isCartMutationTool(toolName)` |
| 5   | Floating chat bubble appears bottom-right on every page | ✓ VERIFIED | `<ChatAssistant />` mounted in `Layout.tsx` after `<AuthEventBridge />`; `fixed right-4 bottom-4` trigger with `aria-label="Yapay Zeka Alışveriş Asistanını Aç"` |
| 6   | Desktop opens 420px drawer, mobile opens full-screen sheet | ✓ VERIFIED | `ChatDrawer.tsx` uses `w-screen md:w-[420px] h-[100dvh]` |
| 7   | Transcript renders streaming deltas, tool chips, retry, login CTA, product cards, handoff CTAs | ✓ VERIFIED | `ChatTranscript.tsx` has `role="log" aria-live="polite"`, empty state with suggested prompts, `Yanıt yazılıyor...`, `Tekrar dene`, `Giriş Yap`, and structured cards for products/cart/order/payment |
| 8   | Playwright smoke proves chat visibility across core routes | ✓ VERIFIED | `frontend/e2e/chat-assistant.spec.ts` has 7 tests covering `/`, `/arama`, `/sepetim`, drawer open/close, and route persistence |
| 9   | Frontend invariant gates enforce gateway-only posture and no EventSource | ✓ VERIFIED | `frontend/scripts/check-frontend-invariants.sh` passes all 15 gates (12–15 added in Plan 11-03); `npm run lint:invariants` exits 0 |
| 10  | CI runs backend build/test and frontend build/test/e2e on push/PR | ✓ VERIFIED | `.github/workflows/ci.yml` has `build`, `infra-tests`, and `frontend` jobs; frontend runs `npm ci`, `npm run build`, `npm test`, `npm run lint:invariants`, `npm run test:e2e -- chat-assistant.spec.ts` |
| 11  | Release tags `v*` publish all 13 Jib images to GHCR | ✓ VERIFIED | `.github/workflows/release.yml` has matrix over 13 services, `docker/login-action@v3` to `ghcr.io`, `./gradlew :<svc>:jib` with `-Djib.to.image` and `-Djib.to.tags=latest` |
| 12  | All 13 backend services have working Jib configuration | ✓ VERIFIED | `grep -c "com.google.cloud.tools.jib" */build.gradle.kts` = 13/13; `./gradlew :eureka-server:jibBuildTar` and `:mcp-server:jibBuildTar` pass |
| 13  | Slack notifications fire for CI/release success/failure | ✓ VERIFIED | `ci.yml` notify job and `release.yml` notify-release job both use `curl -X POST` with `✅ build green on <ref>` / `❌ build failed on <ref>`; no literal `hooks.slack.com` in YAML |
| 14  | `docker compose --profile full up` runs all 13 services + frontend + Postgres + RabbitMQ | ✓ VERIFIED | `docker-compose.yml` has 14 `profiles: ["full"]` entries (13 backend + frontend); `docker compose --profile full config` exits 0; postgres/rabbitmq are profile-free |
| 15  | Root README provides demo runbook with env matrix, tunnel proof, card numbers, rehearsal | ✓ VERIFIED | `README.md` has `## 30-second demo path`, env matrix with secrets/public config, Cloudflare/ngrok sections, Iyzico test card `5528 7900 0000 0008`, AI assistant demo, MCP demo, Slack examples, troubleshooting |
| 16  | `scripts/verify-demo-tunnel.sh` provides repeatable public tunnel proof | ✓ VERIFIED | Script exists, is executable (`chmod +x`), requires `DEMO_TUNNEL_HOSTNAME`, strips `https://`, curls `/api/v1/products`, exits 0 on HTTP 200 |
| 17  | `docs/devops-pipeline-comparison.md` maps Jenkins vs GitHub Actions pipeline logic | ✓ VERIFIED | Doc has Pipeline stages table, equivalent `Jenkinsfile` sketch, secrets/credentials table, "Why GitHub Actions" rationale, local-host deploy model; satisfies DEV-04 |
| 18  | Chat conversation persists across page refresh (visible transcript rehydrates) | ⚠️ PARTIAL | Backend state persists (AI-09 Postgres `ai_conversations` + `messages`), and `conversationId` is reused. Frontend `messages` state initializes to `[]` with no rehydration logic — chat panel is empty after refresh. |
| 19  | Chat cart actions reflect in header cart-counter within 1 second | ? UNCERTAIN | Invalidation logic is wired correctly, but the "within 1 second" timing claim requires a running stack and human observation. |
| 20  | External curl through public tunnel returns 200 with seed products | ? UNCERTAIN | `scripts/verify-demo-tunnel.sh` is ready, but a live Cloudflare/ngrok tunnel is outside repository source and must be verified manually. |

**Score:** 17/20 truths verified (3 partial/uncertain)

### Required Artifacts

| Artifact | Expected    | Status | Details |
| -------- | ----------- | ------ | ------- |
| `frontend/src/components/chat/ChatAssistant.tsx` | Floating trigger and drawer shell state | ✓ VERIFIED | Mounted in `Layout.tsx`; uses `useChatAssistant` hook; 56×56 fixed bottom-right trigger |
| `frontend/src/components/chat/ChatDrawer.tsx` | Responsive drawer/sheet UI | ✓ VERIFIED | `w-screen md:w-[420px] h-[100dvh]`; header title + subtitle + close button; composer with placeholder and send button |
| `frontend/src/components/chat/ChatTranscript.tsx` | Accessible transcript renderer | ✓ VERIFIED | `role="log" aria-live="polite"`; empty state with Turkish prompts; streaming indicator; retry CTA; structured cards via type guards |
| `frontend/src/components/chat/ChatProductCard.tsx` | Compact product card with cart mutation | ✓ VERIFIED | Renders name, price, stock state; `Ürünü Gör` Link; `Sepete Ekle` button with `useMutation` + `addToCart` + cart invalidation |
| `frontend/src/components/chat/ChatHandoffCard.tsx` | Cart/order/payment handoff CTAs | ✓ VERIFIED | `Sepete Git`, `Ödemeye Git`, `Ödemeye Devam Et` with `Link`/`a` navigation |
| `frontend/src/api/chatApi.ts` | POST-capable SSE client | ✓ VERIFIED | `fetch` POST with `Accept: text/event-stream`; Bearer token injection; 401 auth event dispatch; `AbortSignal` support |
| `frontend/src/hooks/useChatAssistant.ts` | Persistent transcript/conversation state hook | ✓ VERIFIED (partial) | `conversationId` in `localStorage`; streaming transcript accumulation; confirmed cart invalidation; retry. **Missing:** transcript rehydration on mount. |
| `ai-service/src/main/java/.../ChatService.java` | Structured tool_result SSE payloads | ✓ VERIFIED | `handleStream` emits `tool_result` with `callId`, `toolName`, `ok`, `summary`, `resultType`, `data` via `buildResultData` |
| `.github/workflows/ci.yml` | Build/test CI plus Slack notification | ✓ VERIFIED | `build`, `infra-tests`, `frontend`, `notify` jobs; `node-version: '24'`; `npm run lint:invariants`; `npm run test:e2e -- chat-assistant.spec.ts` |
| `.github/workflows/release.yml` | GHCR Jib image publish on `v*` tags | ✓ VERIFIED | Matrix of 13 services; `packages: write`; `docker/login-action@v3` to `ghcr.io`; immutable tag + latest |
| `docker-compose.yml` | Full profile and image-tag/env posture | ✓ VERIFIED | 14 `profiles: ["full"]` entries; `${IMAGE_REGISTRY:-n11}/svc:${IMAGE_TAG:-dev}`; `frontend` service with `node:24-alpine` |
| `README.md` | Candidate demo runbook | ✓ VERIFIED | 30-second demo path, env matrix, tunnel instructions, Iyzico card, AI/MCP demo paths, Slack examples, troubleshooting |
| `scripts/verify-demo-tunnel.sh` | Repeatable public tunnel reachability proof | ✓ VERIFIED | Executable; requires `DEMO_TUNNEL_HOSTNAME`; curls `/api/v1/products`; exits 0 on 200 |
| `docs/devops-pipeline-comparison.md` | Jenkins comparison requirement | ✓ VERIFIED | 227 lines; pipeline stages table; Jenkinsfile sketch; secrets mapping; local-host deploy model |

### Key Link Verification

| From | To  | Via | Status | Details |
| ---- | --- | --- | ------ | ------- |
| `Layout.tsx` | `ChatAssistant.tsx` | Component mount after `AuthEventBridge` | ✓ WIRED | `<ChatAssistant />` present in `Layout.tsx` return JSX |
| `chatApi.ts` | `${VITE_API_BASE_URL}/api/v1/chat/stream` | `fetch` POST with `Accept: text/event-stream` | ✓ WIRED | URL constructed via `import.meta.env.VITE_API_BASE_URL`; method POST; headers include `Accept: text/event-stream` |
| `useChatAssistant.ts` | `useCart.ts` | `queryClient.invalidateQueries({ queryKey: cartQueryKey })` | ✓ WIRED | Invalidates only after `tool_result.ok === true` for cart mutation tools |
| `ChatService.java` | `frontend/src/lib/chatEvents.ts` | `tool_result.data` parsed into typed events | ✓ WIRED | Backend emits `resultType` + `data`; frontend parser extracts `data` field; `ChatTranscript.tsx` type-guards and renders cards |
| `ChatProductCard.tsx` | `cartApi.ts` | `useMutation({ mutationFn: () => addToCart(...) })` | ✓ WIRED | Direct call to `addToCart` with `productId` and `qty: 1`; onSuccess invalidates `cartQueryKey` |
| `ci.yml` | GitHub Actions secrets | `secrets.SLACK_WEBHOOK_URL` | ✓ WIRED | Referenced only via `${{ secrets.SLACK_WEBHOOK_URL }}`; no literal URL committed |
| `release.yml` | GHCR | `docker/login-action@v3` + `secrets.GITHUB_TOKEN` | ✓ WIRED | `registry: ghcr.io`; `username: ${{ github.actor }}`; `password: ${{ secrets.GITHUB_TOKEN }}` |
| `README.md` | `docker-compose.yml` | `docker compose --profile full up` command | ✓ WIRED | README references the exact command; compose file supports it with all profiles |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| -------- | ------------- | ------ | ------------------ | ------ |
| `ChatService.java` | `payload.data` | `ToolDispatcher.dispatch()` result → `buildResultData()` | Yes — derives from actual `ToolResult.Ok` JSON | ✓ FLOWING |
| `chatEvents.ts` | `event.data` | SSE frame parser reads `data:` line from backend stream | Yes — parsed from real SSE chunks | ✓ FLOWING |
| `ChatTranscript.tsx` | `lastToolResult.data` | Runtime type guards (`isProductsData`, etc.) narrow `unknown` | Yes — passed through to card components only when type guard passes | ✓ FLOWING |
| `ChatProductCard.tsx` | `product` prop | `lastToolResult.data.products[]` from `tool_result` SSE event | Yes — rendered with `formatTRY`, stock labels, `productUrlSegment` | ✓ FLOWING |
| `useChatAssistant.ts` | `messages` | `useState<ChatTranscriptItem[]>([])` | Partial — accumulates during session, but **lost on refresh** (no rehydration) | ⚠️ STATIC |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| Frontend invariant gates pass | `cd frontend && bash scripts/check-frontend-invariants.sh` | All 15 invariants OK | ✓ PASS |
| Docker compose full profile renders | `docker compose --profile full config >/tmp/n11-compose-full.yml` | Exits 0 | ✓ PASS |
| No Dockerfiles in repo | `find . -name Dockerfile -not -path */node_modules/* -not -path */.git/* \| wc -l` | 0 | ✓ PASS |
| Jib builds for eureka-server | `./gradlew :eureka-server:jibBuildTar --no-daemon` | Exits 0 | ✓ PASS |
| Jib builds for mcp-server | `./gradlew :mcp-server:jibBuildTar --no-daemon` | Exits 0 | ✓ PASS |
| ChatServiceTest passes (structured tool_result) | `./gradlew :ai-service:test --tests "com.n11.ai.domain.chat.ChatServiceTest" --no-daemon` | Needs Gradle daemon + deps | ? SKIP (verified file contents show assertions for `resultType: products` and `data.products[]`) |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ----------- | ----------- | ------ | -------- |
| FE-12 | 11-01, 11-02, 11-03 | Floating chat assistant bubble; SSE streaming; tool-use visible in UI | ✓ SATISFIED | `ChatAssistant.tsx`, `ChatDrawer.tsx`, `ChatTranscript.tsx`, `chatApi.ts`, `useChatAssistant.ts`, `chat-assistant.spec.ts` |
| DEV-01 | 11-04 | Backend services containerized with Jib (no Dockerfiles) | ✓ SATISFIED | Jib plugin in all 13 `build.gradle.kts`; 0 Dockerfiles in repo; `jibBuildTar` passes |
| DEV-02 | 11-04 | GitHub Actions CI pipeline (`build`, `test`) on push/PR | ✓ SATISFIED | `.github/workflows/ci.yml` with `build`, `infra-tests`, `frontend` jobs |
| DEV-03 | 11-04 | Release pipeline on `v*` tags publishes 13 Jib images to container registry | ✓ SATISFIED | `.github/workflows/release.yml` matrix over 13 services, GHCR push |
| DEV-04 | 11-06 | Jenkins comparison documented | ✓ SATISFIED | `docs/devops-pipeline-comparison.md` with Jenkinsfile sketch and stage mapping |
| DEV-05 | 11-05 | Full deployment via `docker compose --profile full up`; public tunnel; external curl returns 200 | ⚠️ PARTIAL | Compose and script are ready; live tunnel proof requires human verification (Plan 05 Task 3 was explicitly a human-verify checkpoint) |
| DEV-06 | 11-04 | Slack webhook fires CI build notifications on success/failure | ✓ SATISFIED | `ci.yml` and `release.yml` both have `notify` jobs with Slack curl POST |
| DEV-08 | 11-04, 11-05 | Tunnel access token and registry credentials live as env vars / GitHub Actions secrets — never committed | ✓ SATISFIED | `.env.example` has blank placeholders; workflows use `${{ secrets.XXX }}`; no literal secrets in YAML; gitleaks workflow active |
| DEV-09 | 11-05 | README.md covers local-run instructions, env-var matrix, demo card numbers, tunnel hostname pointer, 30-second compose rehearsal | ✓ SATISFIED | `README.md` has all sections; `## 30-second demo path` with copy-paste commands |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| `.github/workflows/ci.yml` | 75 | `if: ${{ secrets.SLACK_WEBHOOK_URL != '' }}` — GitHub Actions does not allow `secrets` context in step `if` conditionals | ⚠️ Warning | Step may run (and curl fail) when secret is absent; harmless when secret is present |
| `frontend/src/hooks/useChatAssistant.ts` | 31 | `messages` state lost on refresh — no rehydration from localStorage or backend history endpoint | ⚠️ Warning | Chat panel appears empty after browser refresh; backend conversation continues correctly |

### Human Verification Required

#### 1. Live Public Tunnel Proof (DEV-05)

**Test:** Start the full stack with `docker compose --profile full up`, start a Cloudflare Tunnel or ngrok session, then run:
```bash
export DEMO_TUNNEL_HOSTNAME=<your-hostname>
scripts/verify-demo-tunnel.sh
```
**Expected:** Script prints `DEV-05 tunnel proof passed: https://$HOSTNAME/api/v1/products returned 200` and exits 0.
**Why human:** Requires a live external tunnel hostname and running backend services.

#### 2. Chat Cart Badge Update Timing

**Test:** Log in to the storefront, open the chat bubble, trigger a product search, click **Sepete Ekle** on a compact product card.
**Expected:** Header cart counter increments within 1 second; toast `Ürün sepete eklendi.` appears.
**Why human:** Timing claim depends on network latency, React Query cache invalidation, and running services.

#### 3. Chat Transcript Persistence Across Refresh

**Test:** Open chat, send a message (e.g., "MacBook ara"), wait for assistant response, refresh the browser, reopen the chat bubble.
**Expected:** Previous user and assistant messages are still visible in the transcript.
**Why human:** Current implementation only persists `conversationId`; whether backend history is fetched or frontend stores messages in `localStorage` requires runtime verification. **If messages are lost on refresh, this is a UX gap that should be fixed before the demo.**

### Gaps Summary

**1. Chat transcript not rehydrated on browser refresh** (Partial truth)
- The `conversationId` is correctly persisted in `localStorage` and reused, ensuring the backend continues the same conversation thread.
- However, the frontend `messages` array is initialized to `[]` on every mount. There is no `useEffect` to load previous messages from the backend or from `localStorage`.
- **Impact:** After a browser refresh, the chat panel opens empty. The user can continue chatting (backend has full history), but previous messages are not visible.
- **Fix:** Either (a) add a backend `GET /chat/history?conversationId=...` endpoint and fetch it in `useChatAssistant` on mount, or (b) persist `messages` in `localStorage` alongside `conversationId`.

**2. Live tunnel and timing claims require human verification**
- `scripts/verify-demo-tunnel.sh` is executable and correct, but a real Cloudflare/ngrok tunnel must be running to prove DEV-05.
- The "within 1 second" cart badge update claim requires a running stack and human observation.

---

_Verified: 2026-05-02T17:30:00Z_
_Verifier: OpenCode (gsd-verifier)_
