<![CDATA[# common-error

> **Phase 1** — RFC-7807 Error Handling

Shared library module providing `ProblemDetailControllerAdvice` — a `@RestControllerAdvice` that ensures all error responses across all services use `application/problem+json` with a consistent field set.

## Error Response Shape

All errors follow [RFC-7807](https://www.rfc-editor.org/rfc/rfc7807) `application/problem+json`:

```json
{
  "type": "https://n11clone/errors/validation",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields failed validation",
  "instance": "/api/v1/identity/auth/register",
  "correlationId": "8f0e1d2a-3b4c-5d6e-7f8a-9b0c1d2e3f4a",
  "errors": [
    { "field": "email", "message": "must be a valid email address" }
  ]
}
```

## Error Types

| Type | Status | When |
|------|--------|------|
| `validation` | 400 | Bean validation failures |
| `not-found` | 404 | Resource not found |
| `conflict` | 409 | State conflict (duplicate, invalid transition) |
| `unauthorized` | 401 | Missing or invalid authentication |
| `internal` | 500 | Unexpected error (detail is sanitized — no stack traces, no SQL) |

## Imported By

Every business service includes `implementation(project(":common-error"))` — the advice is auto-discovered via `@ComponentScan("com.n11")`.
]]>
