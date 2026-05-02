---
phase: 11-frontend-chat-assistant-devops-deploy
plan: 01
subsystem: ui
tags: [react, sse, typescript, vitest, tanstack-query, java, mockito]

requires:
  - phase: 08-ai-port-adapter-agent-toolset
    provides: ai-service ChatService with delta/tool_call/tool_result/done/error SSE events
  - phase: 10-frontend-storefront
    provides: Vite React SPA, apiClient auth posture, useCart hook with cartQueryKey

provides:
  - Typed ChatStreamEvent discriminated union (delta | tool_call | tool_result | done | error)
  - Incremental SSE frame parser with Turkish tool-chip mapping
  - fetch-based POST SSE client following gateway auth posture (Bearer token, 401 dispatch)
  - Persistent useChatAssistant hook with conversationId localStorage, cart invalidation, retry
  - Structured tool_result payloads from ai-service (toolName, resultType, data)

affects:
  - 11-02-PLAN.md (UI shell consumes useChatAssistant and chatEvents)
  - 11-03-PLAN.md (Playwright chat smoke tests)

tech-stack:
  added: []
  patterns:
    - "TDD RED/GREEN for all three tasks"
    - "Conditional object-spread pattern for exactOptionalPropertyTypes compliance"
    - "Backend tool_result normalizes data into neutral JSON by tool name"

key-files:
  created:
    - frontend/src/lib/chatEvents.ts
    - frontend/src/lib/chatEvents.test.ts
    - frontend/src/api/chatApi.ts
    - frontend/src/api/chatApi.test.ts
    - frontend/src/hooks/useChatAssistant.ts
    - frontend/src/hooks/useChatAssistant.test.tsx
    - ai-service/src/test/java/com/n11/ai/domain/chat/ChatServiceTest.java
  modified:
    - frontend/src/lib/types.ts
    - ai-service/src/main/java/com/n11/ai/domain/chat/ChatService.java

key-decisions:
  - "ChatToolResultData uses Record<string,unknown> fallback to match backend payload flexibility"
  - "ctaUrl added to ChatTranscriptItem so UI shell can render login CTA without extra prop drilling"
  - "uuid package avoided; crypto.randomUUID() used for conversationId"

patterns-established:
  - "Frontend test execution must run from frontend/ directory to use local vitest + jsdom config"
  - "exactOptionalPropertyTypes requires conditional spreads ({...(x ? {a:x} : {})}) instead of optional assignment"

requirements-completed: [FE-12]

duration: 27min
completed: 2026-05-02
---

# Phase 11 Plan 01: Chat Streaming Foundation Summary

**Browser-side typed SSE parser, gateway-authenticated POST stream client, and persistent chat hook with confirmed cart-query invalidation**

## Performance

- **Duration:** 27 min
- **Started:** 2026-05-02T15:15:57Z
- **Completed:** 2026-05-02T15:43:00Z
- **Tasks:** 3
- **Files modified:** 8

## Accomplishments

- Backend `ChatService.handleStream` now emits structured `tool_result` events with `toolName`, `resultType` (`products` | `product` | `cart` | `order` | `payment` | `generic`), and `data` derived from the actual tool result JSON
- Frontend `chatEvents.ts` provides incremental `parseSseFrames`, Turkish `toolChipCopy` for all 10 canonical tools, and `isCartMutationTool` guard
- `chatApi.ts` implements `fetch`-based POST SSE streaming with Bearer token injection, 401 auth event dispatch, and AbortSignal support
- `useChatAssistant` hook manages persistent `conversationId` in `localStorage`, accumulates streaming transcript, invalidates `cartQueryKey` only after confirmed `tool_result.ok === true`, and exposes `retryLastMessage` / `clearLocalTranscript`
- 27 frontend Vitest tests + 2 backend Mockito tests all green; frontend `tsc -b && vite build` passes

## Task Commits

Each task was committed atomically:

1. **task 1: define typed chat events and parser helpers** - `0e05da3` (feat)
2. **task 2: implement POST SSE chat API client** - `011b6db` (feat)
3. **task 3: create persistent chat assistant hook** - `9542c61` (feat)

**TypeScript fixes** - `0d39bb6` (fix)

## Files Created/Modified

- `frontend/src/lib/types.ts` - ChatStreamEvent union, ChatTranscriptItem, compact card DTOs
- `frontend/src/lib/chatEvents.ts` - SSE parser, tool-chip mapping, cart-mutation guard
- `frontend/src/lib/chatEvents.test.ts` - 12 Vitest tests for parser and helpers
- `frontend/src/api/chatApi.ts` - `streamChat` with gateway auth and chunked SSE consumption
- `frontend/src/api/chatApi.test.ts` - 6 Vitest tests for headers, auth, 401, chunks, abort
- `frontend/src/hooks/useChatAssistant.ts` - Persistent transcript hook with cart invalidation
- `frontend/src/hooks/useChatAssistant.test.tsx` - 9 renderHook tests for streaming, auth, retry
- `ai-service/src/main/java/com/n11/ai/domain/chat/ChatService.java` - Structured tool_result emission
- `ai-service/src/test/java/com/n11/ai/domain/chat/ChatServiceTest.java` - Mockito test for products payload

## Decisions Made

- `ChatToolResultData` uses `Record<string, unknown>` as the fallback branch so backend payload shapes don't require a strict discriminated union on the frontend parser
- `ctaUrl` added to `ChatTranscriptItem` as an optional field so the auth-required login CTA can be rendered by the UI shell without additional prop drilling
- `crypto.randomUUID()` used instead of adding a `uuid` npm dependency

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] TypeScript exactOptionalPropertyTypes build failures**
- **Found during:** task 3 (hook implementation and build verification)
- **Issue:** Initial object literals like `{ ...assistant, text: assistant.text }` failed because `text` is `string | undefined` but `exactOptionalPropertyTypes` forbids explicit `undefined` on optional properties
- **Fix:** Restructured all object literals to use conditional spreads (`...(assistant.text !== undefined ? { text: assistant.text } : {})`), adjusted `ChatToolResultData` union to remove an overly strict `resultType: 'generic'` branch, and cast through `unknown` for dynamic property assignment in `chatEvents.ts`
- **Files modified:** `frontend/src/hooks/useChatAssistant.ts`, `frontend/src/hooks/useChatAssistant.test.tsx`, `frontend/src/api/chatApi.ts`, `frontend/src/api/chatApi.test.ts`, `frontend/src/lib/chatEvents.ts`, `frontend/src/lib/types.ts`
- **Verification:** `cd frontend && npm run build` passes; `tsc -b` emits zero errors
- **Committed in:** `0d39bb6`

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Build compliance fix only; no functional or scope change.

## Issues Encountered

- Running `npx vitest` from the repo root installed vitest 4.x globally into a temporary `node_modules/` and used the wrong test environment (no jsdom, no `window`/`localStorage`). Resolution: always run `npx vitest` from `frontend/` directory so the local vitest 2.x + jsdom config is used.

## Known Stubs

| File | Line | Description |
|------|------|-------------|
| `frontend/src/hooks/useChatAssistant.ts` | ~59 | `ctaUrl` is populated but no UI component renders it yet — Plan 02 owns the chat drawer/transcript shell |

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: xss-surface | `frontend/src/lib/chatEvents.ts` | SSE parser passes `payload.data` through as `unknown`; UI shell must sanitize before rendering into DOM (Plan 02 responsibility) |

## Self-Check: PASSED

- [x] All created files exist on disk
- [x] All commits exist in git history (`0e05da3`, `011b6db`, `9542c61`, `0d39bb6`)
- [x] `./gradlew :ai-service:test --tests "com.n11.ai.domain.chat.ChatServiceTest"` exits 0
- [x] `cd frontend && npx vitest run src/lib/chatEvents.test.ts src/api/chatApi.test.ts src/hooks/useChatAssistant.test.tsx` exits 0
- [x] `cd frontend && npm run build` exits 0

## Next Phase Readiness

- Plan 02 (UI shell: floating bubble, drawer, transcript, tool chips, compact cards) can import `useChatAssistant()` and `chatEvents.ts` directly
- Plan 03 (Playwright chat smoke) can exercise `streamChat` through the real gateway route
- No blockers

---
*Phase: 11-frontend-chat-assistant-devops-deploy*
*Completed: 2026-05-02*
