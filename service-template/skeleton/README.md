# service-template/skeleton/

Copy-paste source tree for scaffolding a new business service. Used by Phase 3+ executors.

## Why a `.template`-suffixed mirror instead of plain source?

- The `.template` suffix prevents the skeleton files from being picked up by the runnable `service-template` subproject's compile classpath (the runnable subproject reads from `service-template/src/main/`, NOT `service-template/skeleton/src-main/`).
- The runnable subproject (under `src/main/`) and the skeleton tree are kept in sync MANUALLY. Any change to one must be applied to the other; Plan 01-07 Task 7's drift verification catches stale mirrors at the end of Phase 1.
- See `01-07-PLAN.md` `<objective>` "CD-02 Hybrid Decision Rationale" for the design trade-off.

## Placeholder tokens

| Token | Meaning | Example replacement |
|-------|---------|---------------------|
| `<SERVICE_NAME>` | Spring application name (kebab-case) | `identity-service`, `product-service`, `cart-service` |
| `__SERVICE_PACKAGE__` | Java package leaf (lowercase, no hyphens) | `identity`, `product`, `cart` |

## Clone procedure (Phase 3+ executors run these)

```bash
SERVICE_NAME=identity-service          # kebab-case Spring app name
SERVICE_PACKAGE=identity               # Java package leaf

# 1. Copy skeleton tree into a new sibling subproject directory.
mkdir -p ${SERVICE_NAME}/src
cp -r service-template/skeleton/src-main ${SERVICE_NAME}/src/main

# 2. Rename the placeholder package directory.
mv ${SERVICE_NAME}/src/main/java/com/n11/__SERVICE_PACKAGE__ \
   ${SERVICE_NAME}/src/main/java/com/n11/${SERVICE_PACKAGE}

# 3. Drop the .template suffix from every file.
find ${SERVICE_NAME}/src/main -type f -name '*.template' | while read f; do
  mv "$f" "${f%.template}"
done

# 4. Substitute placeholder tokens in file contents.
find ${SERVICE_NAME}/src/main -type f \( -name '*.java' -o -name '*.yml' -o -name '*.xml' -o -name '*.sql' \) -exec \
  sed -i \
    -e "s/<SERVICE_NAME>/${SERVICE_NAME}/g" \
    -e "s/__SERVICE_PACKAGE__/${SERVICE_PACKAGE}/g" \
  {} \;

# 5. Add the new subproject to settings.gradle.kts (root):
#    include("${SERVICE_NAME}")

# 6. Author ${SERVICE_NAME}/build.gradle.kts by copying service-template/build.gradle.kts and
#    pruning/adding dependencies as needed for this service's domain.

# 7. Author config-server/src/main/resources/config/${SERVICE_NAME}.yml by copying
#    service-template.yml and overriding:
#       - server.port: <unique port>
#       - flyway.schema: ${SERVICE_PACKAGE}        (or the matching schema name from 01-03 init.sh)
#       - spring.datasource.username/password: env-var refs to ${SERVICE_PACKAGE}_user

# 8. Author ${SERVICE_NAME}/src/main/resources/db/migration/V2__<feature>.sql for the service's
#    domain entities. V1__init_processed_events.sql comes from the skeleton untouched.
```

## Production posture: strip `optional:` from `spring.config.import`

The skeleton's `application.yml.template` keeps `optional:` in `spring.config.import` so each service's own build runs in CI without a config-server. **Each cloned service should strip `optional:` from its `application.yml` for production posture** — without it, an absent config-server fails the boot loud (which is what ARCH-11 wants in production), but lets the service still build offline (CI) because `optional:` only matters at runtime resolution.

```yaml
# CI-friendly (skeleton default):
import: optional:configserver:http://config-server:8888?...

# Production posture (after clone):
import: configserver:http://config-server:8888?...
```

## What the skeleton ships with

- `ServiceApplication.java.template` — `@SpringBootApplication(scanBasePackages = "com.n11")` + `@EnableDiscoveryClient`
- `health/SampleHealthController.java.template` — `/sample` smoke endpoint
- `application.yml.template` — minimal `spring.config.import` to config-server (Cross-Cutting #2)
- `logback-spring.xml.template` — JSON encoder with `correlationId` MDC key (Cross-Cutting #4)
- `db/migration/V1__init_processed_events.sql.template` — saga consumer idempotency inbox (D-11.4)

What the skeleton does NOT ship (per-service):

- `build.gradle.kts` — copy from `service-template/build.gradle.kts` and adapt
- `<svc>-service.yml` config-repo overlay — copy from `service-template.yml` and override per-service keys

## Drift policy

Any change to `service-template/src/main/...` MUST be re-applied to `service-template/skeleton/src-main/...` (modulo placeholder substitutions). Plan 01-07 Task 7 documents the diff procedure. If a future Phase-11 cleanup wants to programmatically generate one from the other, that becomes a backlog item.
