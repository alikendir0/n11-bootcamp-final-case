# Phase 5 Smoke Runbook

Execute this runbook after all Phase 5 plans complete and Jib images are built.
Commands assume working directory = project root.

## Prerequisites

- Docker + docker compose installed
- `.env` populated (CART_DB_PASSWORD / ORDERS_DB_PASSWORD / PAYMENT_DB_PASSWORD must be set; the rest from Phase 4)
- All Phase 5 Jib images built. Full build command (one shot):
  ```bash
  ./gradlew :common-outbox:assemble \
            :cart-service:jibDockerBuild :order-service:jibDockerBuild \
            :payment-service:jibDockerBuild :inventory-service:jibDockerBuild \
            :identity-service:jibDockerBuild :config-server:jibDockerBuild \
            :api-gateway:jibDockerBuild --no-daemon
  ```

CRITICAL (Plan 04-03 lesson): config-server image MUST be rebuilt because Phase 5 Plan 05-05
edited `config-server/src/main/resources/config/api-gateway.yml` (added cart + order routes).
Spring Cloud Config native profile reads `classpath:/config/` which is JAR-bound at image-build
time. Inventory-service image MUST be rebuilt because Plan 05-04 added 2 compensation consumers
(CD-08 PaymentFailedConsumer + CD-09 OrderCancelledConsumer) and Plan 05-01 migrated outbox to
common-outbox.

---

## Step 1 — Boot the full stack

```bash
docker compose up -d
docker compose ps
```

Expected: all 11 services show `(healthy)`. Cold-start budget ~120-180s on first run; ~30-60s warm.

Services in expected healthy order (dependency chain):
1. postgres, rabbitmq (infra — no depends_on)
2. eureka-server, config-server (independent)
3. identity-service, product-service (postgres + rabbitmq + eureka + config)
4. inventory-service (same)
5. cart-service (+ product-service)
6. order-service (+ cart + product + identity)
7. payment-service (basic 4)
8. api-gateway (waits for ALL 7 business services)

If any service is stuck:
```bash
docker compose logs --tail=60 <service-name>
```

---

## Step 2 — Register + login a user (re-uses Phase 3 flow)

```bash
EMAIL="phase5+$(date +%s)@n11.test"
PASS="P@ssw0rd-Phase5"
curl -fsS -X POST http://localhost:8080/api/v1/identity/auth/register \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\",\"fullName\":\"Phase Five User\"}" | python3 -m json.tool

JWT=$(curl -fsS -X POST http://localhost:8080/api/v1/identity/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')
echo "JWT=${JWT:0:30}..."
```

Expected: register returns user object; JWT is a non-empty string.

---

## Step 3 — Add a delivery address (Phase 3 endpoint)

```bash
ADDRESS_ID=$(curl -fsS -X POST http://localhost:8080/api/v1/identity/addresses \
  -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  -d '{"recipientName":"Ali Demir","phone":"+905551112233","il":"İstanbul","ilce":"Kadıköy","mahalle":"Caferağa","streetLine":"Sokak No:1","postalCode":"34710","title":"Ev"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
echo "ADDRESS_ID=$ADDRESS_ID"
```

Expected: ADDRESS_ID is a non-empty UUID.

---

## Step 4 — Pick a product + verify stock (Phase 4 endpoints)

```bash
PRODUCT_ID=$(curl -fsS http://localhost:8080/api/v1/products?size=1 \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["content"][0]["id"])')
echo "PRODUCT_ID=$PRODUCT_ID"

curl -fsS http://localhost:8080/api/v1/inventory/$PRODUCT_ID | python3 -m json.tool
```

Expected: stockState ∈ {STOKTA, SON_URUN, TUKENDI}; pick a product with `availableQty` ≥ 2.

---

## Step 5 — Cart smoke (Phase 5 cart-service)

Add to cart:
```bash
curl -fsS -X POST http://localhost:8080/api/v1/cart/items \
  -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  -d "{\"productId\":\"$PRODUCT_ID\",\"qty\":2}" | python3 -m json.tool
```

Expected: 201 Created; CartView body with `items[0].qty=2` + `lineTotal=qty*unitPriceSnapshot` +
`nameSnapshot` populated.

Same product again (UPSERT verification — D-02):
```bash
curl -fsS -X POST http://localhost:8080/api/v1/cart/items \
  -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  -d "{\"productId\":\"$PRODUCT_ID\",\"qty\":1}" | python3 -m json.tool
```

Expected: `items[0].qty = 3` (2+1 summed); single row (UPSERT, not insert).

GET /cart:
```bash
curl -fsS http://localhost:8080/api/v1/cart -H "Authorization: Bearer $JWT" | python3 -m json.tool
```

Expected: items array has 1 entry with qty=3 and the price/name snapshots populated.

---

## Step 6 — Order smoke (Phase 5 order-service + saga)

Generate Idempotency-Key and place order (D-05 — Stripe pattern):
```bash
IDEMPOTENCY_KEY=$(uuidgen)
ORDER_RESP=$(curl -fsS -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "{\"addressId\":\"$ADDRESS_ID\",\"paymentMethod\":\"CARD\"}" \
  -w '\n%{http_code}\n')
echo "$ORDER_RESP"
```

Expected: HTTP 202; body `{"orderId":"<uuid>","status":"PENDING"}`.

Capture orderId:
```bash
ORDER_ID=$(echo "$ORDER_RESP" | head -1 | python3 -c 'import json,sys; print(json.load(sys.stdin)["orderId"])')
echo "ORDER_ID=$ORDER_ID"
```

Idempotency-Key replay verification (D-05):
```bash
curl -fsS -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "{\"addressId\":\"$ADDRESS_ID\",\"paymentMethod\":\"CARD\"}" \
  -w '\n%{http_code}\n'
```

Expected: HTTP 200 (NOT 202 — replay); same orderId returned.

---

## Step 7 — Poll for saga completion (D-03 + ORD-05)

```bash
for i in {1..30}; do
  STATUS=$(curl -fsS http://localhost:8080/api/v1/orders/$ORDER_ID \
    -H "Authorization: Bearer $JWT" | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])')
  echo "[t=${i}s] status=$STATUS"
  if [ "$STATUS" = "CONFIRMED" ]; then break; fi
  sleep 1
done
```

Expected: status reaches CONFIRMED within ~10-15 seconds.
Saga path: order.created → inventory.q.order-created → stock.reserved → payment.q.stock-reserved
→ payment.completed → order.q.payment-completed → order CONFIRMED → order.confirmed
→ cart.q.order-confirmed → cart cleared (D-07).

Mock payment delay: `mock.payment.delay-ms=100` + outbox poller 5s × 2 hops → ~10-15s total.

---

## Step 8 — Verify processed_events row counts (CLAUDE.md Rule #3 + ARCH-07)

inventory-service (OrderCreatedConsumer):
```bash
docker exec n11-postgres psql -U inventory_user -d n11 -c \
  "SELECT consumer, count(*) FROM inventory.processed_events GROUP BY consumer;"
```

Expected: at least 1 row for `OrderCreatedConsumer`.

payment-service (StockReservedConsumer):
```bash
docker exec n11-postgres psql -U payment_user -d n11 -c \
  "SELECT consumer, count(*) FROM payment.processed_events GROUP BY consumer;"
```

Expected: at least 1 row for `StockReservedConsumer`.

order-service (StockReservedConsumer + PaymentCompletedConsumer):
```bash
docker exec n11-postgres psql -U orders_user -d n11 -c \
  "SELECT consumer, count(*) FROM orders.processed_events GROUP BY consumer;"
```

Expected: rows for `StockReservedConsumer` (1) and `PaymentCompletedConsumer` (1).

cart-service (OrderConfirmedConsumer):
```bash
docker exec n11-postgres psql -U cart_user -d n11 -c \
  "SELECT consumer, count(*) FROM cart.processed_events GROUP BY consumer;"
```

Expected: at least 1 row for `OrderConfirmedConsumer`.

Then verify cart was cleared (D-07):
```bash
curl -fsS http://localhost:8080/api/v1/cart -H "Authorization: Bearer $JWT" | python3 -m json.tool
```

Expected: `items[]` is empty (cart cleared on order.confirmed).

---

## Step 9 — Idempotency over real AMQP — CLAUDE.md Rule #3 (re-publish order.created)

Capture original order.created envelope from order-service outbox:
```bash
ENVELOPE=$(docker exec n11-postgres psql -U orders_user -d n11 -t -A -c \
  "SELECT payload FROM orders.outbox WHERE event_type='order.created' AND payload LIKE '%$ORDER_ID%' LIMIT 1;")
echo "$ENVELOPE" | head -c 200; echo
EVENT_ID=$(echo "$ENVELOPE" | python3 -c 'import json,sys; print(json.load(sys.stdin)["eventId"])')
echo "EVENT_ID=$EVENT_ID"
```

Re-publish via rabbitmqadmin (properties include message_id=<eventId> per 999.2 requirement):
```bash
RABBIT_USER=$(grep RABBITMQ_DEFAULT_USER .env | cut -d= -f2)
RABBIT_PASS=$(grep RABBITMQ_DEFAULT_PASS .env | cut -d= -f2)
docker exec n11-rabbitmq rabbitmqadmin \
  --username="$RABBIT_USER" --password="$RABBIT_PASS" \
  publish exchange=orders.tx routing_key=order.created \
  properties="message_id=$EVENT_ID,content_type=application/json" \
  payload="$ENVELOPE"
sleep 8
```

Verify NO second reservation (CLAUDE.md Rule #3 over real AMQP — idempotency must hold):
```bash
docker exec n11-postgres psql -U inventory_user -d n11 -c \
  "SELECT count(*) FROM inventory.stock_reservations WHERE order_id='$ORDER_ID';"
```

Expected: count = 1 (no duplicate reservation — processed_events inbox blocked the re-delivery).

---

## Step 10 — User-cancel compensation path (CD-09)

**Pre-step (W6 race-window fix):** the default `mock.payment.delay-ms=100` makes the cancel
race against payment-service's mock-completed event sub-second on the local stack. To reliably
verify CD-09 here, bump the delay to `MOCK_PAYMENT_DELAY_MS=5000` and restart payment-service:

```bash
# Bump payment delay so cancel reliably wins the race window
docker compose stop payment-service
MOCK_PAYMENT_DELAY_MS=5000 docker compose up -d payment-service
# Wait for healthy
until docker compose ps payment-service | grep -q '(healthy)'; do sleep 1; done
```

Place a SECOND order, then cancel it within the saga window:
```bash
# Add an item back to cart (Step 8 cleared it on order.confirmed)
curl -fsS -X POST http://localhost:8080/api/v1/cart/items \
  -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  -d "{\"productId\":\"$PRODUCT_ID\",\"qty\":1}" > /dev/null

IDEMP2=$(uuidgen)
ORD2=$(curl -fsS -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $IDEMP2" \
  -d "{\"addressId\":\"$ADDRESS_ID\",\"paymentMethod\":\"CARD\"}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["orderId"])')
echo "ORD2=$ORD2"

# With 5s payment delay we have a ~4s cancel window.
# Wait 1s for inventory reservation to land (stock.reserved outbox poller up to 5s),
# then cancel.
sleep 1
curl -fsS -X POST http://localhost:8080/api/v1/orders/$ORD2/cancel \
  -H "Authorization: Bearer $JWT" -w '\n%{http_code}\n'
```

Expected: 204 No Content (cancel beat the saga reliably with the 5s delay).
Note: if the cancel races and returns 409 (saga already completed), that is also acceptable
on the default mock-100ms-delay stack — document the outcome in the sign-off checklist.

Verify order status + cancel_reason:
```bash
docker exec n11-postgres psql -U orders_user -d n11 -c \
  "SELECT id, status, cancel_reason FROM orders.orders WHERE id='$ORD2';"
```

Expected: `status=CANCELLED`, `cancel_reason=USER_CANCELLED`.

Verify inventory-service released stock (CD-09):
```bash
sleep 8
docker exec n11-postgres psql -U inventory_user -d n11 -c \
  "SELECT order_id, status FROM inventory.stock_reservations WHERE order_id='$ORD2';"
```

Expected: `status=RELEASED` (OrderCancelledConsumer in inventory-service processed order.cancelled).

**Post-step:** restore default delay so subsequent re-runs see normal saga timing:
```bash
docker compose stop payment-service
unset MOCK_PAYMENT_DELAY_MS  # use config-server default 100ms
docker compose up -d payment-service
until docker compose ps payment-service | grep -q '(healthy)'; do sleep 1; done
```

---

## Step 11 — Springdoc aggregator surface verification (CART-06 + ORD-06 + QUAL-01)

Open `http://localhost:8080/swagger-ui.html` in a browser:
- Dropdown shows 5 entries: `identity-service`, `product-service`, `inventory-service`,
  `cart-service`, `order-service`.
- Selecting `cart-service` loads /cart endpoints (GET, POST /items, PATCH /items/{productId},
  DELETE /items/{productId}).
- Selecting `order-service` loads /orders endpoints (POST, GET, GET/{id}, POST/{id}/cancel).

CLI alternative:
```bash
curl -fsS http://localhost:8080/v3/api-docs/swagger-config | python3 -m json.tool | \
  grep -E '"name"'
```

Expected: `cart-service` + `order-service` entries present; NO `payment-service` entry (D-06).

```bash
curl -fsS http://localhost:8080/v3/api-docs/swagger-config | \
  python3 -c 'import json,sys; urls=json.load(sys.stdin)["urls"]; names=[u["name"] for u in urls]; print(names); assert "cart-service" in names and "order-service" in names; assert "payment-service" not in names; print("PASS")'
```

---

## Step 12 — Correlation-ID flow (ARCH-08)

Pull the correlationId from the confirmed order:
```bash
CID=$(docker exec n11-postgres psql -U orders_user -d n11 -t -A -c \
  "SELECT correlation_id FROM orders.orders WHERE id='$ORDER_ID';")
echo "CID=$CID"
docker compose logs --tail=400 cart-service order-service inventory-service payment-service \
  | grep "$CID" | wc -l
```

Expected: ≥4 hits across the 4 services (one per service consumer/producer handling that saga chain).
The correlationId flows: order-service (publish) → inventory-service (consume) → payment-service
(consume) → order-service (PaymentCompleted consume) → cart-service (OrderConfirmed consume).

---

## Step 13 — Teardown

```bash
docker compose down
# To wipe volumes (fresh Flyway run on next boot):
docker compose down -v
```

---

## Sign-off Checklist

- [ ] Step 1: All 11 services healthy (`docker compose ps` shows all healthy within ~120-180s)
- [ ] Step 2: User registered + JWT obtained
- [ ] Step 3: Address saved (ADDRESS_ID is a valid UUID)
- [ ] Step 4: Product ID retrieved + inventory shows availableQty ≥ 2
- [ ] Step 5: Cart UPSERT proven (qty 2+1 → qty 3, single row in cart_items)
- [ ] Step 6: POST /orders returns 202 + replay with same Idempotency-Key returns 200 with same orderId (D-05)
- [ ] Step 7: Order reaches status=CONFIRMED within ~15 seconds (saga happy path end-to-end)
- [ ] Step 8: processed_events row counts ≥1 per consumer (all 4 services); cart cleared (D-07)
- [ ] Step 9: Re-publishing order.created leaves stock_reservations count=1 (CLAUDE.md Rule #3 over real AMQP)
- [ ] Step 10: POST /orders/{id}/cancel either succeeds (204 → CANCELLED + stock RELEASED) or returns 409 (saga race — acceptable on mock-100ms-delay stack; note outcome)
- [ ] Step 11: Springdoc aggregator dropdown shows cart-service + order-service (NOT payment-service per D-06)
- [ ] Step 12: correlationId flows through ≥4 services in JSON logs (ARCH-08)

All 12 checklist items must be checked (or noted as 409-race-acceptable for Step 10) before Phase 5 is considered complete.

---

*Phase 5 saga events verified: order.created, stock.reserved, payment.completed, order.confirmed, order.cancelled*
