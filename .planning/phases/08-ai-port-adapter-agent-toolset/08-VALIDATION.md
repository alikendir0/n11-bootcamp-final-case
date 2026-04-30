---
phase: 8
slug: ai-port-adapter-agent-toolset
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-01
---

# Phase 8 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Detailed Behavior → Test mapping lives in `08-RESEARCH.md` § "Validation Architecture".

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test (bundled with Boot 3.5.14); Testcontainers for Postgres+pgvector integration |
| **Config file** | None — standard Spring Boot test conventions per service module |
| **Quick run command** | `./gradlew :ai-port:test :agent-toolset:test` (no containers, ~10s) |
| **Full suite command** | `./gradlew :ai-port:test :agent-toolset:test :ai-service:test :search-service:test` |
| **Estimated runtime** | ~120 seconds (Testcontainers warm-up + Postgres init) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :ai-port:test :agent-toolset:test` (fast, no containers)
- **After every plan wave:** Run `./gradlew :ai-service:test :search-service:test` (Testcontainers Postgres)
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds (quick) / 120 seconds (wave)

---

## Per-Task Verification Map

> The planner expands this table per plan/task. Source-of-truth requirements → test mapping is `08-RESEARCH.md` § Validation Architecture (Phase Requirements → Test Map).

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| {N}-01-01 | 01 | 1 | AI-01 | — | `ai-port` has zero `com.google.genai` deps | Gradle dep guard | `./gradlew :ai-port:dependencies \| grep -c "com.google.genai"` (expect 0) | ❌ W0 | ⬜ pending |
| {N}-XX-XX | XX | X | AI-04 | — | `ai.provider=echo` activates EchoChatProvider | Integration | `./gradlew :ai-service:test --tests EchoProviderActivationTest` | ❌ W0 | ⬜ pending |
| {N}-XX-XX | XX | X | AI-05 | — | All 10 agent tools registered | Unit | `./gradlew :agent-toolset:test --tests AgentToolRegistryTest` | ❌ W0 | ⬜ pending |
| {N}-XX-XX | XX | X | AI-07 | — | Hallucinated productId rejected | Unit | `./gradlew :ai-service:test --tests ToolDispatcherIdProvenanceTest` | ❌ W0 | ⬜ pending |
| {N}-XX-XX | XX | X | AI-08 | — | SSE typed events emitted | Integration | `./gradlew :ai-service:test --tests ChatStreamSseTest` | ❌ W0 | ⬜ pending |
| {N}-XX-XX | XX | X | AI-09 | — | Authed conversation persisted | Integration (Testcontainers) | `./gradlew :ai-service:test --tests ConversationPersistenceTest` | ❌ W0 | ⬜ pending |
| {N}-XX-XX | XX | X | AI-10 | — | Turkish system prompt enforced | Integration (Echo) | `./gradlew :ai-service:test --tests TurkishSystemPromptTest` | ❌ W0 | ⬜ pending |
| {N}-XX-XX | XX | X | QUAL-08 | — | search-service Spring context loads | Smoke | `./gradlew :search-service:test --tests SearchServiceContextTest` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Test scaffolds that must exist before the first executor task can land:

- [ ] `ai-port/src/test/java/.../AiPortContractTest.java` — verify zero Gemini deps + interface signatures
- [ ] `ai-service/src/test/java/.../EchoChatProviderTest.java` — unit test EchoChatProvider returns echoed text
- [ ] `ai-service/src/test/java/.../EchoProviderActivationTest.java` — Spring profile `ai.provider=echo` activates EchoChatProvider bean
- [ ] `agent-toolset/src/test/java/.../AgentToolRegistryTest.java` — `assertThat(registry.all()).hasSize(10)`
- [ ] `ai-service/src/test/java/.../ToolDispatcherIdProvenanceTest.java` — Pitfall #10 hallucinated-id rejection
- [ ] `ai-service/src/test/java/.../ChatStreamSseTest.java` — assert `event:delta`, `event:tool_call`, `event:tool_result`, `event:done` on the wire
- [ ] `ai-service/src/test/java/.../ConversationPersistenceTest.java` — Testcontainers; authed POST persists row
- [ ] `ai-service/src/test/java/.../TurkishSystemPromptTest.java` — 5-turn mixed-language assertion using EchoChatProvider scaffold
- [ ] `search-service/src/test/java/.../SearchServiceContextTest.java` — `@SpringBootTest` loads + `EmbeddingProvider` bean present
- [ ] `infra-tests` extensions — add ai-service + search-service to multi-service classpath with bean disambiguation per Plan 05-04 lesson
- [ ] `ai-service/src/test/java/.../GeminiSmokeTest.java` (skip-if-absent on `GEMINI_API_KEY`) — single real-Gemini round-trip

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `EchoChatProvider` swap demo (the SOLID grading moment) | AI-04 | Demonstration intent — graders flip the config to see the port-adapter substitution | (1) Set `ai.provider=echo` in `config-server/src/main/resources/config/ai-service.yml`. (2) `./gradlew :config-server:jibDockerBuild && docker compose up -d config-server ai-service`. (3) `curl -N http://localhost:8080/api/v1/chat/stream -H "X-User-Id: demo" -d '{"message":"merhaba"}'`. (4) Observe assistant echoes "merhaba". (5) Flip back to `gemini`, restart, confirm normal behavior returns. |
| Cross-service correlationId trace | ARCH-08 / QUAL-08 | Requires reading 13 service log files for one chat-driven order flow | `grep -r "correlationId=<uuid-from-chat>" infra/logs/` traces chat → cart → order → inventory → payment → notification. |
| Demo-budget aware Gemini interaction | Pitfall #9 | Free-tier 10 RPM cap — demo behavior, not test | Avoid running embedding pre-warm before chat demo; watch for `429 RESOURCE_EXHAUSTED`; fallback model `gemini-2.5-flash` shares the same quota bucket. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s (quick) / 120s (wave)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
