# identity-service

First business service in the n11-clone microservices fleet. Issues RS256 JWTs, serves JWKS, manages user signup/login, address book, and publishes the `user.registered` saga event via the transactional outbox.

Phase: 3 — Identity + Gateway Auth.

## Endpoints (api-contracts.md §1)

| Verb | Path | Auth | Purpose |
|------|------|------|---------|
| POST | /auth/register | public | Create user (email + password). AUTH-01. |
| POST | /auth/login | public | Issue 24h RS256 JWT. AUTH-02. |
| GET | /auth/me | gateway-injected X-User-Id | Return user profile. AUTH-03. |
| GET | /addresses | gateway-injected X-User-Id | List user's saved Türkiye addresses. AUTH-08. |
| POST | /addresses | gateway-injected X-User-Id | Add address. AUTH-08. |
| GET | /.well-known/jwks.json | public | JWKS for gateway validation. AUTH-04. |

Identity-service does NOT validate JWTs itself (D-15). All endpoint security is the gateway's job.

## Required environment variables (.env)

| Variable | Source | Purpose |
|----------|--------|---------|
| `IDENTITY_DB_PASSWORD` | random per-host secret | Postgres password for identity_user (created by infra/postgres/init.sh) |
| `JWT_PRIVATE_KEY` | `openssl genrsa 2048` | RS256 private key (PEM PKCS#8). Public key derived at boot. NEVER commit. |
| `JWT_KEY_ID` | author-chosen stable ID | JWK `kid` field (e.g. `n11-jwt-2026-04`). Bump on rotation. |
| `ADMIN_SEED_EMAIL` | author-chosen | Email of the one-time seeded ROLE_ADMIN user. |
| `ADMIN_SEED_PASSWORD_HASH` | `htpasswd -bnBC 10 "" "<plaintext>"` | BCrypt cost-10 hash of the admin password. |

## Operator runbooks

### Generate the JWT RS256 keypair (one-time per deployment)

```bash
# Generate a fresh PEM PKCS#8 RSA private key (always emits CRT form per Risk 4 in 03-RESEARCH.md).
openssl genrsa 2048 > private.pem

# Single-line escape for .env (replace real newlines with literal \n):
awk 'NR>1{printf "\\n"} {printf "%s", $0}' private.pem
# Paste the output as JWT_PRIVATE_KEY="..." in .env (note the surrounding quotes).
```

Verify the .env value loads at boot: identity-service logs `Loaded RSA keypair, kid=n11-jwt-2026-04` (Plan 03-03 wires this log line).

### Generate the admin bcrypt hash (one-time per deployment)

```bash
# On Linux / macOS (htpasswd from apache2-utils / httpd-tools):
htpasswd -bnBC 10 "" "your-admin-password" | tr -d ':\n'
# Output starts with $2y$10$... -- but identity-service's BCryptPasswordEncoder
# accepts $2a/$2b/$2y interchangeably. Paste the value as ADMIN_SEED_PASSWORD_HASH.
```

### Pre-compose Jib build (per Plan 01-05/01-06 pattern)

```bash
./gradlew :identity-service:jibDockerBuild
docker compose up -d identity-service
```

## Pitfall tripwires

- **Pitfall #6** — JWT private key is env-only; `.gitignore` covers `*.pem`; gitleaks runs in CI.
- **Pitfall #14** — identity-service has NO `ports:` mapping in docker-compose.yml; reachable only through the gateway (port 8080).
- **Pitfall #18** — clock skew: gateway uses `JwtTimestampValidator(Duration.ofSeconds(30))`. On WSL2 demos, run `wsl --shutdown` before the demo to reset host clock drift.

## Test pattern

Smoke unit test (D-16, QUAL-02 starter): `./gradlew :identity-service:test --tests "*PasswordEncoderTest"`. Integration tests (Plan 03-04, 03-05): `./gradlew :identity-service:test`.
