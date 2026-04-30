# payment-service

Iyzico Checkout Form integration for the n11 e-commerce demo.
Handles `stock.reserved` → Iyzico checkout initialization → public callback → `payment.completed` / `payment.failed` saga events.

---

## Environment

All secrets live in the root `.env` file (gitignored) and are injected at runtime.
**Never commit real keys.**

| Variable | Required | Description |
|---|---|---|
| `IYZICO_API_KEY` | yes | Iyzico sandbox API key (from Iyzico Sandbox Dashboard) |
| `IYZICO_SECRET_KEY` | yes | Iyzico sandbox secret key (from Iyzico Sandbox Dashboard) |
| `PUBLIC_BASE_URL` | yes | Public HTTPS URL of the API gateway — Cloudflare Tunnel hostname or ngrok forwarding URL (e.g. `https://n11-demo.example.com` or `https://abc123.ngrok-free.app`). Trailing slash is stripped automatically. |
| `PAYMENT_TIMEOUT_MINUTES` | no | Minutes until a `PENDING` checkout times out and triggers compensation (default `15`) |
| `PAYMENT_TIMEOUT_SCAN_DELAY_MS` | no | Sweep cadence for the timeout job in milliseconds (default `60000` = 1 minute) |
| `IYZICO_DEMO_BUYER_IDENTITY_NUMBER` | no | TC identity number sent to Iyzico for the demo buyer (default `74300864791`) |
| `PAYMENT_DB_PASSWORD` | yes | Postgres password for `payment_user` schema |

Iyzico callback URL is derived automatically:
```
${PUBLIC_BASE_URL}/api/v1/payments/iyzico/callback
```

---

## Cloudflare Tunnel (primary)

Cloudflare Tunnel creates a permanent HTTPS hostname routed to your local API gateway on port 8080.
This is the preferred path for live Iyzico sandbox callbacks and for the interview demo URL.

> The docker-compose `cloudflared` sidecar belongs to Phase 11 deploy hardening and is explicitly deferred per D-03.
> Run cloudflared manually during Phase 6 sandbox testing.

**Prerequisites:** A Cloudflare account with a personal domain added to Cloudflare DNS.

### Step-by-step

```bash
# 1. Install cloudflared (example: Linux x86_64)
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 \
  -o /usr/local/bin/cloudflared && chmod +x /usr/local/bin/cloudflared

# 2. Authenticate — opens a browser window to pick the Cloudflare zone
cloudflared tunnel login

# 3. Create a named tunnel (one-time)
cloudflared tunnel create n11-demo

# 4. Configure routing — replace <ZONE> with your domain (e.g. example.com)
#    and choose a subdomain (e.g. n11-demo.example.com)
cloudflared tunnel route dns n11-demo n11-demo.<ZONE>

# 5. Create the tunnel config file (replace <TUNNEL_UUID> from step 3 output)
mkdir -p ~/.cloudflared
cat > ~/.cloudflared/config.yml <<'EOF'
tunnel: <TUNNEL_UUID>
credentials-file: ~/.cloudflared/<TUNNEL_UUID>.json

ingress:
  - hostname: n11-demo.<ZONE>
    service: http://localhost:8080
  - service: http_status:404
EOF

# 6. Set PUBLIC_BASE_URL in .env
echo "PUBLIC_BASE_URL=https://n11-demo.<ZONE>" >> .env

# 7. Run the tunnel (keep this terminal open during demo / sandbox testing)
cloudflared tunnel run n11-demo
```

After `cloudflared tunnel run` shows `Connection registered connIndex=0`, your gateway is reachable at:
`https://n11-demo.<ZONE>/api/v1/payments/iyzico/callback`

---

## ngrok fallback

Use ngrok if Cloudflare setup fails or the domain is not available.
ngrok gives a random subdomain on every free-tier start — update `PUBLIC_BASE_URL` each time.

```bash
# 1. Install ngrok from https://ngrok.com/download

# 2. Save your authtoken (one-time)
ngrok config add-authtoken $NGROK_AUTHTOKEN

# 3. Start the tunnel pointing at the API gateway
ngrok http 8080
```

ngrok prints a forwarding URL such as `https://abc123.ngrok-free.app`.

```bash
# 4. Update PUBLIC_BASE_URL in .env
# Replace the placeholder with the forwarding URL printed by ngrok
PUBLIC_BASE_URL=https://abc123.ngrok-free.app
```

Restart services (`docker compose up -d payment-service`) so the new `PUBLIC_BASE_URL` is picked up.

> Paid ngrok plan provides a stable subdomain and removes the session timeout.

---

## Iyzico sandbox checkout

Full sandbox flow after tunnel is running:

1. Ensure `IYZICO_API_KEY`, `IYZICO_SECRET_KEY`, and `PUBLIC_BASE_URL` are set in `.env`.
2. Rebuild and restart payment-service:
   ```bash
   ./gradlew :config-server:jibDockerBuild :payment-service:jibDockerBuild
   docker compose up -d config-server payment-service
   ```
3. Create an order through the API (see the smoke runbook at
   `.planning/phases/06-payment-iyzico/06-06-SMOKE-RUNBOOK.md`).
4. Poll `GET /api/v1/payments/{orderId}` until `status=PENDING` and `paymentPageUrl` is present.
5. Open `paymentPageUrl` in a browser — the Iyzico hosted form appears.
6. Enter a sandbox test card (see below), complete 3DS OTP `283356`, and submit.
7. Iyzico POSTs the callback to `PUBLIC_BASE_URL/api/v1/payments/iyzico/callback`.
8. Verify `GET /api/v1/orders/{orderId}` returns `status=CONFIRMED`.

---

## Sandbox test cards

All sandbox cards use:
- **Cardholder name:** `John Doe`
- **Expiry:** `12/30`
- **CVC:** `123`
- **3DS OTP:** `283356`

| Path | Card Number | Expected Outcome |
|---|---|---|
| Happy / 3DS success | `5528 7900 0000 0008` | `paymentStatus=SUCCESS` → `payment.completed` → order `CONFIRMED` |
| Decline (insufficient funds) | `4111 1111 1111 1129` | Iyzico declines → `payment.failed` reason `DECLINED` errorCode `IYZICO_DECLINED` → order `CANCELLED` + inventory `RELEASED` |
| Decline (do not honour) | `4129 1111 1111 1111` | same decline path as above |
| 3DS edge — `mdStatus=0` | `4131 1111 1111 1117` | `payment.failed` reason `UNKNOWN` errorCode `IYZICO_3DS_MDSTATUS_INVALID` → compensation runs |
| 3DS edge — `mdStatus=4` | `4141 1111 1111 1115` | same as above |
| 3DS init failure | `4151 1111 1111 1112` | `payment.failed` reason `UNKNOWN` errorCode `IYZICO_3DS_INIT_FAILED` → compensation runs |
| Timeout (no card) | _(walk away from form)_ | `PAYMENT_TIMEOUT_MINUTES` elapses → `payment.failed` reason `TIMEOUT` errorCode `PAYMENT_TIMEOUT` → compensation runs |

> Source: [Iyzico test cards](https://docs.iyzico.com/ek-bilgiler/test-kartlari.md)

---

## Callback troubleshooting

**Callback not received (payment stays PENDING)**
- Verify `PUBLIC_BASE_URL` is reachable: `curl -v https://<your-tunnel-url>/actuator/health`
- Confirm the tunnel is running and shows connected (cloudflared log or ngrok web UI)
- Check Iyzico dashboard sandbox logs for callback delivery status

**Error code `5062` — submitted amount must equal the total amount of all breakdowns**
- This means the Iyzico request `price`/`paidPrice` does not match the sum of `basketItems[*].price`.
- Fix: basket-line-price reconciliation in `DefaultIyzicoCheckoutClient` — ensure each basket item price is the exact unit price × quantity, and the sum equals `order.totalAmount`.

**HTTP 401 on callback**
- The callback path `POST /api/v1/payments/iyzico/callback` is on the gateway public allowlist.
  Verify `config-server/src/main/resources/config/api-gateway.yml` has the allow-list entry.
  Also verify payment-service `SecurityConfig` permits this path without JWT.

**`payment.failed` published but order not CANCELLED**
- Check order-service `PaymentFailedConsumer` logs for delivery errors or duplicate-event short-circuit.
- Verify `processed_events` in order-service schema does not already have this event ID (stale test data).
