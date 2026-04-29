# 03-06 End-to-End Smoke Test Runbook

**Date:** 2026-04-29
**Stack:** postgres + rabbitmq + eureka-server + config-server + identity-service + api-gateway
**Gateway port:** 8090 (host port 8080 occupied by code-server/AMP_Linux; 8090:8080 mapping used)
**Status:** ALL 6 SMOKE TESTS PASS

---

## Pre-flight Verification

```
docker ps --format "table {{.Names}}\t{{.Status}}"

n11-api-gateway             Up ~1 min
n11-config-server           Up ~3 min (healthy)
n11-identity-service        Up ~20 min (healthy)
n11-rabbitmq                Up ~20 min (healthy)
n11-postgres                Up ~20 min (healthy)
n11-eureka-server           Up ~20 min (healthy)
```

**Note on gateway port:** Host port 8080 was occupied by `code-server` (PID 2417) and `AMP_Linux`
(PID 3472). Gateway container was started on `8090:8080` for smoke testing. In production
`docker compose up`, the api-gateway depends_on policy ensures identity-service is healthy first.

---

## Deviations Found During Smoke

### Deviation 1: Duplicate `spring:` key in api-gateway.yml (Rule 1 - Bug)
- **Found during:** Task 3 (gateway startup failure)
- **Issue:** Task 2 appended a second top-level `spring:` key at the end of the YAML file.
  YAML doesn't allow duplicate keys — config-server returned HTTP 500 when serving api-gateway config.
- **Fix:** Merged the `spring.security.oauth2.resourceserver.jwt` block under the existing `spring:` root key.
- **Commit:** `45b9b9c`

### Deviation 2: Missing explicit identity-service route (Rule 3 - Blocking)
- **Found during:** Task 3 (smoke tests returning 401 on `/api/v1/identity/**` paths)
- **Issue:** Discovery-locator routes services at `/identity-service/**` (service-ID prefix).
  The security allowlist uses `/api/v1/identity/**` (api-contracts.md canonical paths).
  These paths never matched, so all requests including login/register returned 401.
- **Fix:** Added explicit route in api-gateway.yml under `spring.cloud.gateway.server.webflux.routes`:
  ```yaml
  routes:
    - id: identity-service
      uri: lb://IDENTITY-SERVICE
      predicates:
        - Path=/api/v1/identity/**
      filters:
        - StripPrefix=3
  ```
  `StripPrefix=3` strips `/api/v1/identity` → forwards bare path to identity-service.
- **Commit:** `45b9b9c`

---

## Smoke Test 1: JWKS Endpoint

**Command:**
```bash
curl -s http://localhost:8090/api/v1/identity/.well-known/jwks.json
```

**Output:**
```json
{
    "keys": [
        {
            "kty": "RSA",
            "e": "AQAB",
            "use": "sig",
            "kid": "n11-jwt-2026-04",
            "alg": "RS256",
            "n": "0J9nRcfnhPYKe8_zPkp8erjzz3B_p5gnihGJK-X0YDrF8IoLU38QLuA006kSMzxn9R-ZZVsgORwXvkhGG2WFWXzS2gcvKsKK-UjYS0v3YQJ-OxPqQNcqOeylaCSSVHYKPewrn9nryvvUZJ0ccReALJUzEYHK-NwijBhRRvjw-uwyRlnGmhN8D7gdkjk-3kYKnY0ZJ-Ey1DGIpbv9KN4Hczw_LEtpCy9p6HvbOLufaIcq0xRLUkgVk1A_dG5Z9ZY6JIb-phcXCYTJTBVZBCr0JtzmpIEUjSqw7htf-0tGHDWdV3jFNgzIA0xs6oegSRcI0O4H1YYjUg9D1E6zA_HIlQ"
        }
    ]
}
```

**Checks:**
- HTTP 200: YES
- kty=RSA: YES
- alg=RS256: YES
- use=sig: YES
- kid=n11-jwt-2026-04: YES
- has `d` (private exponent): NO (private material stripped by toPublicJWK())

**Result: PASS**

---

## Smoke Test 2: Register (public endpoint)

**Command:**
```bash
curl -s -X POST http://localhost:8090/api/v1/identity/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke@n11-demo.local","password":"SmokeTest1","fullName":"Smoke Tester"}'
```

**Output:**
```json
{
    "accessToken": "eyJraWQiOiJuMTEtand0LTIwMjYtMDQiLCJhbGciOiJSUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": {
        "id": "b280d44f-3c62-4870-a059-c6483f57c98c",
        "email": "smoke@n11-demo.local",
        "fullName": "Smoke Tester",
        "roles": ["ROLE_USER"]
    }
}
```

**JOSE header decode:** `{"kid": "n11-jwt-2026-04", "alg": "RS256"}`

**Checks:**
- HTTP 201: YES
- tokenType=Bearer: YES
- expiresIn=86400 (24h): YES
- roles=[ROLE_USER]: YES
- JWT alg=RS256: YES
- JWT kid=n11-jwt-2026-04: YES

**Result: PASS**

---

## Smoke Test 3: Protected Route Without Token Returns 401

**Command:**
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8090/api/v1/identity/auth/me
```

**Output:** `401`

**Result: PASS** — Gateway JWT chain blocks unauthenticated request (T-3-02 mitigation)

---

## Smoke Test 4: /auth/me With Valid Token

**Command:**
```bash
curl -s http://localhost:8090/api/v1/identity/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

**Output:**
```json
{
    "id": "b280d44f-3c62-4870-a059-c6483f57c98c",
    "email": "smoke@n11-demo.local",
    "fullName": "Smoke Tester",
    "roles": ["ROLE_USER"],
    "createdAt": "2026-04-29T14:18:53.984444Z"
}
```

**Checks:**
- HTTP 200: YES
- email matches register: YES
- roles=[ROLE_USER]: YES
- createdAt present: YES
- Authorization header stripped downstream (verified by unit test GatewayHeaderInjectionFilterTest): YES

**Result: PASS** — JWT round-trip through gateway works; identity-service receives X-User-Id in place of raw Bearer

---

## Smoke Test 5: Address Book CRUD

**POST /addresses:**
```bash
curl -s -X POST http://localhost:8090/api/v1/identity/addresses \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Ev","recipientName":"Smoke Tester","phone":"05551234567",
       "il":"Istanbul","ilce":"Kadikoy","mahalle":"Caferaga",
       "streetLine":"Muhurdar Cad. No:42 D:5","postalCode":"34710","isDefault":true}'
```

**Output (HTTP 201):**
```json
{
    "id": "0609c904-1eaf-43b4-8d3b-78ae47684a2b",
    "title": "Ev",
    "recipientName": "Smoke Tester",
    "phone": "05551234567",
    "il": "Istanbul",
    "ilce": "Kadikoy",
    "mahalle": "Caferaga",
    "streetLine": "Muhurdar Cad. No:42 D:5",
    "postalCode": "34710",
    "isDefault": true,
    "createdAt": "2026-04-29T14:19:14.602854667Z"
}
```

**GET /addresses:**
```bash
curl -s http://localhost:8090/api/v1/identity/addresses \
  -H "Authorization: Bearer $TOKEN"
```

**Output (HTTP 200):** Array with 1 element, title="Ev"

**Checks:**
- POST 201: YES
- isDefault=true: YES
- Turkish address fields present: YES
- GET 200, count=1: YES

**Result: PASS**

---

## Smoke Test 6: Outbox + RabbitMQ

**Outbox table check:**
```sql
SELECT id, aggregate, event_type, sent_at IS NOT NULL AS sent
FROM identity.outbox ORDER BY occurred_at DESC LIMIT 5;
```

**Output:**
```
                  id                  | aggregate |   event_type    | sent
--------------------------------------+-----------+-----------------+------
 7999a520-6f57-4460-a135-c7110f9fc810 | identity  | user.registered | t
(1 row)
```

**RabbitMQ exchange check:**
```bash
docker exec n11-rabbitmq rabbitmqctl list_exchanges | grep identity
```

**Output:** `identity.tx    topic`

**Checks:**
- Outbox row with event_type=user.registered: YES
- sent_at IS NOT NULL (poller drained within 5s): YES
- identity.tx exchange exists in RabbitMQ: YES

**Result: PASS**

---

## Summary

| Test | Description | Expected | Actual | Pass? |
|------|-------------|----------|--------|-------|
| 1 | JWKS endpoint | HTTP 200, RSA key, no private material | HTTP 200, kty=RSA, alg=RS256, no `d` | YES |
| 2 | Register | HTTP 201, Bearer token, 86400s expiry | HTTP 201, tokenType=Bearer, expiresIn=86400 | YES |
| 3 | 401 without token | HTTP 401 on /auth/me | HTTP 401 | YES |
| 4 | /auth/me with token | HTTP 200, UserProfileResponse | HTTP 200, email + roles + createdAt | YES |
| 5 | Address CRUD | POST 201, GET 200 | POST 201 + GET 200 with 1 address | YES |
| 6 | Outbox + RabbitMQ | sent_at NOT NULL, identity.tx exchange | user.registered sent=t, identity.tx topic | YES |

**All 6 smoke tests: PASS**
Phase 3 success criteria 1-6 satisfied.
