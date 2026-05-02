# Phase 9 — MCP Server Runbook

> Operator runbook for the agentic-commerce demo. Walks through the end-to-end
> Claude Desktop → n11 storefront flow that exercises every Phase 9 deliverable.

**Prerequisites:** Phase 1-8 complete; identity / product / cart / order /
payment services healthy; Iyzico sandbox keys in `.env`.

---

## 0. Build Jib images (one-shot per code change)

```bash
./gradlew \
  :config-server:jibDockerBuild \
  :identity-service:jibDockerBuild \
  :api-gateway:jibDockerBuild \
  :mcp-server:jibDockerBuild
```

> **Why:** Plan 09-05 edits `config-server/src/main/resources/config/api-gateway.yml`
> (the `/mcp/**` route) and `config-server/src/main/resources/config/mcp-server.yml`
> (transport config). config-server reads classpath:/config/, which is JAR-bound
> at image-build time (Plan 01-06 lesson). Without rebuild, the gateway and
> mcp-server boot with stale config.

---

## 1. Bring the stack up

```bash
docker compose up -d
docker compose ps                    # all services should be (healthy) within ~60s
```

Confirm mcp-server is up:

```bash
docker compose logs mcp-server | grep "MCP transport adapter registered"
# Expect: "MCP transport adapter registered 10 tools: [search_products, ...]"
```

---

## 2. Capture the demo MCP_API_KEY (FIRST RUN ONLY)

The first identity-service boot generates a fresh demo key and logs the plaintext
ONCE at WARN level (Plan 09-02 AgentSeedRunner / CLAUDE.md Rule #5 / D-05).

```bash
docker compose logs identity-service 2>&1 | grep -A 2 "Phase 9 demo MCP_API_KEY"
# Look for:
#   Phase 9 demo MCP_API_KEY (LOG ONCE — copy into .env, NEVER commit):
#   MCP_API_KEY=<32-byte URL-safe random string>
#   Bound to user_id=<UUID>, agent_label=demo-agent
```

Paste the captured value into your local `.env` (gitignored):

```bash
echo "MCP_API_KEY=<paste-the-value-here>" >> .env
```

Re-up mcp-server so it picks up the env:

```bash
docker compose up -d --force-recreate mcp-server
```

> **Re-issue the demo key (e.g. for fresh demo recording):** truncate the table
> via `docker compose exec postgres psql -U identity_user -d identity_db -c
> "TRUNCATE TABLE identity.agent_api_keys"`, then `docker compose restart
> identity-service`. AgentSeedRunner re-seeds and re-logs.

---

## 3. Smoke-test the auth bridge + tools/list (CLI)

```bash
# Exchange MCP_API_KEY for a JWT
TOKEN=$(curl -s -X POST http://localhost:9090/api/v1/identity/agents/exchange \
  -H "Content-Type: application/json" \
  -d "{\"apiKey\":\"$MCP_API_KEY\"}" | jq -r '.accessToken')
echo "JWT obtained, sub=$(echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq -r .sub)"

# Negative test: /mcp without JWT must return 401
curl -X POST http://localhost:9090/mcp -d '{"jsonrpc":"2.0","method":"tools/list","id":1}' -i \
  | grep -q "401 Unauthorized"

# Positive test: tools/list with valid JWT returns 10 tools
curl -X POST http://localhost:9090/mcp \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/json, text/event-stream" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}' \
  | tee /tmp/tools-list.json | jq '.result.tools | length'
# Expected: 10
```

Compare with ai-service truth source:

```bash
diff <(jq -r '.result.tools[].name' /tmp/tools-list.json | sort) \
     <(curl -s http://localhost:9090/api/v1/chat/tools | jq -r '.[]' | sort)
# Expected: empty diff
```

---

## 4. MCP Inspector — Streamable HTTP transport

```bash
# Inspector launches a local UI; paste $TOKEN into the Authorization header field.
npx @modelcontextprotocol/inspector http http://localhost:9090/mcp
```

In the Inspector UI:
1. Set custom header: `Authorization: Bearer <your-$TOKEN>`
2. Click "Connect"
3. Click "List Tools" — count must equal 10
4. Pick `search_products`, args `{"query":"kulaklık","page":0,"size":5}` — should
   return real product results from product-service

---

## 5. MCP Inspector — stdio transport (Claude Desktop simulation)

```bash
npx @modelcontextprotocol/inspector \
  docker run -i --rm --network=host \
    -e MCP_TRANSPORT=stdio \
    -e SPRING_AI_MCP_SERVER_STDIO=true \
    -e SPRING_AI_MCP_SERVER_PROTOCOL=STREAMABLE \
    -e MCP_API_KEY="$MCP_API_KEY" \
    -e CONFIG_SERVER_URL=http://localhost:8888 \
    -e EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://localhost:8761/eureka/ \
    n11/mcp-server:dev
```

Note: `--network=host` is Linux-only. On macOS/Windows Docker Desktop, replace
with `--add-host=host.docker.internal:host-gateway` and rewrite localhost
references to `host.docker.internal`.

---

## 6. Claude Desktop config

Add to `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS) /
`%APPDATA%\Claude\claude_desktop_config.json` (Windows) /
`~/.config/Claude/claude_desktop_config.json` (Linux):

```json
{
  "mcpServers": {
    "n11-storefront": {
      "command": "docker",
      "args": [
        "run", "-i", "--rm", "--network=host",
        "-e", "MCP_TRANSPORT=stdio",
        "-e", "SPRING_AI_MCP_SERVER_STDIO=true",
        "-e", "SPRING_AI_MCP_SERVER_PROTOCOL=STREAMABLE",
        "-e", "MCP_API_KEY=<paste-MCP_API_KEY-from-.env>",
        "-e", "CONFIG_SERVER_URL=http://localhost:8888",
        "-e", "EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://localhost:8761/eureka/",
        "n11/mcp-server:dev"
      ]
    }
  }
}
```

Restart Claude Desktop. The `n11-storefront` MCP server appears in the tools menu
with 10 tools listed.

---

## 7. End-to-end demo flow (the SC-3 grading moment)

In Claude Desktop, run this exact sequence:

1. **"Bana bir kulaklık öner"** → Claude calls `search_products({"query":"kulaklık"})` → returns ~5 products with names + prices in TRY.
2. **"İlk ürünü sepete ekle"** → Claude calls `add_to_cart({"productId":"<id>","quantity":1})` → returns Ok with cart line ID. *(This is the mutating tool that exercises the JWT auth bridge — Plan 09-04.)*
3. **"Sepetimde ne var?"** → Claude calls `view_cart` → returns line items + totals.
4. **"Şimdi sipariş ver, varsayılan adresime"** → Claude calls `create_order({"addressId":"<default-from-Plan 03-04 seed>","paymentMethod":"IYZICO"})` → returns Ok with `orderId`.
5. **"Ödeme bağlantısı ver"** → Claude calls `get_payment_link({"orderId":"<id>"})` → returns Iyzico checkout URL.
6. Open the URL in a browser → enter Iyzico test card **`5528 7900 0000 0008`**, CVC `123`, expiry any future date → 3DS OTP `123456` → callback → order **CONFIRMED**.
7. **"Sipariş durumumu kontrol et"** → Claude calls `get_order_status({"orderId":"<id>"})` → returns `CONFIRMED`.
8. Open `http://localhost:9090/api/v1/orders` (or the storefront's "Siparişlerim" page after Phase 10 lands) and confirm the order is visible to the same user_id whose JWT.sub the agent used. **This is the SC-3 proof.**

---

## 8. Pitfall #15 demo-mode probe (transport visibility)

```bash
docker compose logs mcp-server 2>&1 | grep "MCP transport adapter registered"
docker compose logs mcp-server 2>&1 | grep -E "Started.*McpServerApplication|spring.ai"
```

Expected: a single startup log line confirms `protocol: STREAMABLE` and `stdio: false`
(compose run). Claude Desktop launch confirms `stdio: true`.

---

## 9. Tunnel exposure (D-03 — share with Iyzico tunnel)

The Cloudflare Tunnel (Phase 6 / Phase 11) exposes `https://<tunnel-host>/api/v1/...`.
Phase 9 uses the same hostname under `/mcp/**`:

```bash
curl -X POST https://<tunnel-host>/mcp \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/json, text/event-stream" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'
```

Tunnel command (Phase 11 owns provisioning; Phase 9 confirms reachability only):

```bash
cloudflared tunnel run <tunnel-name>
# OR ngrok http 9090
```

---

## 10. Audit trail check (D-05)

```bash
docker compose exec postgres psql -U identity_user -d identity_db -c \
  "SELECT agent_label, last_used_at FROM identity.agent_api_keys
   WHERE last_used_at > NOW() - INTERVAL '1 hour';"
```

Expected: one row for `demo-agent` with `last_used_at` updated within the last
few minutes (proves /agents/exchange ran recently).

---

## 11. Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| `tools/list` returns `405 Method Not Allowed` | Streamable HTTP not active | Verify `spring.ai.mcp.server.protocol=STREAMABLE` in `mcp-server.yml`; rebuild config-server Jib image |
| `tools/list` returns 0 tools | AgentToolMcpRegistration didn't load | Check mcp-server logs for "MCP transport adapter registered N tools"; if absent, agent-toolset component scan failed |
| `/agents/exchange` returns 401 with valid key | Hash mismatch (most likely a typo) OR key revoked | Re-capture from identity-service logs; truncate + re-seed if needed |
| Mutating tools (add_to_cart) return AUTH_REQUIRED | JwtBearerInterceptor not wired | Verify `McpRestClientConfig.toolRestClientBuilder` is `@Primary @LoadBalanced`; check for `RestClient.Builder` cycle |
| stdio Claude Desktop "server crashed" | Network reach issue from container to localhost services | On macOS/Windows, swap `--network=host` for `--add-host=host.docker.internal:host-gateway` and update env URLs |
| Intermittent 401 on long-running session | JWT expired mid-session, refresh buffer too tight | Already mitigated by 10-min buffer (Plan 09-04 Pitfall #4); if still seen, increase buffer to 15 min |

---

*Phase 9 RUNBOOK — last updated 2026-05-02*
