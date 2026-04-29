# Phase 4 Smoke Runbook

Execute this runbook after all Phase 4 plans complete and Jib images are built.
Commands assume working directory = project root.

## Prerequisites

- Docker + docker compose installed
- `.env` file populated (see `.env.example`)
- All Phase 4 Jib images built:
  ```bash
  ./gradlew :product-service:jibDockerBuild :inventory-service:jibDockerBuild --no-daemon
  ```
- config-server image rebuilt (api-gateway.yml modified in Phase 4 Plan 03 — stale image would miss routes):
  ```bash
  ./gradlew :config-server:jibDockerBuild --no-daemon
  ```
- api-gateway image rebuilt (SecurityConfig.java modified in Phase 4 Plan 03):
  ```bash
  ./gradlew :api-gateway:jibDockerBuild --no-daemon
  ```

Full rebuild command (one shot):
```bash
./gradlew :config-server:jibDockerBuild :api-gateway:jibDockerBuild :identity-service:jibDockerBuild :product-service:jibDockerBuild :inventory-service:jibDockerBuild --no-daemon
```

---

## Step 1 — Boot the full stack

```bash
docker compose up -d
```

Wait for all services healthy (90–180s cold start on first run):
```bash
docker compose ps
```

Expected: all entries show `Status=healthy` or `(healthy)`. The api-gateway waits for
5 services to be healthy before starting (T-04-03-03 mitigation).

If any service is stuck, check logs:
```bash
docker compose logs --tail=40 <service-name>
```

---

## Step 2 — Product listing smoke

List products (unauthenticated — catalog GET is public):
```bash
curl -fsS http://localhost:8080/api/v1/products | python3 -m json.tool | head -30
```

Expected: JSON with `content` array and `totalElements` >= 50 (seed data from plan 04-01 V3 migration).

Turkish ILIKE search (PROD-04 / LOC-01 — case-insensitive Turkish collation):
```bash
curl -fsS "http://localhost:8080/api/v1/products?q=Telefon" | python3 -m json.tool | head -20
```

Expected: Returns products with "Telefon" in `nameTr`. May be empty if seed has no matching names;
that is acceptable — the absence of a 500 error proves the ILIKE query itself works.

Manual Turkish dotted-I sanity check (PROD-04 / LOC-01):
```bash
curl -fsS "http://localhost:8080/api/v1/products?q=Iphone" | python3 -m json.tool | head -10
curl -fsS "http://localhost:8080/api/v1/products?q=iPhone" | python3 -m json.tool | head -10
```

Expected: Both return equivalent result sets (collation handles `İ`/`i`/`I`/`ı` variants).

---

## Step 3 — Category listing smoke

```bash
curl -fsS http://localhost:8080/api/v1/categories | python3 -m json.tool
```

Expected: JSON array of 8 top-level categories:
- Elektronik
- Moda
- Ev & Yasam
- Anne & Bebek
- Kozmetik
- Spor & Outdoor
- Supermarket
- Kitap-Muzik-Film-Oyun

---

## Step 4 — Product detail smoke (PDP)

Grab a product ID from the Step 2 response, then:
```bash
PRODUCT_ID=<uuid from Step 2 content[0].id>
curl -fsS http://localhost:8080/api/v1/products/$PRODUCT_ID | python3 -m json.tool
```

Expected: JSON object with fields:
- `id` (UUID)
- `nameTr` (Turkish product name)
- `priceGross` (decimal)
- `kdvRate` (8 or 18)
- `imageUrls` (array, may be empty in seed data)
- `categoryId` (UUID)
- `sellerName` (string)

---

## Step 5 — Inventory smoke

Using the same PRODUCT_ID from Step 4:
```bash
curl -fsS http://localhost:8080/api/v1/inventory/$PRODUCT_ID | python3 -m json.tool
```

Expected: JSON object with:
- `productId` (UUID — matches PRODUCT_ID)
- `availableQty` (non-negative integer)
- `stockState` (one of: `STOKTA`, `SON_URUN`, `TUKENDI`)
- `stockStateLabel` (Turkish: one of `"Stokta"`, `"Son N ürün!"`, `"Tükendi"`)

---

## Step 6 — Springdoc aggregator smoke

Open in browser: http://localhost:8080/swagger-ui.html

Checklist:
- [ ] Page loads without 404 or blank screen
- [ ] Dropdown top-right shows at minimum: `identity-service`, `product-service`, `inventory-service`
- [ ] Selecting `product-service` loads endpoints (`/products`, `/categories`)
- [ ] Selecting `inventory-service` loads endpoint (`/inventory/{productId}`)

CLI alternative (verify aggregator config JSON):
```bash
curl -fsS http://localhost:8080/v3/api-docs/swagger-config | python3 -m json.tool
```

Expected: JSON with `urls` array containing entries for `identity-service`, `product-service`, `inventory-service`.

---

## Step 7 — Saga consumer smoke (publish synthetic order.created)

Verify that inventory-service consumes an `order.created` event, reserves stock, and writes a
`stock_reservations` row and an `outbox` row.

**Replace `<PRODUCT_ID>` below with the UUID from Step 4.**

### Option A — via RabbitMQ management UI (http://localhost:15672)

Login: `RABBITMQ_DEFAULT_USER` / `RABBITMQ_DEFAULT_PASS` from `.env`.

1. Click **Exchanges** → `orders.tx` → **Publish message**
2. Routing key: `order.created`
3. Properties: `content_type=application/json`
4. Payload (replace `<PRODUCT_ID>`):
```json
{
  "eventId": "00000000-0000-0000-0000-000000000001",
  "eventType": "order.created",
  "eventVersion": 1,
  "occurredAt": "2026-04-29T12:00:00Z",
  "correlationId": "00000000-0000-0000-0000-000000000002",
  "causationId": "00000000-0000-0000-0000-000000000001",
  "producer": "smoke-test",
  "payload": {
    "orderId": "00000000-0000-0000-0000-000000000003",
    "userId": "00000000-0000-0000-0000-000000000004",
    "currency": "TRY",
    "totalAmount": "100.00",
    "items": [{"productId": "<PRODUCT_ID>", "qty": 1, "unitPrice": "100.00", "nameSnapshot": "Smoke Test"}]
  }
}
```
5. Click **Publish**.

### Option B — via rabbitmqadmin CLI

```bash
RABBIT_USER=$(grep RABBITMQ_DEFAULT_USER .env | cut -d= -f2)
RABBIT_PASS=$(grep RABBITMQ_DEFAULT_PASS .env | cut -d= -f2)

docker exec n11-rabbitmq rabbitmqadmin \
  --username="$RABBIT_USER" --password="$RABBIT_PASS" \
  publish exchange=orders.tx routing_key=order.created \
  payload='{"eventId":"00000000-0000-0000-0000-000000000001","eventType":"order.created","eventVersion":1,"occurredAt":"2026-04-29T12:00:00Z","correlationId":"00000000-0000-0000-0000-000000000002","causationId":"00000000-0000-0000-0000-000000000001","producer":"smoke-test","payload":{"orderId":"00000000-0000-0000-0000-000000000003","userId":"00000000-0000-0000-0000-000000000004","currency":"TRY","totalAmount":"100.00","items":[{"productId":"<PRODUCT_ID>","qty":1,"unitPrice":"100.00","nameSnapshot":"Smoke Test"}]}}'
```

### Verify the reservation was written (wait 10 seconds first):

```bash
docker exec -it n11-postgres psql -U inventory_user -d n11 -c \
  "SELECT order_id, product_id, qty, status FROM inventory.stock_reservations WHERE order_id = '00000000-0000-0000-0000-000000000003';"
```

Expected: 1 row with `status = 'RESERVED'`.

Verify outbox event was created:
```bash
docker exec -it n11-postgres psql -U inventory_user -d n11 -c \
  "SELECT event_type, aggregate, sent_at FROM inventory.outbox ORDER BY occurred_at DESC LIMIT 3;"
```

Expected: Row with `event_type = 'stock.reserved'` (or `stock.reserve_failed` if PRODUCT_ID had 0 stock).
`sent_at` may be NULL until the OutboxPoller runs (fixedDelay=5000ms); wait 10s and retry.

---

## Step 8 — Idempotency re-delivery check

Publish the SAME event again (same `eventId`: `00000000-0000-0000-0000-000000000001`):

```bash
# Re-run the same rabbitmqadmin command from Step 7 Option B
# Or re-publish via management UI with the identical payload
```

Wait 10 seconds, then verify idempotency (CLAUDE.md Rule #3):
```bash
docker exec -it n11-postgres psql -U inventory_user -d n11 -c \
  "SELECT count(*) FROM inventory.processed_events WHERE event_id = '00000000-0000-0000-0000-000000000001';"
```

Expected: `count = 1` — the duplicate event was detected and skipped. A count > 1 indicates a
broken idempotency inbox.

Also verify no double-reservation:
```bash
docker exec -it n11-postgres psql -U inventory_user -d n11 -c \
  "SELECT count(*) FROM inventory.stock_reservations WHERE order_id = '00000000-0000-0000-0000-000000000003';"
```

Expected: `count = 1`.

---

## Step 9 — Teardown

```bash
docker compose down
```

To also wipe volumes (full reset — next boot runs Flyway migrations from scratch):
```bash
docker compose down -v
```

---

## Sign-off Checklist

- [ ] Step 1: All services healthy (`docker compose ps` shows all healthy)
- [ ] Step 2: Product listing returns `totalElements` >= 50 (seed data)
- [ ] Step 3: 8 categories present in `/api/v1/categories` response
- [ ] Step 4: PDP (`/products/{id}`) returns `id`, `nameTr`, `priceGross`, `kdvRate`, `categoryId`, `sellerName`
- [ ] Step 5: Inventory (`/inventory/{productId}`) returns `stockStateLabel` in `{"Stokta", "Tükendi", "Son N ürün!"}`
- [ ] Step 6: Springdoc aggregator dropdown at `/swagger-ui.html` lists `product-service` and `inventory-service`
- [ ] Step 7: `stock_reservations` row with `status = 'RESERVED'` appears after synthetic `order.created` publish
- [ ] Step 8: `processed_events count = 1` after duplicate event re-publish (idempotency proof)

All 8 checklist items must be checked before Phase 4 is considered complete.
