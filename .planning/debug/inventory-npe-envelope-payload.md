---
status: resolved
trigger: "inventory-service saga consumer crashes with NullPointerException when consuming order.created messages over real AMQP delivery — envelope.payload() returns null after Jackson deserialization"
created: 2026-04-29T00:00:00Z
updated: 2026-04-29T22:25:00Z
symptoms_prefilled: true
resolution_verified: "Live smoke test against docker compose stack — first publish creates 1 processed_event + 1 stock_reservation (RESERVED) + 1 outbox row (stock.reserved); second publish with same eventId leaves all counts at 1 (idempotency proven over real AMQP path)"
---

## Current Focus
<!-- OVERWRITE on each update - reflects NOW -->

hypothesis: TWO confirmed bugs. (1) StatefulRetryInterceptor messageKeyGenerator throws AmqpException when message_id is not set (smoke runbook missing it) — listener never invoked, count=0. (2) OrderCreatedConsumer catch block line 92-97 throws NPE if payload is null (payload.orderId() in log). (3) AcknowledgeMode.MANUAL with no basicAck() call leaves messages unacked on successful processing.
test: Confirmed via code inspection of RabbitRetryConfig, OrderCreatedConsumer, and smoke runbook
expecting: Fix 1=change AcknowledgeMode to AUTO in RabbitRetryConfig. Fix 2=defensive null check in catch block. Fix 3=add message_id to smoke runbook rabbitmqadmin command.
next_action: Apply the three fixes

## Symptoms
<!-- Written during gathering, then IMMUTABLE -->

expected: One processed_events row and one stock_reservations row after publishing one order.created message
actual: NullPointerException at OrderCreatedConsumer.java:96 — envelope.payload() returns null; processed_events count = 0
errors: "Caused by: java.lang.NullPointerException: Cannot invoke \"...OrderCreatedPayload.orderId()\" because \"payload\" is null at com.n11.inventory.messaging.OrderCreatedConsumer.handleOrderCreated(OrderCreatedConsumer.java:96)"
reproduction: Publish well-formed envelope to orders.tx exchange with routing key order.created via RabbitMQ HTTP API; watch docker logs n11-inventory-service
started: Observed when running full stack (real AMQP delivery); integration test passes because it bypasses AMQP listener container

## Eliminated
<!-- APPEND only - prevents re-investigating -->

## Evidence
<!-- APPEND only - facts discovered -->

- timestamp: 2026-04-29T00:30:00Z
  checked: InventoryRabbitConfig.java
  found: NO Jackson2JsonMessageConverter set on factory — only topology beans (exchanges, queues, bindings). The bug report's hypothesis about converter on the factory is WRONG.
  implication: The message body IS the raw bytes when the listener receives it.

- timestamp: 2026-04-29T00:31:00Z
  checked: RabbitRetryConfig.java
  found: AcknowledgeMode.MANUAL is set. StatefulRetryOperationsInterceptor has a messageKeyGenerator that throws AmqpException when message_id is null/blank.
  implication: (a) If message_id not set on published message, key generator throws BEFORE listener is invoked → processed_events=0. (b) If listener processes without exception, message is never acked → infinite redelivery.

- timestamp: 2026-04-29T00:32:00Z
  checked: 04-03-SMOKE-RUNBOOK.md Step 7 Option B (rabbitmqadmin command)
  found: rabbitmqadmin command does NOT set message_id in properties. No properties flag used at all.
  implication: This is the PRIMARY root cause: key generator throws AmqpException on every delivery → listener never invoked → processed_events=0 → symptom matches.

- timestamp: 2026-04-29T00:33:00Z
  checked: OrderCreatedConsumer.java lines 90-97
  found: Catch block at line 92 catches Exception from service. At line 96, log statement uses payload.orderId() where payload is the local variable. If payload is null (from treeToValue returning null for NullNode), this throws a second NPE that escapes the listener method.
  implication: Secondary bug: defensive NPE in catch block. With StatefulRetry, this NPE causes 3 retries then DLQ.

- timestamp: 2026-04-29T00:34:00Z
  checked: RejectAndDontRequeueRecoverer.recover bytecode
  found: The recoverer does NOT call channel.basicNack() — it THROWS ListenerExecutionFailedException(AmqpRejectAndDontRequeueException). This means it works correctly with AcknowledgeMode.AUTO (container sees the exception, nacks to DLQ). AcknowledgeMode.AUTO is safe to use.
  implication: Changing AcknowledgeMode from MANUAL to AUTO is the correct fix for the unacked message problem.

## Resolution
<!-- OVERWRITE as understanding evolves -->

root_cause: |
  Two compounding bugs caused the "count(*) FROM inventory.processed_events = 0" symptom and NPE logs:

  BUG 1 (PRIMARY — count=0): The smoke runbook's rabbitmqadmin command did not set the AMQP
  `message_id` property. The StatefulRetryInterceptor's messageKeyGenerator in RabbitRetryConfig
  throws AmqpException when message_id is null/blank. This exception fires BEFORE the listener
  method is invoked, causing every published message to be rejected immediately. The listener
  code (OrderCreatedConsumer.handleOrderCreated) was NEVER reached. Hence processed_events=0.

  BUG 2 (SECONDARY — NPE): OrderCreatedConsumer.handleOrderCreated had AcknowledgeMode.MANUAL
  (via RabbitRetryConfig) but NO channel.basicAck() call in the listener. This meant every
  successfully-processed message was left perpetually unacked, causing infinite redelivery on
  next consumer restart/connection reset. Additionally, the catch block at the original line
  92-97 called payload.orderId() where payload could be null (from treeToValue returning null
  for a NullNode envelope.payload()), creating a secondary NPE that would escape the catch
  block and propagate to the retry interceptor.

fix: |
  Fix 1 (RabbitRetryConfig.java): Changed AcknowledgeMode.MANUAL to AcknowledgeMode.AUTO.
  Confirmed that RejectAndDontRequeueRecoverer THROWS (not basicNack) which works correctly
  with AUTO mode — container sees AmqpRejectAndDontRequeueException and nacks no-requeue to DLQ.

  Fix 2 (OrderCreatedConsumer.java): Rewrote the consumer's error handling:
  - Malformed/unrecoverable messages now throw AmqpRejectAndDontRequeueException immediately
    (routes to DLQ without consuming retry slots)
  - Null payload guard added before treeToValue (prevents NPE)
  - Service exceptions propagate (NOT swallowed) so StatefulRetryInterceptor can retry then DLQ
  - Explicit UTF-8 charset on new String(body) for correctness
  - null payload guard AFTER treeToValue (handles NullNode case)

  Fix 3 (04-03-SMOKE-RUNBOOK.md): Added message_id=<eventId> to rabbitmqadmin properties
  and Management UI properties field in Step 7. Added clear warning about why this is required.
  Changed totalAmount/unitPrice from strings to numbers in payload examples.

verification: |
  ./gradlew :common-events:test --no-daemon → BUILD SUCCESSFUL
  ./gradlew :inventory-service:test --no-daemon → BUILD SUCCESSFUL
  All 5 tests in OrderCreatedConsumerIntegrationTest + other test classes pass.
  Log shows "acknowledgeMode=AUTO" in container shutdown messages (confirmed ack mode change).

files_changed:
  - common-events/src/main/java/com/n11/events/RabbitRetryConfig.java
  - inventory-service/src/main/java/com/n11/inventory/messaging/OrderCreatedConsumer.java
  - .planning/phases/04-catalog-inventory/04-03-SMOKE-RUNBOOK.md
