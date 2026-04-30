# Phase 7 — Notification (Saga Closure) Smoke Runbook

Execute this runbook after all Phase 7 plans (07-01 through 07-05) complete and Jib images are built.
Commands assume working directory = project root.

**This runbook proves NOTIF-01 (4 consumers fire), NOTIF-02 (structured log lines visible per send), and NOTIF-03 (notification-service is a fully independent Spring Boot microservice — Eureka-registered, own Postgres schema). It complements the automated `QualFourSagaNotificationTest` (Plan 07-05) by exercising real user flows on a live docker-compose stack.**

---

## Prerequisites

- Docker + docker compose installed.
- `.env` populated. Phase 7 needs:
  - All Phase 1–6 secrets (existing)
  - `NOTIFICATION_DB_PASSWORD` (already in `.env.example`; default `changeme-notification` is acceptable for smoke)
- 11-service docker-compose stack already proven booting healthy in Phase 5 / Phase 6 smoke runbooks.

---

## Step 0 — Preflight (automated tests)

Run the unit + integration suite:

```bash
./gradlew :notification-service:test :infra-tests:test
```

All tests must pass. Specifically:
- `OrderConfirmedConsumerIdempotencyTest`, `OrderCancelledConsumerIdempotencyTest`,
  `PaymentFailedConsumerIdempotencyTest`, `UserRegisteredConsumerIdempotencyTest`
- `NotificationServiceLogTest`
- `ConsumerDlqRoutingTest`
- `QualFourSagaNotificationTest` (in :infra-tests)

Do NOT proceed if any test is red.

---

## Step 1 — Jib rebuild

Phase 7 introduces a new image (`n11/notification-service:dev`) and may have edited
`config-server/src/main/resources/config/notification-service.yml`. Rebuild:

```bash
./gradlew :config-server:jibDockerBuild :notification-service:jibDockerBuild
```

> Note: config-server's `notification-service.yml` is classpath-bound at image-build time
> (Spring Cloud Config native profile). Editing `config-server/src/main/resources/config/*.yml`
> requires a `jibDockerBuild` before the change is served to running containers.

---

## Step 2 — Stack boot

```bash
docker compose --env-file .env up -d
```

Wait until all services show `(healthy)` in `docker compose ps` (~60–90s on a warm Docker daemon).
Specifically watch for `n11-notification-service ... (healthy)`.

```bash
# Poll until notification-service is healthy
until docker compose ps notification-service | grep -q '(healthy)'; do
  echo "Waiting for notification-service to become healthy..."
  sleep 5
done
echo "notification-service is healthy"
```

If any service is stuck:
```bash
docker compose logs --tail=80 notification-service
```

---

## Step 3 — Eureka registration verification (NOTIF-03)

Open `http://localhost:8761` in a browser. Confirm `NOTIFICATION-SERVICE` row exists with status `UP`.

Or via API:
```bash
curl -s http://localhost:8761/eureka/apps/NOTIFICATION-SERVICE | grep -c '<status>UP</status>'
```
Expected: `>= 1`.

Also verify the actuator health endpoint directly:
```bash
docker exec n11-notification-service wget -q -O- http://localhost:8087/actuator/health
```
Expected: `{"status":"UP"}`.

---

## Step 4 — Database migration verification (NOTIF-03)

```bash
docker exec n11-postgres psql -U notification_user -d n11 -c "\dt notification.*"
```

Expected output lists 3 tables:
- `notification.flyway_schema_history`
- `notification.notifications`
- `notification.processed_events`

Also confirm zero rows in the audit table at the start:
```bash
docker exec n11-postgres psql -U notification_user -d n11 \
  -c "SELECT count(*) FROM notification.notifications;"
```
Expected: `0` (clean stack).

---

## Step 5 — Trigger user.registered (NOTIF-01 + NOTIF-02 — Turkish welcome)

Pick a unique email for a fresh smoke run:
```bash
EMAIL="smoke-$(date +%s)@buyer.example.com"
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"smoke-pass-1234\",\"fullName\":\"Smoke Tester\"}"
```

Wait ~5s for the identity-service outbox poller to publish `user.registered` →
notification-service consumer to fire:
```bash
sleep 5
```

Verify the audit row:
```bash
docker exec n11-postgres psql -U notification_user -d n11 \
  -c "SELECT user_id, event_type, payload_json->>'subject' AS subject, sent_at FROM notification.notifications WHERE event_type='user.registered' ORDER BY sent_at DESC LIMIT 1;"
```
Expected: 1 row, with `subject` (extracted from `payload_json->>'subject'`) equal to `Hoş geldiniz!`.

Verify the structured log line:
```bash
docker compose logs notification-service --no-color | grep "notification.sent" | grep "user.registered" | tail -1
```
Expected: a single INFO line with `recipient=<uuid>`, `subject="Hoş geldiniz!"`, `correlationId=<uuid>`, `eventType=user.registered`, `channel=EMAIL`.

---

## Step 6 — Trigger order.confirmed (NOTIF-01 + NOTIF-02 — Turkish order confirmation)

Re-use the Phase 5/6 happy-path checkout flow (login → add address → add cart item →
POST /orders → wait for saga to reach CONFIRMED). The exact bash sequence is documented
in `.planning/phases/05-cart-order-skeleton/05-05-SMOKE-RUNBOOK.md` Steps 4–8 — copy
those commands; nothing about them changes for Phase 7.

After the order reaches CONFIRMED:
```bash
docker exec n11-postgres psql -U notification_user -d n11 \
  -c "SELECT user_id, event_type, payload_json->>'subject' AS subject, sent_at FROM notification.notifications WHERE event_type='order.confirmed' ORDER BY sent_at DESC LIMIT 1;"
```
Expected: 1 row, with `subject` (extracted from `payload_json->>'subject'`) equal to `Siparişiniz onaylandı`.

---

## Step 7 — Trigger order.cancelled (NOTIF-01 + NOTIF-02 — Turkish cancellation)

Place a second order (re-use the Phase 5/6 checkout flow), then cancel it:

```bash
# Cancel the order using its orderId (captured from the order-placement response)
curl -s -X POST "http://localhost:8080/api/v1/orders/${ORDER_ID}/cancel" \
  -H "Authorization: Bearer $JWT" | python3 -m json.tool
```

Wait ~5s for the saga cancellation flow to propagate:
```bash
sleep 5
```

Verify the audit row:
```bash
docker exec n11-postgres psql -U notification_user -d n11 \
  -c "SELECT user_id, event_type, payload_json->>'subject' AS subject, payload_json->>'bodyTurkish' AS body, sent_at FROM notification.notifications WHERE event_type='order.cancelled' ORDER BY sent_at DESC LIMIT 1;"
```
Expected: 1 row, with `subject` (extracted from `payload_json->>'subject'`) equal to `Siparişiniz iptal edildi`. The `body` (extracted from `payload_json->>'bodyTurkish'`) contains `Sipariş iptal edildi` (Turkish reason mapping for `USER_CANCELLED`).

---

## Step 8 — (Optional) Trigger payment.failed (NOTIF-01 + NOTIF-02)

This step re-uses the Phase 6 sandbox decline path. Skip if Cloudflare Tunnel /
Iyzico sandbox is not currently set up. If exercised:

Follow `.planning/phases/06-payment-iyzico/06-06-SMOKE-RUNBOOK.md` § "Decline path"
(`4111 1111 1111 1129` test card on the hosted Iyzico form). After payment.failed
fires:

```bash
docker exec n11-postgres psql -U notification_user -d n11 \
  -c "SELECT user_id, event_type, payload_json->>'subject' AS subject, sent_at FROM notification.notifications WHERE event_type='payment.failed' ORDER BY sent_at DESC LIMIT 1;"
```
Expected: 1 row, with `subject` (extracted from `payload_json->>'subject'`) equal to `Ödemeniz alınamadı`.

> **Note:** The `user_id` column for `payment.failed` rows contains the `orderId`, not the user's actual UUID. This is a documented schema trade-off — `payment-failed.schema.json` does not include a `userId` field. See Plan 07-03 decision log and `NotificationService.handlePaymentFailed` Javadoc.

---

## Step 9 — CorrelationId cross-service trace (saga-contracts.md §6 + QUAL-06)

Pick a correlationId from any of the above audit rows:
```bash
CID=$(docker exec n11-postgres psql -U notification_user -d n11 -tA \
    -c "SELECT correlation_id FROM notification.notifications ORDER BY sent_at DESC LIMIT 1;")
echo "Tracing correlationId=$CID"
```

Then trace it across all services:
```bash
docker compose logs --no-color | grep -E "correlationId=$CID|\"correlationId\":\"$CID\"" | wc -l
```
Expected: `>= 3` (at minimum: 1 emitter service log + 1 notification-service log + 1 ingress hop).

Also enumerate which services touched it:
```bash
docker compose logs --no-color | grep -E "correlationId=$CID|\"correlationId\":\"$CID\"" \
  | awk -F'|' '{print $1}' | sort -u
```
Expected: at least 2 distinct service prefixes (e.g., `n11-order-service` or `n11-identity-service` AND `n11-notification-service`).

---

## Step 10 — Sign-off table (operator)

Tick each box after the corresponding step lands green:

| Requirement | Step | Result |
|-------------|------|--------|
| NOTIF-03 — Eureka registration | Step 3 | [ ] PASS / [ ] FAIL |
| NOTIF-03 — own schema + migrations | Step 4 | [ ] PASS / [ ] FAIL |
| NOTIF-01 — user.registered consumer | Step 5 | [ ] PASS / [ ] FAIL |
| NOTIF-02 — Turkish welcome log | Step 5 | [ ] PASS / [ ] FAIL |
| NOTIF-01 — order.confirmed consumer | Step 6 | [ ] PASS / [ ] FAIL |
| NOTIF-02 — Turkish confirmation log | Step 6 | [ ] PASS / [ ] FAIL |
| NOTIF-01 — order.cancelled consumer | Step 7 | [ ] PASS / [ ] FAIL |
| NOTIF-02 — Turkish cancellation log | Step 7 | [ ] PASS / [ ] FAIL |
| NOTIF-01 — payment.failed consumer | Step 8 | [ ] PASS / [ ] FAIL / [ ] SKIPPED |
| QUAL-04 — saga happy-path E2E | Step 0 (automated) | [ ] PASS / [ ] FAIL |
| Saga §6 correlationId trace | Step 9 | [ ] PASS / [ ] FAIL |

Phase 7 is signed off when all required rows (everything except optional Step 8) are PASS.

---

## Teardown

```bash
docker compose down
# To wipe volumes for a clean re-run:
# docker compose down -v
```

---

*Phase 7 — Notification (Saga Closure)*
*Smoke runbook authored: 2026-04-30*
