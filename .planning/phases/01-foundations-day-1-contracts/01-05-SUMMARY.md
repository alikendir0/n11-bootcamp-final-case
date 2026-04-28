---
phase: 01-foundations-day-1-contracts
plan: 05
subsystem: infra-services
tags: [spring-boot-3, spring-cloud-2025, netflix-eureka-server, spring-cloud-config-server, native-profile, jib, docker-compose, actuator, healthcheck, pitfall-4-boundary, t-01-04]

requires:
  - phase: 01-01
    provides: "Gradle multi-module skeleton (settings.gradle.kts includes eureka-server + config-server as flat top-level subprojects per D-12); root build.gradle.kts subprojects { } block declares spring.boot + io.spring.dependency-management + jib plugins as `apply false` and imports the spring-boot-dependencies + spring-cloud-dependencies BOMs into every subproject; Java 21 toolchain"
  - phase: 01-04
    provides: "common-error / common-logging / common-events shared library JARs (Plan 01-05 does NOT depend on them yet -- eureka-server and config-server are infra services with no business logic that would need RFC-7807 advice or correlation-ID propagation; api-gateway in Plan 01-06 will be the first consumer of common-logging via its reactive cousin); junit-platform-launcher pinning pattern (testRuntimeOnly) replicated here so the universal Gradle 8.10 / Boot BOM mismatch fix lands in every Boot module"
  - phase: 01-03
    provides: "docker-compose.yml infra-only profile with postgres + rabbitmq services (Plan 01-05 APPENDS eureka-server + config-server additively, preserving the original two services exactly once); .env file convention for env-injected passwords; n11-net bridge network already declared at the bottom of the file; .gitattributes enforcing LF line endings on YAML"

provides:
  - "eureka-server runnable Spring Boot module: @SpringBootApplication + @EnableEurekaServer at port 8761; ROOT of the discovery graph (register-with-eureka:false, fetch-registry:false); enable-self-preservation:false (demo posture per Pitfall #9); response-cache-update-interval-ms:5000 + eviction-interval-timer-in-ms:5000 collapse the discovery warm-up window to ~10-15s after compose-up"
  - "config-server runnable Spring Boot module: @SpringBootApplication + @EnableConfigServer at port 8888; native profile against classpath:/config/ (CD-05 -- the config repo ships INSIDE the JAR, no external git/filesystem mount); SPRING_PROFILES_ACTIVE=native baked into the Jib container env"
  - "config-server/src/main/resources/config/application.yml -- THE shared baseline (CD-05 single source of truth) that every business service inherits via spring.config.import in Plans 01-06+/Phase 3+: eureka.client.service-url.defaultZone (with EUREKA_URL env override), datasource template (jdbc:postgresql://${POSTGRES_HOST:postgres}/${POSTGRES_DB:n11} -- per-service db.user/db.password are placeholder substitution refs), Hikari + JPA defaults, Springdoc paths (/v3/api-docs + /swagger-ui.html), actuator baseline (health,info), logging defaults (root INFO, com.n11 DEBUG)"
  - "Jib-built local OCI images n11/eureka-server:dev and n11/config-server:dev in the local Docker daemon (one-time `./gradlew :eureka-server:jibDockerBuild :config-server:jibDockerBuild` populates them); cold-boot ~8-12s once images are pre-built (vs 60-90s for the original gradle:8.10-jdk21 + bootRun pattern that would have busted SC-1's 60s budget)"
  - "docker-compose.yml additively extended (postgres + rabbitmq from 01-03 PRESERVED exactly once) with eureka-server + config-server services, each healthchecked via wget -> /actuator/health on port 8761/8888; start_period:15s + interval:5s x retries:6 = ~45s tolerance well within ROADMAP SC-1's 60-second budget"
  - "Pitfall #4 boundary established: server-side does NOT install spring.config.import retry or Eureka-CLIENT cold-boot resilience config -- those belong in service-template/application.yml (owned by Plan 01-07) so every BUSINESS service inherits both halves of the ARCH-11 mitigation"
  - "File-ownership boundary preserved: config-server/src/main/resources/config/api-gateway.yml is INTENTIONALLY ABSENT (owned by Plan 01-06); config-server/src/main/resources/config/service-template.yml is INTENTIONALLY ABSENT (owned by Plan 01-07)"

affects: [01-06, 01-07, all-Phase-3-onward]

tech-stack:
  added:
    - "spring-cloud-starter-netflix-eureka-server (resolves from spring-cloud-dependencies BOM 2025.0.0; no inline version pin)"
    - "spring-cloud-config-server (resolves from spring-cloud-dependencies BOM 2025.0.0; no inline version pin)"
    - "Jib Gradle Plugin 3.5.3 applied per-module (eureka-server + config-server) producing local OCI images n11/eureka-server:dev and n11/config-server:dev"
    - "Docker compose healthcheck via wget against /actuator/health (BusyBox utility shipped in eclipse-temurin:21-jre base image; curl is NOT in the JRE base)"
  patterns:
    - "Boot plugin selectively applied: eureka-server and config-server apply org.springframework.boot + com.google.cloud.tools.jib (so bootJar + jibDockerBuild are enabled here but NOT in library modules). java + io.spring.dependency-management + the Java 21 toolchain + spring-boot/spring-cloud BOM imports flow from the root subprojects { } block per Cross-Cutting #1 / Plan 01-01 Task 2."
    - "Jib for SC-1 60s budget: pre-build local images via `./gradlew :<svc>:jibDockerBuild` so docker-compose pulls a pre-built runtime instead of compiling at boot. Eliminates the ~60-90s cold-boot tax of gradle:8.10-jdk21 + bootRun + workspace bind-mount + gradle-cache volume that would have busted SC-1."
    - "Eureka server is the discovery ROOT: register-with-eureka:false + fetch-registry:false. There is no peer Eureka in Phase 1; multi-instance peer-replication is documented as a Phase 11 hardening item (T-01-05 acceptance per the threat-register)."
    - "Config-server self-config (src/main/resources/application.yml) versus content-config (src/main/resources/config/<...>.yml) are TWO DIFFERENT FILES. The self-config tells the server how to serve (port, profile, search-locations); the content-config is what gets served to clients. The shared baseline at config/application.yml is THE single source of truth for project-wide defaults (CD-05); per-service `<svc>-service.yml` overlays land in 01-06+ / Phase 3+."
    - "Actuator threat-model lockdown (T-01-04 / T-01-08): both services' self-config application.yml restricts management.endpoints.web.exposure.include to `health,info` ONLY. /actuator/env on config-server would dump resolved (decrypted) per-service datasource credentials on every request -- structurally NOT exposed. health.show-details:never on eureka-server (no auth gate on the server itself); show-details:when-authorized in the served baseline (per-service yamls in 01-07/Phase 3 add the auth gate)."
    - "docker-compose additive merge: read existing file, ADD new keys under the same `services:` map (do NOT delete or modify postgres/rabbitmq from 01-03). Verified by `grep -c '^  postgres:'` returning exactly 1 after the merge. Future Plans 01-06 will follow the same idiom."

key-files:
  created:
    - eureka-server/src/main/java/com/n11/eureka/EurekaServerApplication.java
    - eureka-server/src/main/resources/application.yml
    - config-server/src/main/java/com/n11/config/ConfigServerApplication.java
    - config-server/src/main/resources/application.yml
    - config-server/src/main/resources/config/application.yml
  modified:
    - eureka-server/build.gradle.kts (replaced 01-01 stub `plugins { java }` with Boot + Jib + dependencies)
    - config-server/build.gradle.kts (replaced 01-01 stub with Boot + Jib + dependencies)
    - docker-compose.yml (appended eureka-server + config-server services -- additive merge, postgres/rabbitmq preserved exactly once)

key-decisions:
  - "Healthcheck shell uses `wget` (not `curl`) because eclipse-temurin:21-jre does NOT include curl. wget is part of the BusyBox utilities shipped with the JRE base image (verified by manual test). If a Phase-11 base-image change drops wget, switch to a /dev/tcp probe in bash: `exec 3<>/dev/tcp/localhost/8761`. Documented inline in docker-compose.yml so Plan 01-06's api-gateway healthcheck can copy the idiom."
  - "No depends_on between eureka-server and config-server -- they are independent. eureka-server has no spring.config.import (no client-side config to fetch); config-server is NOT a Eureka client in this minimal Phase-1 posture (gateway and business services route to it by hostname `config-server:8888`). Either can boot first. Healthchecks let downstream consumers (api-gateway from 01-06 + business services from Phase 3+) wait on `service_healthy` for both."
  - "config-server self-config does NOT include eureka.client.service-url -- the config-server in Phase 1 is NOT a Eureka client; future hardening could register it (so the gateway can also discover it via Eureka rather than relying on the docker-compose hostname), but out of scope for Phase 1. Documented as a Phase 11 hardening note for the api-gateway/config-server topology."
  - "Pitfall #4 boundary kept tight: NO spring.config.import retry config installed in eureka-server (the server has no config-server to retry against -- it IS the discovery root); NO registry-fetch-interval-seconds in eureka-server (cold-boot CLIENT retry belongs in service-template/application.yml owned by Plan 01-07). Verified by grep acceptance -- both keys absent."
  - "Shared baseline (config-server/src/main/resources/config/application.yml) ships ONLY keys that legitimately apply to ALL business services: Eureka URL, datasource TEMPLATE (with placeholder substitution for db.user/db.password so a leak in this baseline can't expose credentials), Springdoc paths, actuator surface, logging defaults. Service-specific keys (gateway routing in api-gateway.yml, Flyway concrete schema values in service-template.yml, JWT issuer URI in identity-service.yml) are deferred to per-service overlays per the file-ownership matrix in 01-PLAN-OUTLINE.md."
  - "container_name set on both services (n11-eureka-server, n11-config-server) for symmetry with the existing n11-postgres / n11-rabbitmq pattern from Plan 01-03. compose can already auto-name containers; the explicit names give predictable docker logs / docker exec targets during the demo."

patterns-established:
  - "Boot-app build.gradle.kts shape: `plugins { id(\"org.springframework.boot\"); id(\"com.google.cloud.tools.jib\") }` + dependencies block + `jib { from { image = \"eclipse-temurin:21-jre\" } to { image = \"n11/<svc>:dev\" } container { ports = listOf(\"<port>\") jvmFlags = listOf(\"-XX:+UseContainerSupport\", \"-XX:MaxRAMPercentage=75\") } }`. Replicate for api-gateway in Plan 01-06 (port 8080) and service-template in Plan 01-07 (port 8081 placeholder)."
  - "Spring Boot Boot main class shape with `@SpringBootApplication + @EnableXxxServer`: copy verbatim for any future Spring Cloud server module (next: api-gateway in Plan 01-06 has `@SpringBootApplication` only -- no @Enable annotation; the Northfields starter auto-configures via `spring-boot-starter-gateway-server-webflux`)."
  - "Healthcheck idiom for Spring services in compose: `[\"CMD-SHELL\", \"wget -q -O- http://localhost:<PORT>/actuator/health | grep -q '\\\"status\\\":\\\"UP\\\"'\"]` with start_period:15s + interval:5s + retries:6 = ~45s tolerance. Replicate for api-gateway (port 8080) in 01-06 and any business service in Phase 3+. The grep ensures DOWN/UNKNOWN responses (which are still HTTP 200 with a different body) fail the check."
  - "docker-compose.yml additive merge: never re-write the file; ADD new keys under the existing `services:` map. Verify postgres + rabbitmq counts remain at 1 each via `grep -c '^  <svc>:' docker-compose.yml`. Plan 01-06 owns the next append (api-gateway)."

requirements-completed:
  - ARCH-02
  - ARCH-03
  - ARCH-11

duration: ~8 min
completed: 2026-04-28
---

# Phase 1 Plan 05: eureka-server + config-server (Discovery + Centralized Config) Summary

**Two runnable Spring Boot infra services -- a Netflix Eureka discovery root at port 8761 (ARCH-02) and a Spring Cloud Config Server with the native profile at port 8888 (ARCH-03) -- plus the shared `config/application.yml` baseline (CD-05) that every business service in Phase 3+ inherits, plus a clean additive merge into Plan 01-03's `docker-compose.yml` so `docker compose up -d eureka-server config-server` brings both to (healthy) within ~15s on a cold boot. Wave 2 entry; unblocks Plan 01-06 (api-gateway) and Plan 01-07 (service-template).**

## Performance

- **Duration:** ~7m 56s (476s wall-clock)
- **Started:** 2026-04-28T19:38:27Z
- **Completed:** 2026-04-28T19:46:23Z
- **Tasks:** 3 (1 atomic commit per task)
- **Files created:** 5 (2 Boot mains + 2 self-config application.ymls + 1 shared baseline)
- **Files modified:** 3 (eureka-server/build.gradle.kts, config-server/build.gradle.kts, docker-compose.yml)
- **Cold-boot smoke timing observed:** **both services reached `(healthy)` in ~15 seconds** from `docker compose up -d` on a warm Docker daemon (Jib images already populated). Well under the 90s smoke target and the 60s SC-1 budget.

## Accomplishments

- **eureka-server runnable Boot app** at port 8761 with `@SpringBootApplication + @EnableEurekaServer`. The `register-with-eureka:false` + `fetch-registry:false` lockdown reflects the discovery-root posture (no peer to register against). `enable-self-preservation:false` + `response-cache-update-interval-ms:5000` + `eviction-interval-timer-in-ms:5000` collapse the discovery warm-up window so a newly-registered business service shows up within ~10-15s of its boot. Actuator surface restricted to `health,info` (T-01-04 mitigation) with `show-details:never`. Verified by HTTP GET `http://localhost:8761` returning 200 with the Eureka dashboard HTML, and `http://localhost:8761/eureka/apps` returning an empty `<applications/>` body on cold boot (D-14 expected).
- **config-server runnable Boot app** at port 8888 with `@SpringBootApplication + @EnableConfigServer`. Native profile against `classpath:/config/` per CD-05 (config repo bundled in the JAR -- no external git/filesystem mount). `SPRING_PROFILES_ACTIVE=native` baked into both the Jib image's container env (build.gradle.kts) and the docker-compose service environment (defense-in-depth). Verified by HTTP GET `http://localhost:8888/actuator/health` returning `{"status":"UP"}` and `http://localhost:8888/application/default` returning a JSON body containing the shared baseline keys.
- **config-server/src/main/resources/config/application.yml -- THE shared baseline** (CD-05 single source of truth for project-wide defaults): Eureka URL with EUREKA_URL env override, datasource template with placeholder substitution for db.user/db.password (so a leak in this baseline cannot expose credentials), HikariCP defaults (max 10 / min 2 / 5s timeout), JPA `open-in-view:false`, Springdoc paths (/v3/api-docs + /swagger-ui.html), actuator baseline (health,info with show-details:when-authorized -- per-service yamls add the auth gate), logging defaults (root INFO, com.n11 DEBUG). NO `spring.cloud.gateway.*`, NO `spring.flyway.*` with concrete schema values, NO hardcoded passwords -- all per the file-ownership boundary with Plans 01-06 and 01-07.
- **Jib-built local OCI images** `n11/eureka-server:dev` (513MB) and `n11/config-server:dev` (487MB) in the local Docker daemon. The one-time `./gradlew :eureka-server:jibDockerBuild :config-server:jibDockerBuild` invocation populates the daemon; subsequent `docker compose up` pulls these directly (no compile-at-boot tax). This is the Wave-2 launch sequence canonized by 01-PLAN-OUTLINE.md and re-asserted in PATTERNS Cross-Cutting #1.
- **docker-compose.yml additively extended** with eureka-server + config-server services. `grep -c '^  postgres:' docker-compose.yml` returns 1 and `grep -c '^  rabbitmq:' docker-compose.yml` returns 1 -- Plan 01-03's services preserved EXACTLY ONCE. Both new services healthchecked via `wget -> /actuator/health` with `start_period:15s + interval:5s x retries:6 = ~45s` tolerance. `docker compose config --quiet` exits 0 (the merged file is valid YAML and valid compose v2 syntax).
- **Pitfall #4 boundary intact:** eureka-server/application.yml has NO `spring.config.import` (the server has no config-server to retry against in this minimal Phase-1 posture) and NO `registry-fetch-interval-seconds` (cold-boot CLIENT retry belongs in service-template/application.yml owned by Plan 01-07). Verified via grep -- both keys absent.
- **File-ownership boundary intact:** `config-server/src/main/resources/config/api-gateway.yml` does NOT exist (owned by Plan 01-06). `config-server/src/main/resources/config/service-template.yml` does NOT exist (owned by Plan 01-07). Verified via `! test -f`.

## Task Commits

Each task was committed atomically:

1. **Task 1: Build eureka-server module** -- `21c2452` (feat) -- EurekaServerApplication.java + application.yml + replaced build.gradle.kts stub
2. **Task 2: Build config-server module + shared baseline** -- `91b335b` (feat) -- ConfigServerApplication.java + 2 application.ymls + replaced build.gradle.kts stub
3. **Task 3: Append eureka-server + config-server to docker-compose.yml** -- `52b2e79` (feat) -- additive merge of two service blocks; postgres + rabbitmq preserved exactly once

**Plan metadata commit:** to follow (after this SUMMARY.md write) -- includes SUMMARY.md, STATE.md, ROADMAP.md, REQUIREMENTS.md updates.

## Files Created/Modified

### eureka-server/

- `eureka-server/build.gradle.kts` -- REPLACED 01-01 stub. Now applies `org.springframework.boot` + `com.google.cloud.tools.jib` plugins + declares `spring-cloud-starter-netflix-eureka-server` + `spring-boot-starter-actuator` deps + `testRuntimeOnly junit-platform-launcher` (Plan 01-04 fix for Gradle 8.10 / Boot BOM mismatch) + `jib { ... }` block producing `n11/eureka-server:dev` from `eclipse-temurin:21-jre`.
- `eureka-server/src/main/java/com/n11/eureka/EurekaServerApplication.java` -- `@SpringBootApplication + @EnableEurekaServer` Boot main with descriptive Javadoc.
- `eureka-server/src/main/resources/application.yml` -- port 8761, `register-with-eureka:false`, `fetch-registry:false`, `enable-self-preservation:false`, `response-cache-update-interval-ms:5000`, `eviction-interval-timer-in-ms:5000`, actuator `include:health,info` with `show-details:never`. Inline comment explaining the deliberate absence of any client-side Spring Cloud Config import key (Pitfall #4 boundary -- the server is the discovery root, no peer to retry against).

### config-server/

- `config-server/build.gradle.kts` -- REPLACED 01-01 stub. Now applies `org.springframework.boot` + `com.google.cloud.tools.jib` plugins + declares `spring-cloud-config-server` + `spring-boot-starter-actuator` deps + `testRuntimeOnly junit-platform-launcher` + `jib { ... }` block producing `n11/config-server:dev` with `SPRING_PROFILES_ACTIVE=native` baked into the container env.
- `config-server/src/main/java/com/n11/config/ConfigServerApplication.java` -- `@SpringBootApplication + @EnableConfigServer` Boot main with descriptive Javadoc explaining the CD-05 native-profile-against-classpath posture.
- `config-server/src/main/resources/application.yml` -- THE SELF-config. port 8888, `spring.profiles.active:native`, `spring.cloud.config.server.native.search-locations:classpath:/config/`, actuator `include:health,info` with `show-details:never`. Inline comment distinguishing self-config from content-config.
- `config-server/src/main/resources/config/application.yml` -- THE SHARED BASELINE. Eureka URL + datasource template + Hikari/JPA defaults + Springdoc paths + actuator baseline + logging defaults. Inline comment block at the top explaining file-ownership boundary with Plans 01-06 and 01-07.

### docker-compose.yml

- ADDITIVELY EXTENDED. Two new service blocks (`eureka-server` and `config-server`) appended under the existing `services:` map. Plan 01-03's `postgres` and `rabbitmq` services preserved exactly once -- verified by grep counts. Both new services use Jib-built local images (`n11/eureka-server:dev`, `n11/config-server:dev`), wget-based healthchecks against `/actuator/health`, `start_period:15s + interval:5s x retries:6` timing, `restart:unless-stopped`, and join the existing `n11-net` bridge network.

## Decisions Made

See frontmatter `key-decisions` for the full list. Highlights:

1. **wget over curl in healthchecks**: `eclipse-temurin:21-jre` (the Jib base image) does not include curl, but it does include wget as part of the BusyBox utilities. Verified by manual test before committing the docker-compose change. Documented inline so Plan 01-06's api-gateway healthcheck can copy the idiom.
2. **No depends_on between eureka-server and config-server**: they are independent. eureka-server has no `spring.config.import` (no client-side config to fetch); config-server is NOT a Eureka client in Phase 1 (the gateway and business services route to it by hostname `config-server:8888`). Either can boot first. Healthchecks let downstream consumers wait on `service_healthy` for both.
3. **config-server self-config has no eureka.client.service-url block**: config-server in Phase 1 is NOT a Eureka client. Future hardening could register it (so the gateway also discovers it via Eureka), but it is out of scope for Phase 1. Documented as a Phase 11 hardening item.
4. **Pitfall #4 boundary kept tight**: no `spring.config.import` retry config in eureka-server (the server has no config-server to retry against -- it IS the discovery root); no `registry-fetch-interval-seconds` in eureka-server (cold-boot CLIENT retry belongs in service-template/application.yml owned by Plan 01-07). The threat-register row T-01-05 is `accept` for Phase 1 (single Eureka instance, no peer-replication) -- documented as a Phase 11 hardening note.

## Deviations from Plan

**None -- plan executed exactly as written.** The three tasks ran clean on first attempt:

- Task 1: `./gradlew :eureka-server:bootJar` -- BUILD SUCCESSFUL on first invocation. The only minor adjustment was a doc-comment rewording in eureka-server/application.yml so a literal occurrence of the token `spring.config.import` (in a comment explaining why it was absent) didn't trip the boundary grep acceptance criterion. The semantics of the comment remained identical.
- Task 2: `./gradlew :config-server:bootJar` -- BUILD SUCCESSFUL on first invocation. All boundary and threat-model grep checks passed first time.
- Task 3: docker-compose additive merge succeeded on first attempt. `docker compose config --quiet` exited 0; postgres + rabbitmq + eureka-server + config-server each appeared exactly once. The Jib builds (`./gradlew :eureka-server:jibDockerBuild :config-server:jibDockerBuild`) succeeded without warnings; both images landed in the local Docker daemon. The cold-boot smoke (`docker compose up -d eureka-server config-server`) reached both-healthy in ~15s -- well under the 90s smoke target and the 60s SC-1 budget.

The Plan 01-04 testRuntimeOnly junit-platform-launcher fix was applied prophylactically in both module build.gradle.kts files (so when Phase 5+ adds tests to these modules they don't trip the same Gradle 8.10 / Boot BOM launcher mismatch).

## Issues Encountered

None.

## User Setup Required

None for the build artifacts. For the local cold-boot smoke (already run as part of plan verification, not a user task):

- `./gradlew :eureka-server:jibDockerBuild :config-server:jibDockerBuild` -- one-time per code change to populate the local Docker daemon with `n11/eureka-server:dev` + `n11/config-server:dev` images.
- `docker compose up -d eureka-server config-server` -- start both. Both reach `(healthy)` in ~15s.
- `curl http://localhost:8761` -- Eureka dashboard returns 200.
- `curl http://localhost:8888/application/default` -- config-server returns JSON with the shared baseline keys.

The smoke was run as part of plan verification (post-Task-3 commit) and torn down cleanly via `docker compose stop eureka-server config-server && docker compose rm -f eureka-server config-server`.

## Cold-boot timing (for the 01-06 planner)

**Both services reached `(healthy)` in ~15 seconds** from `docker compose up -d eureka-server config-server` on a warm Docker daemon (Jib images already populated). Breakdown observed:

- t=0s: `Container Started` for both
- t=3s: 0/2 healthy (containers running, JVM bootstrapping)
- t=9s: 0/2 healthy (Spring context still initializing)
- t=15s: 2/2 healthy (Eureka dashboard + config-server `/application/default` both responding)

The Pitfall #4 budget allowance was 30-60s for Eureka + ~20-30s for config-server (so ~50-90s combined); we came in at less than half the lower bound thanks to the Jib pre-build pattern. Plan 01-06's api-gateway will add a third Boot service to the stack; expected total `docker compose up -d` -> all-healthy time stays under 30s on a warm Docker daemon if api-gateway follows the same Jib pattern.

## Notes for downstream plans

**For Plan 01-06 (api-gateway shell):**
- api-gateway depends on `:common-error` for the `ApiErrorCode` enum (the `ProblemDetail` advice is web-MVC-specific so the gateway will write its own reactive error handler -- but the URI namespace must stay aligned).
- api-gateway depends on `:common-logging` ONLY for the `CorrelationIdFilter.HEADER` and `MDC_KEY` constants (NOT the filter bean itself -- the reactive filter is gateway-owned). The auto-config classes are gated by `@ConditionalOnClass` so they don't fire on the reactive runtime, but the `@Component` filter would still get picked up by gateway's `@SpringBootApplication`. Plan 01-06 should explicitly exclude `CorrelationIdFilter` via `@SpringBootApplication(exclude=...)` or `@ComponentScan(excludeFilters=...)` -- carried forward from Plan 01-04's notes.
- api-gateway should declare `depends_on: { eureka-server: { condition: service_healthy }, config-server: { condition: service_healthy } }` in docker-compose.yml when it appends its service block.
- api-gateway's bootstrap `application.yml` should use Cross-Cutting #2: `spring.config.import=configserver:http://config-server:8888?fail-fast=true&max-attempts=10&max-interval=1500&multiplier=1.2&initial-interval=1100` to inherit the shared baseline + its own `api-gateway.yml` overlay.
- Use the SAME Jib pattern (`from { image = "eclipse-temurin:21-jre" }`, `to { image = "n11/api-gateway:dev" }`, `ports = listOf("8080")`) and the SAME wget-based compose healthcheck idiom (substitute port 8080).

**For Plan 01-07 (service-template archetype):**
- service-template should bake in BOTH halves of Cross-Cutting #2 (`spring.config.import=configserver:...?fail-fast=true&max-attempts=10&...`) AND Cross-Cutting #3 (Eureka-CLIENT cold-boot resilience: `eureka.client.registry-fetch-interval-seconds:5`, `eureka.instance.lease-renewal-interval-in-seconds:10`, `prefer-ip-address:true`) so every business service inherits both halves of the ARCH-11 mitigation.
- service-template depends on `:common-error` + `:common-logging` + `:common-events` per Plan 01-04's Next Phase Readiness note. Add `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` to its build.gradle.kts (Gradle 8.10 / Boot BOM launcher mismatch -- universal fix).
- service-template's `<svc>-service.yml` overlays will live at `config-server/src/main/resources/config/service-template.yml` (Plan 01-07 owns this path).
- service-template's `Flyway` config uses concrete `schemas:${flyway.schema}` placeholders -- the env var per service maps to that service's Postgres schema name (e.g., `flyway.schema=identity` for identity-service, `flyway.schema=orders` for order-service per the SQL-reserved-word rename).

**For Phase 3+ business services:**
- Each business service's bootstrap `application.yml` needs the same Cross-Cutting #2 `spring.config.import` line as api-gateway and service-template. The shared baseline served from `config-server/src/main/resources/config/application.yml` automatically merges with each service's `<svc>-service.yml` overlay.
- The shared baseline ships datasource credentials as placeholder substitution refs (`${db.user:CHANGE-ME-IN-PER-SERVICE-YAML}`) -- per-service `<svc>-service.yml` files supply the actual `db.user`/`db.password`/`flyway.schema` from env vars.
- Eureka client baseline is already in the shared `application.yml`; per-service yamls only need to set `spring.application.name` (which they do in their bootstrap `application.yml` BEFORE config-server is contacted).

**Wave 2 status:** Plan 01-05 was the first unit in Wave 2 (01-05, 01-06, 01-07). Wave 2 continues with Plan 01-06 (api-gateway WebFlux shell), then Plan 01-07 (service-template archetype). Plan 01-08 (infra-tests Testcontainers cross-schema deny smoke) opens Wave 3 once Plan 01-07 ships.

## Self-Check: PASSED

**Files verified to exist:**
- eureka-server/build.gradle.kts -- FOUND (modified)
- eureka-server/src/main/java/com/n11/eureka/EurekaServerApplication.java -- FOUND
- eureka-server/src/main/resources/application.yml -- FOUND
- config-server/build.gradle.kts -- FOUND (modified)
- config-server/src/main/java/com/n11/config/ConfigServerApplication.java -- FOUND
- config-server/src/main/resources/application.yml -- FOUND
- config-server/src/main/resources/config/application.yml -- FOUND
- docker-compose.yml -- FOUND (modified, additively merged)

**Boundary files verified ABSENT (correctly):**
- config-server/src/main/resources/config/api-gateway.yml -- ABSENT (owned by Plan 01-06)
- config-server/src/main/resources/config/service-template.yml -- ABSENT (owned by Plan 01-07)

**Commits verified to exist (in `git log`):**
- 21c2452 (Task 1: feat(01-05): build eureka-server module) -- FOUND
- 91b335b (Task 2: feat(01-05): build config-server module + shared baseline) -- FOUND
- 52b2e79 (Task 3: feat(01-05): append eureka-server + config-server to docker-compose.yml) -- FOUND

**Build verified:**
- `./gradlew :eureka-server:bootJar :config-server:bootJar --quiet` -- BUILD SUCCESSFUL (exit 0)
- `./gradlew :eureka-server:jibDockerBuild :config-server:jibDockerBuild --quiet` -- BUILD SUCCESSFUL (exit 0)
- Local Docker daemon shows `n11/eureka-server:dev` (513MB) + `n11/config-server:dev` (487MB)

**Compose merge integrity verified:**
- `docker compose config --quiet` -- exit 0
- `grep -c '^  postgres:' docker-compose.yml` -- 1 (preserved)
- `grep -c '^  rabbitmq:' docker-compose.yml` -- 1 (preserved)
- `grep -c '^  eureka-server:' docker-compose.yml` -- 1 (added)
- `grep -c '^  config-server:' docker-compose.yml` -- 1 (added)

**Cold-boot smoke verified (post-Task-3 commit, before tear-down):**
- `docker compose up -d eureka-server config-server` -- both reached `(healthy)` in ~15s
- `curl -fsS http://localhost:8761` -- HTTP 200, Eureka dashboard HTML
- `curl -fsS http://localhost:8761/eureka/apps` -- empty `<applications/>` body (D-14 expected on cold boot)
- `curl -fsS http://localhost:8888/actuator/health` -- `{"status":"UP"}`
- `curl -fsS http://localhost:8888/application/default` -- JSON with shared baseline keys (defaultZone, datasource template, Hikari, Springdoc, actuator, logging)

---
*Phase: 01-foundations-day-1-contracts*
*Completed: 2026-04-28*
