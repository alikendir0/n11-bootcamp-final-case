# Phase 6 — Iyzico Sandbox Smoke Runbook

Execute this runbook after all Phase 6 plans complete and Jib images are built.
Commands assume working directory = project root.

**This runbook proves PAY-07: a real sandbox card payment traverses the full saga end-to-end
through a public Cloudflare Tunnel / ngrok callback, resulting in order `CONFIRMED` (happy path)
and order `CANCELLED` with inventory `RELEASED` (failure / timeout paths).**

The operator must exercise **at minimum**: the happy path (row 1), one decline path (any of rows 2-3),
and the timeout path (row 7). Other paths are optional but increase confidence.

---

## Prerequisites

- Docker + docker compose installed.
- `.env` populated with **real Iyzico sandbox credentials**:
  - `IYZICO_API_KEY` — from [Iyzico Sandbox Dashboard](https://sandbox.iyzipay.com)
  - `IYZICO_SECRET_KEY` — same source
  - `PUBLIC_BASE_URL` — public HTTPS URL of the API gateway (Cloudflare Tunnel hostname or ngrok URL)
  - All Phase 5 database passwords must be set.
- Cloudflare Tunnel or ngrok is running and `PUBLIC_BASE_URL` is reachable from the internet.
  Verify: `curl -fsS https://<your-public-url>/actuator/health`
  Expected: `{"status":"UP"}`

---

## Step 0 — Preflight (automated tests)

Run the payment-service and order-service unit/integration test suites to confirm no regressions:

```bash
./gradlew :payment-service:test :order-service:test
```

All tests must pass before proceeding to live sandbox testing.

---

## Step 1 — Jib rebuild

Rebuild the images that change with Phase 6 config (payment-service uses Iyzico env vars
pulled from config-server; config-server JAR must be rebuilt to pick up any yml edits):

```bash
./gradlew :config-server:jibDockerBuild \
          :api-gateway:jibDockerBuild \
          :payment-service:jibDockerBuild \
          :order-service:jibDockerBuild \
          :inventory-service:jibDockerBuild --no-daemon
```

> Note: config-server's `payment-service.yml` is classpath-bound at image-build time
> (Spring Cloud Config native profile). Editing `config-server/src/main/resources/config/*.yml`
> requires a `jibDockerBuild` before the change is served to running containers.

---

## Step 2 — Boot the full stack

```bash
docker compose up -d
docker compose ps
```

Expected: all services show `(healthy)`. Cold-start budget ~120-180s on first run; ~30-60s warm.

If any service is stuck:
```bash
docker compose logs --tail=80 <service-name>
```

---

## Step 3 — Set and verify PUBLIC_BASE_URL

Confirm the tunnel is live and the gateway responds through it:

```bash
# Verify the public URL you set in .env
PUBLIC_BASE_URL=$(grep '^PUBLIC_BASE_URL=' .env | cut -d= -f2)
echo "PUBLIC_BASE_URL=${PUBLIC_BASE_URL}"

curl -fsS "${PUBLIC_BASE_URL}/actuator/health" | python3 -m json.tool
```

Expected: `{"status":"UP"}`.

If this fails, the Iyzico callback will not reach payment-service.
See `payment-service/README.md` for Cloudflare Tunnel and ngrok setup instructions.

---

## Step 4 — Register, login, and add delivery address

```bash
EMAIL="phase6+$(date +%s)@n11.test"
PASS="P@ssw0rd-Phase6"

# Register
curl -fsS -X POST http://localhost:8080/api/v1/identity/auth/register \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\",\"fullName\":\"Phase Six User\"}" \
  | python3 -m json.tool

# Login and extract JWT
JWT=$(curl -fsS -X POST http://localhost:8080/api/v1/identity/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')
echo "JWT=${JWT:0:30}..."

# Add delivery address
ADDRESS_ID=$(curl -fsS -X POST http://localhost:8080/api/v1/identity/addresses \
  -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  -d '{"recipientName":"Ali Demir","phone":"+905551112233","il":"İstanbul","ilce":"Kadıköy","mahalle":"Caferağa","streetLine":"Sokak No:1","postalCode":"34710","title":"Ev"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
echo "ADDRESS_ID=$ADDRESS_ID"
```

Expected: JWT is a non-empty string; ADDRESS_ID is a non-empty UUID.

---

## Step 5 — Add a product to cart

```bash
PRODUCT_ID=$(curl -fsS "http://localhost:8080/api/v1/products?size=1" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["content"][0]["id"])')
echo "PRODUCT_ID=$PRODUCT_ID"

# Verify stock is available
curl -fsS "http://localhost:8080/api/v1/inventory/$PRODUCT_ID" | python3 -m json.tool

# Add to cart
curl -fsS -X POST http://localhost:8080/api/v1/cart/items \
  -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  -d "{\"productId\":\"$PRODUCT_ID\",\"qty\":1}" | python3 -m json.tool
```

Expected: 201 Created with cart item; inventory shows `availableQty >= 1`.

---

## Step 6 — Place order

```bash
IDEMPOTENCY_KEY=$(uuidgen)
ORDER_RESP=$(curl -fsS -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "{\"addressId\":\"$ADDRESS_ID\",\"paymentMethod\":\"CARD\"}")
echo "$ORDER_RESP" | python3 -m json.tool

ORDER_ID=$(echo "$ORDER_RESP" | python3 -c 'import json,sys; print(json.load(sys.stdin)["orderId"])')
echo "ORDER_ID=$ORDER_ID"
```

Expected: HTTP 202; body `{"orderId":"<uuid>","status":"PENDING"}`.

---

## Step 7 — Poll until paymentPageUrl is ready

After placing the order, the saga fires `order.created` → inventory reserves → `stock.reserved` →
payment-service calls Iyzico → `PENDING` checkout row created with `paymentPageUrl`.

```bash
for i in {1..30}; do
  RESP=$(curl -fsS "http://localhost:8080/api/v1/payments/$ORDER_ID" \
    -H "Authorization: Bearer $JWT" 2>/dev/null || echo '{}')
  STATUS=$(echo "$RESP" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("status","?"))' 2>/dev/null)
  URL=$(echo "$RESP" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("paymentPageUrl",""))' 2>/dev/null)
  echo "[t=${i}s] status=$STATUS paymentPageUrl=${URL:0:50}"
  if [ "$STATUS" = "PENDING" ] && [ -n "$URL" ]; then
    echo ""
    echo "paymentPageUrl: $URL"
    break
  fi
  sleep 2
done
```

Expected: within ~30 seconds, `status=PENDING` and `paymentPageUrl` is populated with an
`https://sandbox.iyzipay.com/...` URL.

> If status stays `PENDING_INITIALIZATION` past 30s, check payment-service logs:
> `docker compose logs --tail=60 payment-service`
> Common causes: missing `IYZICO_API_KEY` / `IYZICO_SECRET_KEY`, or Iyzico sandbox down.

---

## Iyzico Sandbox Test Card Matrix

Open `paymentPageUrl` in a browser and enter the card details below.

All sandbox cards use:
- **Cardholder name:** `John Doe`
- **Expiry:** `12/30`
- **CVC:** `123`
- **3DS OTP:** `283356`

| Path | Card Number | Expected Outcome |
|---|---|---|
| Happy / 3DS success | `5528 7900 0000 0008` | retrieve `paymentStatus=SUCCESS` → `payment.completed` → order `CONFIRMED` |
| Decline (insufficient funds) | `4111 1111 1111 1129` | retrieve declines → `payment.failed` reason `DECLINED` errorCode `IYZICO_DECLINED` → order `CANCELLED` + inventory `RELEASED` |
| Decline (do not honour) | `4129 1111 1111 1111` | same decline path as above |
| 3DS edge — `mdStatus=0` | `4131 1111 1111 1117` | `payment.failed` reason `UNKNOWN` errorCode `IYZICO_3DS_MDSTATUS_INVALID` → compensation runs |
| 3DS edge — `mdStatus=4` | `4141 1111 1111 1115` | same as above |
| 3DS init failure | `4151 1111 1111 1112` | `payment.failed` reason `UNKNOWN` errorCode `IYZICO_3DS_INIT_FAILED` → compensation runs |
| Timeout (no card) | _(walk away from form)_ | `PAYMENT_TIMEOUT_MINUTES=1` elapses → scheduled job emits `payment.failed` reason `TIMEOUT` errorCode `PAYMENT_TIMEOUT` → compensation runs |

> Source: [Iyzico test cards](https://docs.iyzico.com/ek-bilgiler/test-kartlari.md)

---

## Step 8A — Happy path: complete payment with `5528 7900 0000 0008`

1. Open `paymentPageUrl` in a browser (captured from Step 7).
2. Enter card `5528 7900 0000 0008`, name `John Doe`, expiry `12/30`, CVC `123`.
3. Submit. When the 3DS OTP prompt appears, enter `283356`.
4. Iyzico redirects the browser to the callback URL. A minimal Turkish confirmation page appears.
5. Verify the callback hit payment-service:

```bash
docker compose logs --tail=30 payment-service | grep "iyzico/callback\|COMPLETED\|payment.completed"
```

6. Poll order status until CONFIRMED:

```bash
for i in {1..30}; do
  STATUS=$(curl -fsS "http://localhost:8080/api/v1/orders/$ORDER_ID" \
    -H "Authorization: Bearer $JWT" | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])')
  echo "[t=${i}s] order status=$STATUS"
  if [ "$STATUS" = "CONFIRMED" ]; then break; fi
  sleep 2
done
```

Expected: `status=CONFIRMED` within ~10-15 seconds of Iyzico redirecting.

7. Verify payment row:

```bash
docker exec n11-postgres psql -U payment_user -d n11 -c \
  "SELECT status, iyzico_payment_id, failure_code FROM payment.payments WHERE order_id='$ORDER_ID';"
```

Expected: `status=COMPLETED`, `iyzico_payment_id` is non-null, `failure_code` is null.

---

## Step 8B — Decline path: place a new order, use card `4111 1111 1111 1129`

For each additional test path, repeat Steps 5-7 to get a fresh order and `paymentPageUrl`.

```bash
# Re-add to cart (cart was cleared after CONFIRMED order)
curl -fsS -X POST http://localhost:8080/api/v1/cart/items \
  -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  -d "{\"productId\":\"$PRODUCT_ID\",\"qty\":1}" > /dev/null

IDEMP2=$(uuidgen)
ORDER2_RESP=$(curl -fsS -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $IDEMP2" \
  -d "{\"addressId\":\"$ADDRESS_ID\",\"paymentMethod\":\"CARD\"}")
ORDER2_ID=$(echo "$ORDER2_RESP" | python3 -c 'import json,sys; print(json.load(sys.stdin)["orderId"])')
echo "ORDER2_ID=$ORDER2_ID"

# Wait for paymentPageUrl
for i in {1..30}; do
  RESP=$(curl -fsS "http://localhost:8080/api/v1/payments/$ORDER2_ID" \
    -H "Authorization: Bearer $JWT" 2>/dev/null || echo '{}')
  STATUS=$(echo "$RESP" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("status","?"))' 2>/dev/null)
  URL=$(echo "$RESP" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("paymentPageUrl",""))' 2>/dev/null)
  if [ "$STATUS" = "PENDING" ] && [ -n "$URL" ]; then echo "paymentPageUrl: $URL"; break; fi
  sleep 2
done
```

Open the new `paymentPageUrl`, enter card `4111 1111 1111 1129`, same name/expiry/CVC.

After Iyzico declines and redirects:

```bash
# Poll for CANCELLED
for i in {1..30}; do
  STATUS=$(curl -fsS "http://localhost:8080/api/v1/orders/$ORDER2_ID" \
    -H "Authorization: Bearer $JWT" | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])')
  echo "[t=${i}s] order status=$STATUS"
  if [ "$STATUS" = "CANCELLED" ]; then break; fi
  sleep 2
done

# Verify inventory released
sleep 8
docker exec n11-postgres psql -U inventory_user -d n11 -c \
  "SELECT order_id, status FROM inventory.stock_reservations WHERE order_id='$ORDER2_ID';"
```

Expected: `order status=CANCELLED` and `stock_reservations.status=RELEASED`.

---

## Step 8C — Timeout path: set PAYMENT_TIMEOUT_MINUTES=1, walk away from the form

For the timeout path, temporarily reduce the timeout to 1 minute so you don't wait 15 minutes.

```bash
# Stop payment-service, override timeout to 1 minute, restart
docker compose stop payment-service
PAYMENT_TIMEOUT_MINUTES=1 PAYMENT_TIMEOUT_SCAN_DELAY_MS=15000 \
  docker compose up -d payment-service
until docker compose ps payment-service | grep -q '(healthy)'; do sleep 2; done

# Re-add to cart
curl -fsS -X POST http://localhost:8080/api/v1/cart/items \
  -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  -d "{\"productId\":\"$PRODUCT_ID\",\"qty\":1}" > /dev/null

IDEMP3=$(uuidgen)
ORDER3_RESP=$(curl -fsS -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $IDEMP3" \
  -d "{\"addressId\":\"$ADDRESS_ID\",\"paymentMethod\":\"CARD\"}")
ORDER3_ID=$(echo "$ORDER3_RESP" | python3 -c 'import json,sys; print(json.load(sys.stdin)["orderId"])')
echo "ORDER3_ID=$ORDER3_ID"

# Wait for paymentPageUrl, then DO NOT complete the payment form
for i in {1..30}; do
  RESP=$(curl -fsS "http://localhost:8080/api/v1/payments/$ORDER3_ID" \
    -H "Authorization: Bearer $JWT" 2>/dev/null || echo '{}')
  STATUS=$(echo "$RESP" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("status","?"))' 2>/dev/null)
  URL=$(echo "$RESP" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("paymentPageUrl",""))' 2>/dev/null)
  if [ "$STATUS" = "PENDING" ] && [ -n "$URL" ]; then echo "Got paymentPageUrl — do NOT complete form"; break; fi
  sleep 2
done

# Wait ~75 seconds for the timeout job to fire (PAYMENT_TIMEOUT_MINUTES=1 + scan delay ~15s)
echo "Waiting 75s for timeout sweep..."
sleep 75

# Verify order CANCELLED and inventory RELEASED
STATUS=$(curl -fsS "http://localhost:8080/api/v1/orders/$ORDER3_ID" \
  -H "Authorization: Bearer $JWT" | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])')
echo "Order status (expect CANCELLED): $STATUS"

docker exec n11-postgres psql -U payment_user -d n11 -c \
  "SELECT status, failure_reason, failure_code FROM payment.payments WHERE order_id='$ORDER3_ID';"

docker exec n11-postgres psql -U inventory_user -d n11 -c \
  "SELECT order_id, status FROM inventory.stock_reservations WHERE order_id='$ORDER3_ID';"
```

Expected: `order status=CANCELLED`; payment row shows `status=TIMED_OUT`, `failure_reason=TIMEOUT`,
`failure_code=PAYMENT_TIMEOUT`; `stock_reservations.status=RELEASED`.

```bash
# Restore default timeout settings
docker compose stop payment-service
docker compose up -d payment-service
until docker compose ps payment-service | grep -q '(healthy)'; do sleep 2; done
```

---

## Step 9 — Callback idempotency (duplicate POST /iyzico/callback)

After Step 8A (CONFIRMED order), re-send the callback token.
The payment is already COMPLETED so the duplicate must be a no-op (zero additional outbox rows).

```bash
# Get the iyzico_token from the DB
TOKEN=$(docker exec n11-postgres psql -U payment_user -d n11 -t -A -c \
  "SELECT iyzico_token FROM payment.payments WHERE order_id='$ORDER_ID';")
echo "TOKEN=$TOKEN"

# Count outbox rows before replay
BEFORE=$(docker exec n11-postgres psql -U payment_user -d n11 -t -A -c \
  "SELECT count(*) FROM payment.outbox WHERE payload LIKE '%$ORDER_ID%';")
echo "Outbox rows before replay: $BEFORE"

# Re-POST the callback
curl -fsS -X POST "http://localhost:8080/api/v1/payments/iyzico/callback" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=$TOKEN" | head -c 300
echo ""

# Count outbox rows after replay
AFTER=$(docker exec n11-postgres psql -U payment_user -d n11 -t -A -c \
  "SELECT count(*) FROM payment.outbox WHERE payload LIKE '%$ORDER_ID%';")
echo "Outbox rows after replay: $AFTER"
```

Expected: `BEFORE == AFTER` — no new outbox row written on duplicate callback.

---

## Step 10 — Verify Springdoc surface includes payment-service

```bash
curl -fsS http://localhost:8080/v3/api-docs/swagger-config | \
  python3 -c 'import json,sys; urls=json.load(sys.stdin)["urls"]; names=[u["name"] for u in urls]; print(names); assert "payment-service" in names; print("PASS: payment-service in Springdoc aggregator")'
```

Expected: `PASS: payment-service in Springdoc aggregator`.

---

## Step 11 — Teardown

```bash
docker compose down
# To wipe volumes for a clean re-run:
docker compose down -v
```

---

## Sign-off Checklist

The operator must check at minimum: rows 1, 4 or 5 (any decline), and row 7 (timeout).

| # | Path | Card / Action | Expected | Outcome |
|---|---|---|---|---|
| 1 | **Happy path (REQUIRED)** | `5528 7900 0000 0008` — complete 3DS OTP `283356` | `paymentStatus=SUCCESS` → `payment.completed` → order `CONFIRMED` | [ ] PASS / [ ] FAIL |
| 2 | Decline (insufficient funds) | `4111 1111 1111 1129` | `payment.failed` reason `DECLINED` → order `CANCELLED` + inventory `RELEASED` | [ ] PASS / [ ] FAIL / [ ] SKIPPED |
| 3 | Decline (do not honour) | `4129 1111 1111 1111` | same as above | [ ] PASS / [ ] FAIL / [ ] SKIPPED |
| 4 | 3DS edge `mdStatus=0` | `4131 1111 1111 1117` | `payment.failed` reason `UNKNOWN` errorCode `IYZICO_3DS_MDSTATUS_INVALID` → compensation | [ ] PASS / [ ] FAIL / [ ] SKIPPED |
| 5 | 3DS edge `mdStatus=4` | `4141 1111 1111 1115` | same as above | [ ] PASS / [ ] FAIL / [ ] SKIPPED |
| 6 | 3DS init failure | `4151 1111 1111 1112` | `payment.failed` reason `UNKNOWN` errorCode `IYZICO_3DS_INIT_FAILED` → compensation | [ ] PASS / [ ] FAIL / [ ] SKIPPED |
| 7 | **Timeout (REQUIRED)** | Walk away from form with `PAYMENT_TIMEOUT_MINUTES=1` | `TIMED_OUT` payment → `payment.failed` reason `TIMEOUT` errorCode `PAYMENT_TIMEOUT` → order `CANCELLED` + inventory `RELEASED` | [ ] PASS / [ ] FAIL |
| 8 | Callback idempotency | Re-POST same token (Step 9) | No additional outbox row; order stays `CONFIRMED` | [ ] PASS / [ ] FAIL |
| 9 | Callback reachability | `curl PUBLIC_BASE_URL/actuator/health` | `{"status":"UP"}` | [ ] PASS / [ ] FAIL |
| 10 | Springdoc surface | `payment-service` in Springdoc aggregator | PASS | [ ] PASS / [ ] FAIL |

**PAY-07 is satisfied when rows 1, 7, and at minimum one of rows 2-6 are marked PASS.**

---

*Phase 6 saga events verified: stock.reserved → Iyzico initialize → payment.completed / payment.failed → compensation*
