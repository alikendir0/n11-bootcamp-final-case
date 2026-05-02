# Phase 11: Frontend Chat Assistant + DevOps Deploy - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-02
**Phase:** 11-frontend-chat-assistant-devops-deploy
**Areas discussed:** Chat panel shape, Streaming transcript, Cart handoff behavior, Release/deploy posture

---

## Chat Panel Shape

| Question | Selected | Options considered |
|----------|----------|--------------------|
| When the bottom-right chat bubble opens, what should the default panel feel like? | Right-side drawer | Right-side drawer; Centered modal; Compact popover; You decide |
| How large should the chat drawer be on desktop? | 420px drawer | 420px drawer; 520px demo panel; Responsive max 40vw; You decide |
| What should happen on mobile when the bubble opens? | Full-screen sheet | Full-screen sheet; Bottom sheet; Same drawer scaled; You decide |
| Should the chat panel stay open when the user navigates between pages? | Stay open | Stay open; Minimize on navigation; Close on navigation; You decide |

**Notes:** The user chose the recommended low-disruption shape that fits the existing `Layout.tsx` integration point.

---

## Streaming Transcript

| Question | Selected | Options considered |
|----------|----------|--------------------|
| How should tool-use events appear in the transcript while the assistant is working? | Inline status chips | Inline status chips; Expandable tool cards; Toast only; You decide |
| Should the final assistant message include product/cart summaries as structured cards or plain text? | Text plus compact cards | Text plus compact cards; Plain text only; Cards only for products; You decide |
| How should streaming errors appear if the SSE stream or Gemini call fails? | Inline retry state | Inline retry state; Toast plus stop; Silent fallback text; You decide |
| How should auth-required tool replies behave for guests? | Inline login CTA | Inline login CTA; Plain assistant message; Auto-open login; You decide |

**Notes:** The user chose transcript-visible proof of tool use without heavy debugger-style cards.

---

## Cart Handoff Behavior

| Question | Selected | Options considered |
|----------|----------|--------------------|
| After the assistant successfully adds or updates a cart item, what should happen first in the storefront UI? | Refresh badge + toast | Refresh badge + toast; Auto-open cart page; Ask next action; You decide |
| Should chat product cards include direct action buttons? | View + add buttons | View + add buttons; View only; No card actions; You decide |
| When the assistant creates an order or retrieves a payment link, what should the handoff be? | CTA to checkout/payment | CTA to checkout/payment; Auto-navigate; Open new tab for payment; You decide |
| Should chat-driven cart mutations use optimistic UI before the backend confirms? | Confirm then update | Confirm then update; Optimistic badge bump; Optimistic inside chat only; You decide |

**Notes:** The user chose backend-confirmed cart sync over optimistic UI to avoid stock/order mismatch during the demo.

---

## Release/Deploy Posture

| Question | Selected | Options considered |
|----------|----------|--------------------|
| Which image registry should the release-tag workflow publish Jib images to? | GHCR | GHCR; Docker Hub; Both; You decide |
| How should release image tags be named for the 13 services? | v tag + latest | v tag + latest; v tag only; sha + v tag; You decide |
| When should Slack build notifications fire? | CI and release | CI and release; Release only; Failures only; You decide |
| How should the README deploy flow be optimized for the interview demo? | One-command primary path | One-command primary path; Detailed checklist first; Two tracks; You decide |

**Notes:** The user chose the simplest GitHub-native release path and a demo-first README structure.

---

## OpenCode's Discretion

- Exact component names and file layout for chat UI.
- Exact Turkish microcopy for status chips and retry states.
- Technical reconciliation of current POST SSE backend with browser streaming implementation.

## Deferred Ideas

- Mid-stream cancellation.
- Conversational PDP summaries, compare-products, cart-aware suggestions, and recommendation UI.
- Docker Hub publishing.
