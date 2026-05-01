---
phase: 9
slug: mcp-server
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-01
---

# Phase 9 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Spring Boot Test) |
| **Config file** | Root `build.gradle.kts` — `tasks.withType<Test> { useJUnitPlatform() }` |
| **Quick run command** | `./gradlew :mcp-server:test :infra-tests:test --tests "*.McpServerToolsListEqualityTest"` |
| **Full suite command** | `./gradlew :mcp-server:test :identity-service:test :infra-tests:test` |
| **Estimated runtime** | ~90 seconds (Spring context boot dominates) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :mcp-server:test` (or scoped test for the module touched — `:identity-service:test` if identity-service changed)
- **After every plan wave:** Run `./gradlew :mcp-server:test :identity-service:test :infra-tests:test --tests "*.McpServer*"`
- **Before `/gsd-verify-work`:** `./gradlew :mcp-server:test :identity-service:test :infra-tests:test` must be green
- **Max feedback latency:** ~120 seconds (full Phase 9 suite)

---

## Per-Task Verification Map

> The planner fills this table from each PLAN.md task. Below is the seed mapping derived from RESEARCH.md §Validation Architecture; the planner must extend it to one row per task and link each row's "Test Type / Automated Command" to a real test file or grep check.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 9-XX-XX | XX | 0 | AI-11 | — | N/A | integration | `./gradlew :infra-tests:test --tests "*.McpServerToolsListEqualityTest"` | ❌ W0 | ⬜ pending |
| 9-XX-XX | XX | 0 | AI-12 | — | mcp-server boots with both transports active | integration | `./gradlew :mcp-server:test --tests "*McpServerContextTest*"` | ❌ W0 | ⬜ pending |
| 9-XX-XX | XX | 0 | AI-13 | — | API key → JWT exchange returns JWT bound to real user_id | unit + integration | `./gradlew :identity-service:test --tests "*AgentExchangeControllerTest*"` | ❌ W0 | ⬜ pending |
| 9-XX-XX | XX | — | AI-11 (SC-4) | — | Zero local tool definitions in mcp-server | static (grep) | `! grep -r 'class.*AgentTool\\|implements AgentTool' mcp-server/src/main/java/` | ✅ Static | ⬜ pending |
| 9-XX-XX | XX | — | AI-12 (SC-2) | — | MCP Inspector lists 10 tools via HTTP transport | manual / smoke | `npx @modelcontextprotocol/inspector http http://localhost:8090/mcp` | Manual | ⬜ pending |
| 9-XX-XX | XX | — | AI-13 (SC-3) | — | add_to_cart via MCP client persists into cart-service | manual / smoke | See `.planning/phases/09-mcp-server/RUNBOOK.md` Claude Desktop demo flow | Manual | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `mcp-server/src/test/java/com/n11/mcp/McpServerContextTest.java` — Spring context boots, ToolCallbackProvider yields 10 callbacks, transport properties resolve
- [ ] `mcp-server/src/test/java/com/n11/mcp/AgentToolMcpRegistrationTest.java` — Each ToolCallback exposes name/description/inputSchema equal to source `AgentTool` bean
- [ ] `identity-service/src/test/java/com/n11/identity/agent/AgentExchangeControllerTest.java` — Valid API key → 200 + JWT (sub == bound user_id); invalid → 401; revoked → 401; missing → 400
- [ ] `infra-tests/src/test/java/com/n11/infratests/mcp/McpServerToolsListEqualityTest.java` — JSON-RPC `tools/list` reply equals `ToolRegistry.all().stream().map(AgentTool::name).toSet()`; schemas match byte-for-byte after canonicalization
- [ ] `infra-tests/src/test/java/com/n11/infratests/mcp/McpServerTestConfig.java` — Plan 05-04 excludeFilters pattern, prevents foreign `@SpringBootApplication` expansion

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Claude Desktop demo flow (search → add → cart → order → payment → SC-3) | AI-12, AI-13 | End-to-end UI involves 3DS browser handoff (Iyzico test card) and Claude Desktop client | `RUNBOOK.md`: 1) `docker compose up -d`, 2) install Claude Desktop config snippet, 3) Turkish prompts to drive the demo, 4) verify order in `Siparişlerim` |
| MCP Inspector tool listing (HTTP) | AI-11, AI-12 | Inspector is an interactive Node CLI | `npx @modelcontextprotocol/inspector http http://localhost:8090/mcp`, copy JWT into Authorization, click `tools/list`, count must equal 10 |
| MCP Inspector tool listing (stdio) | AI-12 | Stdio requires subprocess launch | `npx @modelcontextprotocol/inspector stdio docker run -i --rm --network=host -e MCP_TRANSPORT=stdio -e MCP_API_KEY=$KEY mcp-server:<tag>` |
| Pitfall #15 demo-mode probe (transport log line) | AI-12 | Visual confirmation of which transport activated at boot | After `docker compose logs mcp-server`, expect `MCP transport: HTTP, capabilities: tools` (or `stdio` for Claude Desktop launch) |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (5 files listed above)
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
