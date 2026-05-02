<![CDATA[# common-logging

> **Phase 1** — Correlation-ID Propagation

Shared library module providing end-to-end correlation-ID propagation across HTTP, AMQP, and SLF4J MDC. Enables tracing a single saga across all 13 services with `grep "correlationId":"<uuid>"`.

## Propagation Chain

| Hop | Mechanism | Class |
|-----|-----------|-------|
| **Inbound HTTP** | `OncePerRequestFilter` reads `X-Correlation-Id` header into MDC | `CorrelationIdFilter` |
| **Outbound HTTP** | `ClientHttpRequestInterceptor` reads MDC and adds header to `RestClient` calls | `CorrelationIdInterceptor` |
| **Outbound AMQP** | `MessagePostProcessor` on `RabbitTemplate` reads MDC and adds header to messages | `CorrelationIdMessagePostProcessor` |
| **Inbound AMQP** | `@Around` aspect on `@RabbitListener` reads message header into MDC | Aspect |
| **Logback** | MDC key `correlationId` is included in structured JSON logs | logstash-logback-encoder |

## Usage

The module is auto-discovered via `@ComponentScan("com.n11")`. No explicit configuration needed.

```bash
# Trace a saga across all services
grep '"correlationId":"8f0e1d2a-3b4c-5d6e-7f8a-9b0c1d2e3f4a"' logs/*.log
```

## Note: Reactive Gateway Exclusion

`common-logging` is intentionally **not** imported by `api-gateway` — the servlet-based `CorrelationIdFilter` cannot load on the reactive (Netty) runtime. The gateway has its own `GatewayCorrelationIdFilter` (reactive `WebFilter`) that shares the same `X-Correlation-Id` wire-format header.
]]>
