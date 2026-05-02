# n11 Bootcamp Final Case — Agentic E-Commerce Clone

A 13-service Spring Boot microservices backend with a Turkish React storefront, an AI shopping assistant, and an MCP server exposing the same toolset to external AI agents.

---

## 30-second demo path

```bash
# 1. Copy env placeholders
cp .env.example .env

# 2. Build all 13 backend Jib images locally
./gradlew :eureka-server:jibDockerBuild :config-server:jibDockerBuild :api-gateway:jibDockerBuild :identity-service:jibDockerBuild :product-service:jibDockerBuild :inventory-service:jibDockerBuild :cart-service:jibDockerBuild :order-service:jibDockerBuild :payment-service:jibDockerBuild :notification-service:jibDockerBuild :search-service:jibDockerBuild :ai-service:jibDockerBuild :mcp-server:jibDockerBuild

# 3. Start the full stack (Postgres, RabbitMQ, all 13 services, frontend)
docker compose --profile full up

# 4. Verify the public tunnel reaches the products API
DEMO_TUNNEL_HOSTNAME=<tunnel-hostname> scripts/verify-demo-tunnel.sh
```

---

## Environment matrix

All secrets live in the root `.env` file (gitignored) and are injected at runtime.
**Never commit real keys.**

### Required secrets

| Variable | Source | Description |
|---|---|---|
| `GEMINI_API_KEY` | Google AI Studio | Gemini API key for the chat assistant |
| `IYZICO_API_KEY` | Iyzico Sandbox Dashboard | Sandbox API key |
| `IYZICO_SECRET_KEY` | Iyzico Sandbox Dashboard | Sandbox secret key |
| `JWT_PRIVATE_KEY` | `openssl genrsa 2048` | RS256 private key in PEM PKCS#8 format |
| `SLACK_WEBHOOK_URL` | Slack Incoming Webhooks | CI/build notification target |
| `CLOUDFLARE_TUNNEL_TOKEN` | Cloudflare Zero Trust | Primary tunnel token (preferred) |
| `NGROK_AUTHTOKEN` | ngrok dashboard | Fallback tunnel authtoken |

### Public config

| Variable | Default | Description |
|---|---|---|
| `PUBLIC_BASE_URL` | `<set-in-env>` | HTTPS URL of the API gateway through the tunnel |
| `VITE_API_BASE_URL` | `http://localhost:9090` | Frontend → gateway base URL |
| `IMAGE_REGISTRY` | `n11` | Docker image registry prefix (`ghcr.io/<owner>/<repo>` for GHCR) |
| `IMAGE_TAG` | `dev` | Docker image tag (`latest` or `vX.Y.Z` for releases) |
| `DEMO_TUNNEL_HOSTNAME` | `<set-in-env>` | Public hostname without `https://` prefix |

---

## Cloudflare Tunnel (primary)

Cloudflare Tunnel creates a permanent HTTPS hostname routed to your local API gateway on port 9090.
This is the preferred path for live Iyzico sandbox callbacks and for the interview demo URL.

**Prerequisites:** A Cloudflare account with a personal domain added to Cloudflare DNS.

```bash
# 1. Install cloudflared (example: Linux x86_64)
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 \
  -o /usr/local/bin/cloudflared && chmod +x /usr/local/bin/cloudflared

# 2. Authenticate — opens a browser window to pick the Cloudflare zone
cloudflared tunnel login

# 3. Create a named tunnel (one-time)
cloudflared tunnel create n11-demo

# 4. Configure routing — replace <ZONE> with your domain (e.g. example.com)
cloudflared tunnel route dns n11-demo n11-demo.<ZONE>

# 5. Create the tunnel config file (replace <TUNNEL_UUID> from step 3 output)
mkdir -p ~/.cloudflared
cat > ~/.cloudflared/config.yml <<'EOF'
tunnel: <TUNNEL_UUID>
credentials-file: ~/.cloudflared/<TUNNEL_UUID>.json

ingress:
  - hostname: n11-demo.<ZONE>
    service: http://localhost:9090
  - service: http_status:404
EOF

# 6. Set PUBLIC_BASE_URL and DEMO_TUNNEL_HOSTNAME in .env
echo "PUBLIC_BASE_URL=https://n11-demo.<ZONE>" >> .env
echo "DEMO_TUNNEL_HOSTNAME=n11-demo.<ZONE>" >> .env

# 7. Run the tunnel (keep this terminal open during demo / sandbox testing)
cloudflared tunnel run n11-demo
```

After `cloudflared tunnel run` shows `Connection registered connIndex=0`, your gateway is reachable at:
`https://n11-demo.<ZONE>/api/v1/products`

---

## ngrok fallback

Use ngrok if Cloudflare setup fails or the domain is not available.
ngrok gives a random subdomain on every free-tier start — update `PUBLIC_BASE_URL` and `DEMO_TUNNEL_HOSTNAME` each time.

```bash
# 1. Install ngrok from https://ngrok.com/download

# 2. Save your authtoken (one-time)
ngrok config add-authtoken $NGROK_AUTHTOKEN

# 3. Start the tunnel pointing at the API gateway
ngrok http 9090
```

ngrok prints a forwarding URL such as `https://abc123.ngrok-free.app`.

```bash
# 4. Update .env
PUBLIC_BASE_URL=https://abc123.ngrok-free.app
DEMO_TUNNEL_HOSTNAME=abc123.ngrok-free.app
```

Restart services (`docker compose --profile full up -d`) so the new `PUBLIC_BASE_URL` is picked up.

> Paid ngrok plan provides a stable subdomain and removes the session timeout.

---

## GHCR release images

On a `v*` Git tag, GitHub Actions publishes every service image to GHCR:

```bash
IMAGE_REGISTRY=ghcr.io/<owner>/<repo> IMAGE_TAG=v1.0.0 docker compose --profile full up
```

You can also pull `latest` for the most recent release:

```bash
IMAGE_REGISTRY=ghcr.io/<owner>/<repo> IMAGE_TAG=latest docker compose --profile full up
```

For reproducible demos, pin an immutable `v*` tag instead of `latest`.

---

## Iyzico sandbox demo

Test card for the happy path:

- **Card number:** `5528 7900 0000 0008`
- **Cardholder name:** `John Doe`
- **Expiry:** `12/30`
- **CVC:** `123`
- **3DS OTP:** `283356`

Complete flow:
1. Add a product to cart in the storefront.
2. Proceed to checkout (`/odeme/adres`) and select an address.
3. Click **Siparişi Tamamla** — the Iyzico hosted form opens.
4. Enter the test card above, complete 3DS with OTP `283356`.
5. Iyzico POSTs the callback to `PUBLIC_BASE_URL/api/v1/payments/iyzico/callback`.
6. Verify `GET /api/v1/orders/{orderId}` returns `status=CONFIRMED`.

> Full test card matrix (decline, 3DS edge cases, timeout) is in `payment-service/README.md`.

---

## AI assistant demo path

1. Open the storefront at `http://localhost:5173` (or through the tunnel).
2. Click the floating **Yapay Zeka Alışveriş Asistanı** bubble (bottom-right).
3. Type `MacBook ara` — the assistant streams a Turkish response, searches products, and shows compact cards.
4. Click **Sepete Ekle** on a product card — the header cart badge updates within 1 second.
5. Ask `Sepetimde ne var?` — the assistant summarizes the current cart.
6. Proceed to checkout from the cart page to complete the order.

The conversation persists across page navigation and browser refresh.

---

## MCP external-agent demo

The same toolset is exposed to external AI agents via the MCP server at `/mcp/**` through the gateway tunnel.

1. Ensure the tunnel is running and `MCP_API_KEY` is set in `.env`.
2. Configure Claude Desktop or the MCP Inspector with the gateway URL:
   ```
   https://<DEMO_TUNNEL_HOSTNAME>/mcp/
   ```
3. Authenticate with `MCP_API_KEY` — the server exchanges it for an internal JWT.
4. List tools: `search_products`, `get_product`, `list_categories`, `add_to_cart`, `view_cart`, `update_cart_item`, `remove_from_cart`, `create_order`, `get_payment_link`, `get_order_status`.
5. Invoke mutating tools (e.g., `add_to_cart`) — they complete through the same cart/order/payment flow as the storefront assistant.

> The MCP server is stateless and shares the exact same `agent-toolset` module as `ai-service`.

---

## Slack notifications

The CI pipeline and release workflow send Slack notifications on build success and failure:

- `✅ build green on <ref>`
- `❌ build failed on <ref>`

Set `SLACK_WEBHOOK_URL` in GitHub Actions secrets (for CI) and in `.env` (for local compose if applicable).

---

## Troubleshooting

**`docker compose --profile full up` fails with "no such image"**
- Run the `./gradlew ... jibDockerBuild` command shown at the top of this README first. Jib builds local images; they are not pulled from a registry by default.

**Tunnel script returns non-200**
- Verify the stack is healthy: `docker compose ps`
- Verify the gateway responds locally: `curl http://localhost:9090/api/v1/products`
- Verify the tunnel is connected: `cloudflared tunnel info n11-demo` or check the ngrok web UI.
- Ensure `DEMO_TUNNEL_HOSTNAME` does **not** include `https://`.

**Iyzico callback not received (payment stays PENDING)**
- Verify `PUBLIC_BASE_URL` is reachable: `curl -v https://<your-tunnel-url>/actuator/health`
- Confirm the tunnel is running and shows connected.
- Check Iyzico dashboard sandbox logs for callback delivery status.

**Frontend shows "Bir hata oluştu" on every API call**
- Verify `VITE_API_BASE_URL` in `frontend/.env` points to the gateway host port (`http://localhost:9090` by default).
- Verify the gateway is healthy in `docker compose ps`.

**CI build fails with "Gradle daemon disappeared"**
- The GitHub Actions workflow uses `--no-daemon`. If running locally, ensure sufficient RAM for 13 parallel Jib builds.

---

*Built for the Patika.dev × n11 Spring Boot Bootcamp final case.*
