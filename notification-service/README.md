# notification-service

> **Phase 7** — Saga Leaf Consumer

Fully independent saga leaf consumer — consumes order lifecycle and user registration events, logs structured Turkish "email payloads" to the notifications audit table. Mock email/SMS (log-only for demo, no real SMTP/Twilio).

## Saga Events Consumed

| Exchange | Routing Key | Queue | Purpose |
|----------|-------------|-------|---------|
| orders.tx | order.confirmed | notify.q.order-confirmed | "Sipariş Onaylandı" email |
| orders.tx | order.cancelled | notify.q.order-cancelled | "Sipariş İptal Edildi" email |
| payments.tx | payment.failed | notify.q.payment-failed | "Ödeme Başarısız" email |
| identity.tx | user.registered | notify.q.user-registered | "Hoş Geldiniz" welcome email |

Each consumer has a dedicated DLQ (messages rejected after 3 retries).

## How It Works

1. Event arrives on RabbitMQ queue
2. `@RabbitListener` method validates and routes to `NotificationService`
3. `NotificationService` (`@Transactional`):
   - Checks `processed_events` inbox for idempotency
   - Renders Turkish email template from `NotificationTemplates`
   - Persists to `notifications` audit table
   - Logs structured JSON "email payload" (recipient, subject, body)
4. No real email is sent — the log line IS the deliverable for the demo

## Turkish Email Templates

| Event | Subject | Body excerpt |
|-------|---------|-------------|
| order.confirmed | Sipariş Onaylandı | "Siparişiniz #{orderId} onaylandı..." |
| order.cancelled | Sipariş İptal Edildi | "Siparişiniz #{orderId} iptal edildi..." |
| payment.failed | Ödeme Başarısız | "Ödeme işleminiz başarısız oldu..." |
| user.registered | Hoş Geldiniz | "n11'e hoş geldiniz! Hesabınız oluşturuldu..." |

## Required Environment Variables

| Variable | Description |
|----------|-------------|
| `NOTIFICATION_DB_PASSWORD` | PostgreSQL password for `notification_user` |
| `RABBITMQ_DEFAULT_USER` | RabbitMQ username |
| `RABBITMQ_DEFAULT_PASS` | RabbitMQ password |

## Build & Run

```bash
./gradlew :notification-service:jibDockerBuild
docker compose up -d notification-service
```

## Tests

```bash
./gradlew :notification-service:test
```

| Test | Purpose |
|------|---------|
| Idempotency tests (x4) | Duplicate event delivery produces exactly 1 processed_events row |
| NotificationServiceLogTest | Turkish template rendering |
| ConsumerDlqRoutingTest | Poison messages land on DLQ within 3 retries |
