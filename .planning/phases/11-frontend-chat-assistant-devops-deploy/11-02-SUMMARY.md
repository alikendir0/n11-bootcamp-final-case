---
phase: 11-frontend-chat-assistant-devops-deploy
plan: 02
subsystem: ui
 tags: [react, tailwind, chat, sse, accessibility]

# Dependency graph
requires:
  - phase: 11-01
    provides: useChatAssistant hook, typed SSE events, chatApi client
provides:
  - Floating chat bubble shell mounted in Layout.tsx
  - Responsive ChatDrawer (420px desktop / full-screen mobile)
  - Accessible ChatTranscript with live-region semantics
  - ToolStatusChip component for streaming tool-use indicators
  - ChatProductCard with Ürünü Gör / Sepete Ekle actions
  - ChatHandoffCard for cart, order, and payment CTAs
affects:
  - frontend-storefront
  - ai-service

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Mock hook pattern for ChatAssistant unit tests (vi.mock + QueryClientProvider + MemoryRouter)"
    - "Type guards for runtime tool_result.data narrowing with exactOptionalPropertyTypes"

key-files:
  created:
    - frontend/src/components/chat/ChatAssistant.tsx
    - frontend/src/components/chat/ChatDrawer.tsx
    - frontend/src/components/chat/ChatTranscript.tsx
    - frontend/src/components/chat/ToolStatusChip.tsx
    - frontend/src/components/chat/ChatProductCard.tsx
    - frontend/src/components/chat/ChatHandoffCard.tsx
    - frontend/src/components/chat/ChatAssistant.test.tsx
  modified:
    - frontend/src/components/layout/Layout.tsx

key-decisions:
  - "ChatAssistant uses useChatAssistant hook and passes state to ChatDrawer — keeps shell and business logic separated"
  - "Type guards (isProductsData, isProductData, etc.) isolate runtime data validation from React rendering"
  - "ChatProductCard uses useMutation + addToCart directly, mirroring ProductDetailPage cart mutation pattern"

patterns-established:
  - "Chat component test wrapper: QueryClientProvider + MemoryRouter to satisfy useMutation and Link contexts"
  - "exactOptionalPropertyTypes-safe prop interfaces: optional props explicitly typed as T | undefined"

requirements-completed: [FE-12]

# Metrics
duration: 19min
completed: 2026-05-02
---

# Phase 11 Plan 02: Chat UI Shell Summary

**Floating Turkish chat assistant with responsive drawer, accessible transcript, tool chips, and compact product/handoff cards integrated into the storefront layout.**

## Performance

- **Duration:** 19 min
- **Started:** 2026-05-02T15:48:51Z
- **Completed:** 2026-05-02T16:08:14Z
- **Tasks:** 3
- **Files modified:** 7 created + 1 modified = 8

## Accomplishments

- Globally mounted floating chat bubble with Turkish aria-labels and responsive drawer (420px desktop / full-screen mobile)
- Accessible transcript with `role="log"`, `aria-live="polite"`, `aria-relevant="additions text"`, empty state, streaming indicator, and retry card
- Tool status chips with pending/success/failure states using Turkish labels from `toolChipCopy`
- Composer with placeholder, send button, and disabled streaming state
- Compact product cards rendered from real `tool_result.data` payloads with `Ürünü Gör` and `Sepete Ekle` actions
- Cart/order/payment handoff cards with explicit CTAs (`Sepete Git`, `Ödemeye Git`, `Ödemeye Devam Et`)
- 13 passing unit tests covering shell, transcript, composer, and structured card rendering

## task Commits

Each task was committed atomically:

1. **task 1: mount floating assistant shell in layout** - `3cbc1c2` (test), `b90fa15` (feat)
2. **task 2: render accessible transcript and tool chips** - `dcfd1ad` (feat)
3. **task 3: render compact product and handoff cards** - `77b831b` (feat)

**TypeScript fixes:** `c54c300` (fix)

**Plan metadata:** pending

## Files Created/Modified

- `frontend/src/components/chat/ChatAssistant.tsx` - Floating trigger + drawer shell state
- `frontend/src/components/chat/ChatDrawer.tsx` - Responsive drawer/sheet UI with composer
- `frontend/src/components/chat/ChatTranscript.tsx` - Accessible transcript renderer with tool chips and structured cards
- `frontend/src/components/chat/ToolStatusChip.tsx` - One-line tool status chip with spinner/dots
- `frontend/src/components/chat/ChatProductCard.tsx` - Compact product card with cart mutation
- `frontend/src/components/chat/ChatHandoffCard.tsx` - Cart/order/payment handoff CTAs
- `frontend/src/components/chat/ChatAssistant.test.tsx` - 13 Vitest cases for all chat states
- `frontend/src/components/layout/Layout.tsx` - Mounts `<ChatAssistant />` after `<AuthEventBridge />`

## Decisions Made

- ChatAssistant uses `useChatAssistant` hook directly and passes state to ChatDrawer — keeps shell and business logic separated
- Type guards (`isProductsData`, `isProductData`, etc.) isolate runtime `tool_result.data` validation from React rendering
- ChatProductCard uses `useMutation` + `addToCart` directly, mirroring the existing `ProductDetailPage` cart mutation pattern
- Test wrapper includes both `QueryClientProvider` and `MemoryRouter` to satisfy `useMutation` and `Link` contexts

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] TypeScript strict-mode compilation failures in ChatTranscript and test file**
- **Found during:** task 3 (build verification)
- **Issue:** `exactOptionalPropertyTypes: true` rejected passing `undefined` to optional props; `tool_result.data` access produced `unknown` types causing ReactNode and ChatProductCardData mismatches
- **Fix:** Added `| undefined` to optional prop interfaces (`ToolStatusChipProps`, `ChatHandoffCardProps`); introduced runtime type guards (`isProductsData`, `isProductData`, `isCartData`, `isOrderData`, `isPaymentData`) to narrow `unknown` before rendering
- **Files modified:** `frontend/src/components/chat/ChatTranscript.tsx`, `frontend/src/components/chat/ToolStatusChip.tsx`, `frontend/src/components/chat/ChatHandoffCard.tsx`, `frontend/src/components/chat/ChatAssistant.test.tsx`
- **Verification:** `npm run build` exits 0; `npx vitest run` passes 13/13
- **Committed in:** `c54c300`

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Minor — strict-mode compliance fixes with no behavior change. No scope creep.

## Issues Encountered

- ChatAssistant tests for cards initially failed because `ChatProductCard` uses `useMutation` (requires `QueryClientProvider`) and `ChatHandoffCard` uses `Link` (requires `MemoryRouter`). Resolved by adding a shared test wrapper with both providers.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Chat UI shell is complete and integrated into Layout.tsx
- Ready for 11-03 (Playwright chat smoke tests, retry/auth/cart handoff tests, no-direct-ai-service invariant gates)
- All chat components compile and tests pass; build is green

## Self-Check: PASSED

- All 8 created/modified files verified on disk
- All 6 commits (3 task commits + 1 fix + 1 test + 1 metadata) verified in git log
- Build (`npm run build`) exits 0 with no TypeScript errors
- Tests (`npx vitest run src/components/chat/ChatAssistant.test.tsx`) pass 13/13

---
*Phase: 11-frontend-chat-assistant-devops-deploy*
*Completed: 2026-05-02*
