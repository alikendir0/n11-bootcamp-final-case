# Deferred Items — Phase 06 Payment Iyzico

| ID | Category | Item | Discovered | Status |
|----|----------|------|------------|--------|
| D-06-01 | Test infrastructure | `./gradlew :payment-service:test` starts AMQP infrastructure during `StockReservedConsumerIntegrationTest` and fails without a RabbitMQ test container/credentials (`AmqpAuthenticationException`). Plan-specific tests pass; this appears unrelated to 06-01 SDK/config/schema/contract changes and should be fixed when Phase 6 revisits the stock-reserved consumer flow. | 06-01 overall verification | OPEN |
