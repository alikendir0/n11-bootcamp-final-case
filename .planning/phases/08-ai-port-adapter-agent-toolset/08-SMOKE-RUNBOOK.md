# Phase 8 Smoke Runbook — AI Port + Adapter + Agent Toolset

> **Audience:** Graders and demo operators.
> This document is the single reference for running the Phase 8 end-to-end smoke and the SOLID swap demo.
> It doubles as the seed script for the Phase 11 README demo section.
> Estimated operator time: 10 minutes for sections 1–9; 5 minutes for the SOLID swap moment (section 5) alone.

---

## Section 1 — Prerequisites

- **Docker + docker-compose** installed and Docker daemon running
- **Phases 1–7 services** were previously deployed; their docker images exist (`docker images | grep n11`)
- **`.env` file** present in the project root with the following variables set (from earlier phases):
  - `AI_DB_PASSWORD`, `SEARCH_DB_PASSWORD`
  - `RABBITMQ_DEFAULT_USER`, `RABBITMQ_DEFAULT_PASS`
  - `JWT_PRIVATE_KEY` (RS256 key for identity-service)
  - `IYZICO_API_KEY`, `IYZICO_SECRET_KEY` (Iyzico sandbox credentials)
- **`GEMINI_API_KEY`** set in `.env` for the live Gemini demo path (Section 5 step b-c).
  If the key is absent or quota is exhausted, use `ai.provider=echo` for the structural demo (see Pitfall #9 note in Section 10).

---

## Section 2 — Build + Boot

```bash
# Build the images for Phase 8 services and config-server (which holds ai.provider config)
./gradlew :ai-service:jibDockerBuild :search-service:jibDockerBuild :config-server:jibDockerBuild

# Bring the full stack up
docker compose up -d

# Wait for ai-service + search-service health (≤90 seconds)
for s in ai-service search-service; do
  until [ "$(docker inspect -f '{{.State.Health.Status}}' n11-$s 2>/dev/null)" = "healthy" ]; do
    sleep 2
  done
  echo "$s healthy"
done
```

Expected: both services report `healthy` within 90 seconds.

> Note: if Docker Desktop is listening on a non-default port (e.g., host 8088 instead of 8080 due to port collision), all `curl` commands below substitute `8088` for `8080`. The gateway internal port is always 8080; the host mapping is in `docker-compose.yml`.

---

## Section 3 — Quick sanity: are all 13 services up?

```bash
docker compose ps --format "table {{.Name}}\t{{.Status}}" | grep -v "Up" | grep -v NAME
# Expected: zero rows (all services Up)

# Verify gateway routes include SSE chat route
curl -fsS http://localhost:8080/actuator/gateway/routes | jq '.[].id' | grep ai
# Expected: "ai-service-chat-stream" and "ai-service-chat" visible
```

---

## SC-1 Verification — AI-01 / Pitfall #7: zero Gemini deps in ai-port

**Requirement:** SC-1 — ai-port has zero `com.google.genai` artifacts.

```bash
# Dependency check (must return 0)
./gradlew :ai-port:dependencies | grep -v '^#' | grep -c "com.google.genai"
# Expected output: 0

# Source scan: no Gemini imports outside infrastructure/llm/
grep -rln "^import com.google.genai" ai-service/src/main/java/com/n11/ai/ \
  | grep -v "infrastructure/llm/"
# Expected output: (empty — zero matches)

# D-09: search-service has zero Gemini deps
grep -rln "com.google.genai" search-service/
# Expected output: (empty — zero matches)
```

If any of these return non-zero, the Pitfall #7 sealed boundary has been violated and QUAL-08 is at risk.

---

## SC-2 Verification — The SOLID Swap Demo (AI-04)

**This is the grading moment.** The swap proves that `ChatProvider` and `EmbeddingProvider` are genuine ports — not Gemini wrappers with a thin facade.

### Step a — Confirm current provider is `gemini`

```bash
grep "provider:" config-server/src/main/resources/config/ai-service.yml
# Expected: "  provider: gemini"
```

### Step b — Send a chat request with `ai.provider=gemini` (baseline)

```bash
curl -fsS -X POST http://localhost:8080/api/v1/chat \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 00000000-0000-0000-0000-000000000001' \
  -d '{"conversationId":"00000000-0000-0000-0000-000000000002","message":"merhaba, en ucuz laptopu göster"}' | jq .
# Expected: a Turkish Gemini response describing a product or asking to search
```

### Step c — Capture the resolved Gemini model identifier (Pitfall #1 audit trail)

```bash
docker logs n11-ai-service 2>&1 | grep -i "resolved\|model\|gemini" | head -5
# Expected: a line like:
#   ai-service: resolved chat model = gemini-3-flash-preview
# This confirms the ApplicationReadyEvent model probe completed without falling back.
```

Record this line — it is the Pitfall #1 audit trail for the grading report.

### Step d — Swap to `ai.provider=echo`

```bash
sed -i 's/provider: gemini/provider: echo/' config-server/src/main/resources/config/ai-service.yml

./gradlew :config-server:jibDockerBuild
docker compose up -d --force-recreate config-server ai-service

# Wait for ai-service to return healthy
until [ "$(docker inspect -f '{{.State.Health.Status}}' n11-ai-service 2>/dev/null)" = "healthy" ]; do sleep 2; done
echo "ai-service healthy with echo provider"
```

### Step e — Confirm echo provider is active

```bash
curl -fsS -X POST http://localhost:8080/api/v1/chat \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 00000000-0000-0000-0000-000000000001' \
  -d '{"conversationId":"00000000-0000-0000-0000-000000000003","message":"merhaba"}' | jq .
# Expected: {"conversationId":"...","text":"[ECHO] merhaba"}
# The [ECHO] prefix is the EchoChatProvider signal.
```

### Step f — Flip back to Gemini

```bash
sed -i 's/provider: echo/provider: gemini/' config-server/src/main/resources/config/ai-service.yml

./gradlew :config-server:jibDockerBuild
docker compose up -d --force-recreate config-server ai-service

until [ "$(docker inspect -f '{{.State.Health.Status}}' n11-ai-service 2>/dev/null)" = "healthy" ]; do sleep 2; done
echo "ai-service restored to gemini provider"
```

> **Operator note:** Steps d–f are the SOLID grading moment. The same `ChatService.chat()` call site drives both providers — only the `ai.provider` config knob changes between them. The rest of ai-service (ToolDispatcher, ConversationStateService, SSE streaming) does not know which provider is active. This is the hexagonal architecture demonstration QUAL-08 grades.

---

## SC-3 Verification — 10 Tools Registered + ID Validation (AI-05 / AI-07)

```bash
# All agent-toolset unit tests
./gradlew :agent-toolset:test --tests AgentToolRegistryTest --tests ToolSchemaContractTest

# ToolDispatcher ID provenance test
./gradlew :ai-service:test --tests ToolDispatcherIdProvenanceTest

# Expected: BUILD SUCCESSFUL — all tests green
# AgentToolRegistryTest asserts registry.all().size() == 10
# ToolDispatcherIdProvenanceTest asserts hallucinated IDs return UNKNOWN_ID error
```

---

## SC-4 Verification — Turkish SSE Chat with Persistence (AI-06 / AI-08 / AI-09 / AI-10)

### SSE wire-format check

```bash
# TypedSSE: observe event names on the wire
curl -N -X POST http://localhost:8080/api/v1/chat/stream \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 00000000-0000-0000-0000-000000000001' \
  -H 'X-Correlation-Id: smoke-ai-001' \
  -d '{"conversationId":"00000000-0000-0000-0000-000000000004","message":"Elektronik kategorisindeki en ucuz 3 ürünü göster"}' | head -20
# Expected lines starting with:
#   event:delta
#   event:tool_call
#   event:tool_result
#   event:done
```

### Persistence check

```bash
# Confirm conversation and messages are written to Postgres
docker exec n11-postgres psql -U postgres -d n11 -c \
  "SELECT id, user_id, created_at FROM ai.ai_conversations ORDER BY created_at DESC LIMIT 3;"

docker exec n11-postgres psql -U postgres -d n11 -c \
  "SELECT conversation_id, role, sequence_no FROM ai.messages ORDER BY created_at DESC LIMIT 10;"
```

### Persistence survives restart check

```bash
# Restart ai-service and verify the conversation is still retrievable (D-02 forever-for-authed)
docker compose restart ai-service
sleep 25
until [ "$(docker inspect -f '{{.State.Health.Status}}' n11-ai-service 2>/dev/null)" = "healthy" ]; do sleep 2; done

curl -fsS http://localhost:8080/api/v1/chat/conversations/00000000-0000-0000-0000-000000000004 \
  -H 'X-User-Id: 00000000-0000-0000-0000-000000000001' | jq .
# Expected: JSON array of TurnDto records — all turns present after restart
```

---

## SC-5 Verification — Chat-Cart Bridge (AI-15 / D-05)

This verifies that items the chat assistant adds to the cart are visible via the cart-service REST endpoint.

```bash
# Step 1: Use a real authed user (substitute a valid userId from Phase 3 identity-service)
USER_ID="11111111-1111-1111-1111-111111111111"

# Step 2: Ask the assistant to add a product via chat
curl -fsS -X POST http://localhost:8080/api/v1/chat \
  -H 'Content-Type: application/json' \
  -H "X-User-Id: $USER_ID" \
  -d "{\"conversationId\":\"00000000-0000-0000-0000-000000000010\",\"message\":\"En ucuz laptopu sepete ekle (1 adet)\"}" | jq .
# Expected: Turkish response confirming the product was added
# (ChatService calls add_to_cart tool → cart-service POST /cart/items)

# Step 3: Read the same user's cart directly via REST
curl -fsS http://localhost:8080/api/v1/cart \
  -H "X-User-Id: $USER_ID" | jq .
# Expected: cart JSON containing the product the chat assistant added
# This proves AI-15: chat tool call and REST cart share the same data source
```

---

## Section 9 — Cross-Service Correlation-ID Trace (Observability)

```bash
# Send a chat request with a known X-Correlation-Id
curl -fsS -X POST http://localhost:8080/api/v1/chat \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 00000000-0000-0000-0000-000000000001' \
  -H 'X-Correlation-Id: smoke-ai-001' \
  -d '{"conversationId":"00000000-0000-0000-0000-000000000005","message":"Laptop ara"}' | jq .

# Trace the correlation ID across service logs
docker compose logs --no-color ai-service cart-service product-service order-service payment-service notification-service \
  | grep '"correlationId":"smoke-ai-001"' | head -20
# Expected: log lines from multiple services share the same correlationId
# Minimum expected services: ai-service + product-service (via search_products tool call)
# Full chain (if add_to_cart + create_order is invoked): ai-service + cart-service + order-service + inventory-service + payment-service + notification-service
```

---

## Section 10 — Pitfall #9 Demo-Budget Notes

**Free-tier Gemini Flash = 10 RPM.** Each user turn in a multi-turn, tool-using conversation can produce 3–5 model calls (one per tool round-trip in the manual loop plus the final response turn).

**Practical guidance for the 5-minute demo:**

1. **Avoid embedding pre-warm before the chat demo.** The `GeminiEmbeddingAdapter` shares the same API key and quota bucket. If embedding is triggered (e.g., by another service call), it consumes RPM.
2. **Watch for `429 RESOURCE_EXHAUSTED` in ai-service logs:**
   ```bash
   docker logs n11-ai-service 2>&1 | grep "429\|RESOURCE_EXHAUSTED" | tail -5
   ```
3. **If 429 appears:** switch the demo to `ai.provider=echo` (Section 5, Step d). The echo path loses the live Gemini magic but keeps the full SSE streaming, tool dispatch, and cart-bridge flow visible — which is sufficient for the SOLID demonstration.
4. **Fallback model `gemini-2.5-flash`:** shares the same quota bucket — falling back does NOT help with rate limits. It only helps with model-identifier 404 (Pitfall #1). See `GeminiChatAdapter.verifyModel()` for the automatic fallback logic.
5. **Paid tier** (out of scope for bootcamp) eliminates this concern entirely. Budget 60 seconds of pause between demo runs if the free tier cap is hit.

---

## Section 11 — Roll-up: 5/5 ROADMAP Phase 8 Success Criteria

| SC | Requirement | Verifying Command | Status |
|----|-------------|-------------------|--------|
| SC-1 | ai-port zero Gemini deps (AI-01, Pitfall #7) | Section 4: `./gradlew :ai-port:dependencies` returns 0 genai count | run |
| SC-2 | `ai.provider=echo` swap works (AI-04) | Section 5: curl returns `[ECHO] <message>` with echo provider | run |
| SC-3 | 10 tools registered + ID validation (AI-05, AI-07) | Section 6: `AgentToolRegistryTest` + `ToolDispatcherIdProvenanceTest` green | run |
| SC-4 | SSE Turkish chat + persistence (AI-06, AI-08, AI-09, AI-10) | Section 7: SSE events + Postgres rows + survives restart | run |
| SC-5 | Chat cart visible in REST cart (AI-15) | Section 8: cart REST returns item added by chat tool | run |

Mark each row `PASS` after running the corresponding section.

---

## Section 12 — Human-Verify Checkpoint

**This section is why `autonomous: false` is set in the plan frontmatter.**

The operator MUST run at minimum:
- **Section 5 (SOLID swap demo)** end-to-end — observe `[ECHO]` response with echo provider, Gemini response with gemini provider.
- **Section 8 (AI-15 cart-bridge demo)** — confirm chat-added product appears in REST cart.
- **Section 7 (persistence-survives-restart)** — confirm conversation persists after `docker compose restart ai-service`.

**Required audit trail captures:**
1. The ai-service startup log line containing the resolved Gemini model identifier (Step c of Section 5). Looks like: `ai-service: resolved chat model = gemini-3-flash-preview`. This is the Pitfall #1 compliance record.
2. The curl response from the echo-provider chat request (Step e of Section 5) — must start with `[ECHO]`.
3. The cart REST response from Section 8 — must contain the product added by chat.

Capture these observations into `08-05-SUMMARY.md` before typing "approved."

---

## Sign-Off

**Author:** Plan 08-05
**Date:** 2026-05-01
**Phase 8 complete when:** All sections (1–12) pass and observations are recorded in `08-05-SUMMARY.md`.
