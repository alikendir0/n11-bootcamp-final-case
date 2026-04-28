---
phase: 01-foundations-day-1-contracts
plan: 06
subsystem: api-gateway
tags: [spring-cloud-2025, northfields, gateway-server-webflux, reactive, webflux, global-filter, correlation-id, jwt-deferred, permitall, springdoc-aggregator, sse-forward-ref, jib, docker-compose, t-01-04, t-01-09, t-01-10, t-01-11, t-01-13, pitfall-2-lockdown]

requires:
  - phase: 01-01
    provides: "Gradle multi-module skeleton (settings.gradle.kts includes api-gateway as flat top-level subproject per D-12); root build.gradle.kts subprojects { } block declares spring.boot + io.spring.dependency-management + jib plugins as `apply false` and imports the spring-boot-dependencies + spring-cloud-dependencies BOMs into every subproject; Java 21 toolchain"
  - phase: 01-02
    provides: ".planning/api-contracts.md §gateway-routing — the discovery-locator-driven prefix map per service (api-gateway.yml in 01-06 implements this via spring.cloud.gateway.server.webflux.discovery.locator); D-09 RFC-7807 + correlation-ID + Authorization-strip policies that the gateway is responsible for at the public ingress"
  - phase: 01-04
    provides: "common-error library JAR — ApiErrorCode enum + ProblemDetailControllerAdvice (gateway depends on this for the URI namespace, even though the @ControllerAdvice itself is web-MVC and does not bind on the reactive runtime)"

provides:
  - "api-gateway runnable Spring Boot module: Spring Cloud Gateway 2025.0 (Northfields) reactive WebFlux at port 8080; @SpringBootApplication boot main; reactive permitAll() SecurityWebFilterChain with CORS for http://localhost:5173 (D-14 Phase 1 posture; Phase 3 swaps to JWT chain); two reactive @Component GlobalFilters (GatewayCorrelationIdFilter + GatewayHeaderInjectionFilter)"
  - "GatewayCorrelationIdFilter (D-09 mitigation) — implements GlobalFilter, Ordered.HIGHEST_PRECEDENCE; reads inbound X-Correlation-Id, generates UUID if absent or blank, mutates the forwarded request to add the header, echoes on the response, and writes the value to Reactor Context keyed `correlationId` so downstream reactive code can lift into MDC. HEADER constant aligned with com.n11.logging.CorrelationIdFilter.HEADER (servlet-side counterpart from 01-04) via wire-format compatibility, not a Java import"
  - "GatewayHeaderInjectionFilter (T-01-09 mitigation, Phase 1 STUB) — strips inbound X-User-Id / X-User-Roles before forwarding so a public client cannot impersonate identity-service-issued claims while the chain is permitAll(). Phase 3 will REPLACE this stub wholesale with full JWT-claim injection (Authorization-strip + decoded sub/roles re-injection). Javadoc ships clear breadcrumbs for the Phase 3 planner"
  - "config-server/src/main/resources/config/api-gateway.yml — gateway runtime config served by config-server (CD-05). Discovery-locator on with lower-case-service-id, httpclient response-timeout 60s, actuator exposure include='health,info,gateway' (T-01-04 explicit allowlist; the `gateway` value is the magic that exposes /actuator/gateway/routes -- Phase 1 SC-1 endpoint), management.endpoint.gateway.access=read_only, springdoc.swagger-ui.urls=[] (T-01-10 -- aggregator surfaces NOTHING in Phase 1; Phase 4+ explicitly append per-service entries), commented-out Phase 8 SSE forward-reference keyed `id: ai-service-chat-stream` with metadata.response-timeout: -1 + Path=/api/v1/chat/stream/** + Phase-8 NOTE about no ModifyResponseBody / no RetryFilter on SSE routes"
  - "api-gateway/src/main/resources/application.yml — bootstrap-only per Cross-Cutting #2 (Boot 3.x uses spring.config.import, NEVER bootstrap.yml). spring.application.name=api-gateway + spring.config.import=configserver:http://config-server:8888?fail-fast=true&max-attempts=10&max-interval=1500&multiplier=1.2&initial-interval=1100 (cold-boot retry window ~15-30s, fits SC-1 60s budget)"
  - "Jib-built local OCI image n11/api-gateway:dev in the local Docker daemon (one-time `./gradlew :api-gateway:jibDockerBuild` populates it); cold-boot ~5-10s in compose stack"
  - "docker-compose.yml additively extended (postgres + rabbitmq from 01-03 PRESERVED, eureka-server + config-server from 01-05 PRESERVED) with the api-gateway service block: depends_on service_healthy on both eureka-server and config-server, port 8080:8080, wget-based healthcheck, container_name=n11-api-gateway, SPRING_PROFILES_ACTIVE=docker"
  - "Pitfall #2 lockdown verified structurally: configurations.all { exclude(...) } denies spring-boot-starter-tomcat, spring-boot-starter-web, springdoc-openapi-starter-webmvc-ui transitives; ./gradlew :api-gateway:dependencies --configuration runtimeClasspath shows zero matches for any of those"
  - "SC-1 verification (single-owner, this plan's Task 6): all 5 Phase-1 infra services boot to (healthy) within 25s of `docker compose up -d` on a warm Docker daemon — well within ROADMAP SC-1's 60s budget. Recorded in .planning/phases/01-foundations-day-1-contracts/01-06-SC1-SMOKE.log"

affects: [01-07, all-Phase-3-onward, 08-ai-service]

tech-stack:
  added:
    - "spring-cloud-starter-gateway-server-webflux (Spring Cloud 2025.0 Northfields rename of the deprecated flat-named pre-2025 starter; resolves transitively to spring-cloud-gateway-server-webflux 4.3.0 via the BOM)"
    - "spring-boot-properties-migrator (runtimeOnly) — bridges any flat-prefix YAML strays to the new spring.cloud.gateway.server.webflux.* prefix and logs the canonical binding"
    - "spring-cloud-starter-config (gateway pulls runtime config from config-server's api-gateway.yml)"
    - "spring-cloud-starter-netflix-eureka-client (discovery-locator drives routing)"
    - "spring-boot-starter-webflux (explicit reactive web stack -- Pitfall #2 hardening even though gateway-server-webflux pulls it transitively)"
    - "spring-boot-starter-security (reactive Spring Security 6 -- Phase 1 permitAll(); Phase 3 swaps to JWT)"
    - "springdoc-openapi-starter-webflux-ui:2.8.17 -- aggregator surface only (the -webmvc-ui variant is denied by exclusions to prevent transitive Tomcat re-pull)"
    - "Jib Gradle 3.5.3 applied per-module (n11/api-gateway:dev image)"
  patterns:
    - "Boot+Jib selectively applied: api-gateway applies org.springframework.boot + com.google.cloud.tools.jib (so bootJar + jibDockerBuild are enabled). java + io.spring.dependency-management + Java 21 toolchain + spring-boot/spring-cloud BOM imports flow from the root subprojects { } block per Cross-Cutting #1 / Plan 01-01 Task 2."
    - "Pitfall #2 lockdown: configurations.all { exclude(group=\"org.springframework.boot\", module=\"spring-boot-starter-tomcat\"); exclude(... starter-web); exclude(... org.springdoc / springdoc-openapi-starter-webmvc-ui) }. A future plan that accidentally adds a Tomcat-pulling starter will fail dependency resolution rather than silently flipping the gateway to MVC and breaking Phase 8 SSE."
    - "Reactive GlobalFilter pattern for the gateway: implement org.springframework.cloud.gateway.filter.GlobalFilter + org.springframework.core.Ordered (the GlobalFilter package is UNCHANGED in Northfields/4.3.0 -- the rename was property-prefix-only). Use exchange.getRequest().mutate().header(...).build() and chain.filter(exchange.mutate().request(mutated).build()).contextWrite(Context.of(\"correlationId\", cid)) to thread state into Reactor Context."
    - "Defense-in-depth header stripping: even when the auth chain is permitAll() (D-14 Phase 1 posture), strip forgeable identity claim headers (X-User-Id, X-User-Roles) at the GatewayHeaderInjectionFilter (T-01-09). Phase 3 keeps the strip semantics and adds trusted-claim injection (defense in depth -- never trust the wire even with auth on)."
    - "CD-05 single-source-of-truth for runtime config: bootstrap-only application.yml at the gateway with spring.config.import to config-server; everything else (discovery-locator, actuator exposure, Springdoc) lives in config-server/src/main/resources/config/api-gateway.yml. The two-file split lets config-server overlay the shared baseline (application.yml) with gateway-specific values without code changes."
    - "Springdoc aggregator with empty urls list as the Phase 1 posture: T-01-10 mitigation -- the aggregator surfaces NOTHING until each service's swagger surface is reviewed and intentionally listed. Phase 4+ phases append per-service entries; never via wildcard / never auto-discover."
    - "Jib pre-build for SC-1 60s budget: pre-build local images via `./gradlew :<svc>:jibDockerBuild` (bundleable into one Gradle invocation across eureka + config + gateway) so docker-compose pulls a pre-built runtime instead of compiling at boot. Cold-boot 5-service stack to (healthy) in 25s observed -- well under the 60s budget. Replicate for every Boot service in Phase 3+."
    - "Stale-Jib-image hazard awareness: editing a YAML file under config-server/src/main/resources/config/ requires a config-server Jib rebuild before docker-compose serves it. The classpath:/config/ search-locations of Spring Cloud Config native profile binds at JAR-build time, not container-runtime."

key-files:
  created:
    - api-gateway/src/main/java/com/n11/gateway/ApiGatewayApplication.java
    - api-gateway/src/main/java/com/n11/gateway/SecurityConfig.java
    - api-gateway/src/main/java/com/n11/gateway/GatewayCorrelationIdFilter.java
    - api-gateway/src/main/java/com/n11/gateway/GatewayHeaderInjectionFilter.java
    - api-gateway/src/main/resources/application.yml
    - config-server/src/main/resources/config/api-gateway.yml
    - .planning/phases/01-foundations-day-1-contracts/01-06-SC1-SMOKE.log
  modified:
    - api-gateway/build.gradle.kts (replaced 01-01 stub `plugins { java }` with full Boot+Jib config; later commit dropped the :common-logging dep -- Rule 1 deviation; see Deviations below)
    - docker-compose.yml (appended api-gateway service block; postgres/rabbitmq/eureka-server/config-server preserved exactly once each)

key-decisions:
  - "Drop :common-logging dependency from api-gateway entirely (Rule 1 deviation). The gateway's reactive runtime structurally cannot host common-logging's auto-config classes (RestClientConfig + RabbitTemplateConfig @Import the servlet-side CorrelationIdFilter, which references jakarta.servlet.Filter -- correctly absent due to Pitfall #2 Tomcat exclusion). Even with @ConditionalOnClass, Spring's ConfigurationClassParser reads class metadata for @Import targets BEFORE conditional gates fire, so the gate doesn't help. The gateway has its own reactive GatewayCorrelationIdFilter; the two systems stay in sync via the wire-format X-Correlation-Id header name, not via a shared Java constant. 01-04-SUMMARY and 01-05-SUMMARY both flagged this as a known landmine for 01-06; the dependency-drop is the cleanest structural fix -- narrower than @SpringBootApplication(exclude=...) or @ComponentScan exclusion filters."
  - "GlobalFilter import package is org.springframework.cloud.gateway.filter (UNCHANGED in Northfields). The Spring Cloud 2025.0 rename was property-prefix-only (spring.cloud.gateway.* → spring.cloud.gateway.server.webflux.*) and starter-coordinate-only (spring-cloud-starter-gateway → spring-cloud-starter-gateway-server-webflux); the class packages stayed the same. Verified by unzip of spring-cloud-gateway-server-4.3.0.jar: org/springframework/cloud/gateway/filter/GlobalFilter.class is present at the original path. The plan's Task 3 fallback for a moved package proved unnecessary -- but the verify-by-unzip discipline kept it cheap to confirm."
  - "Property prefix binding (Cross-Cutting #6 verify-at-bootRun) confirmed: the YAML uses spring.cloud.gateway.server.webflux.* and binds without redirection. No PropertyMigratorListener deprecation warning seen in api-gateway logs at startup => the Northfields canonical prefix is what Spring is binding. Verification was done via container logs grep (`docker logs n11-api-gateway 2>&1 | grep -iE 'discover|locator|migrator|deprecat'`) rather than `:bootRun --debug` because Jib-image cold-boot is structurally identical to bootRun for this purpose and avoids the bootRun-hangs-waiting-for-requests trap."
  - "Wget-not-curl healthcheck idiom replicated from 01-05: eclipse-temurin:21-jre ships BusyBox utilities including wget but NOT curl. ['CMD-SHELL', 'wget -q -O- http://localhost:8080/actuator/health | grep -q \"\\\"status\\\":\\\"UP\\\"\"'] with start_period:15s + interval:5s x retries:6 = ~45s tolerance well within SC-1 60s budget."
  - "/actuator/gateway/routes returns ONE self-route at Phase 1, NOT literally [] as the plan's D-14 wording anticipated. Reason: the gateway is itself a Eureka client and discovery-locator auto-routes its own service id `API-GATEWAY` under /api-gateway/**. With no business services registered, this is the only route. The actual single self-route is correct Northfields behavior; the SC-1 success criterion 'routes are visible in /actuator/gateway/routes' is satisfied (200 + valid JSON body). Phase 3+ services will appear as additional entries automatically. Smoke log records the actual response body for audit."
  - "Stale config-server Jib image hazard surfaced (Rule 3 deviation): editing config-server/src/main/resources/config/api-gateway.yml requires `./gradlew :config-server:jibDockerBuild` BEFORE `docker compose up -d` will see the file (Spring Cloud Config native profile reads classpath:/config/ which is JAR-bound at build time). First cold-boot attempt reached api-gateway healthy in 31s but /actuator/gateway/routes returned 404; rebuild + re-cold-boot reached all-healthy in 25s. Recommend documenting this as a Wave-2+ workflow rule for any plan that touches a config-server YAML."

patterns-established:
  - "Reactive GlobalFilter shape (replicate for Phase 3 JWT chain + Phase 8 SSE filter): @Component implements GlobalFilter, Ordered. Filter method: read header from exchange.getRequest().getHeaders(), mutate request via exchange.getRequest().mutate().header(...).build(), pass forward via chain.filter(exchange.mutate().request(mutated).build()).contextWrite(Context.of(KEY, value)). Order via getOrder() with Ordered.HIGHEST_PRECEDENCE + offset for ordering between multiple GlobalFilters."
  - "CD-05 config-overlay split: api-gateway/src/main/resources/application.yml is bootstrap-only (spring.application.name + spring.config.import); all runtime config (discovery-locator, actuator exposure, Springdoc) lives in config-server/src/main/resources/config/api-gateway.yml. Replicate this split for every Boot app in Phase 3+ -- bootstrap.yml is forbidden (Boot 3.x dropped it)."
  - "5-service docker-compose append pattern: read existing file, ADD new keys under the existing services: map (NEVER re-write). Verify additivity via `grep -c '^  <svc>:' docker-compose.yml` returning exactly 1 for every previously-existing service. Plan 01-07 will follow the same idiom if it adds a service-template service block (likely deferred to Phase 11)."
  - "Phase 1 → Phase 3 hand-off contract for SecurityConfig.java + GatewayHeaderInjectionFilter.java: both files Javadoc-document that they are designed to be REPLACED, not patched. Phase 3's planner should expect to delete + rewrite SecurityConfig wholesale (oauth2ResourceServer().jwt() against identity-service's JWKS) and replace GatewayHeaderInjectionFilter with the JWT-claim injection version (Authorization-strip + sub/roles re-injection). The strip semantics in GatewayHeaderInjectionFilter stay (defense in depth -- never trust the wire even with auth on)."
  - "Phase 1 → Phase 8 hand-off contract for SSE routes: api-gateway.yml ships a commented-out routes block keyed `id: ai-service-chat-stream` with predicates `Path=/api/v1/chat/stream/**`, metadata `response-timeout: -1` + `connect-timeout: 5000`, filter `PreserveHostHeader=true`, and an inline NOTE that NO ModifyResponseBody filter and NO RetryFilter may be added to SSE routes (both buffer the response and break token-by-token streaming). Phase 8 just uncomments and points uri at lb://ai-service."

requirements-completed:
  - ARCH-04
  - QUAL-01

duration: ~17 min
completed: 2026-04-28
---

# Phase 1 Plan 06: api-gateway Spring Boot WebFlux Shell Summary

**A bootable Spring Cloud Gateway 2025.0 (Northfields) reactive WebFlux module on the new `spring-cloud-starter-gateway-server-webflux` coordinate, with discovery-locator-driven routing, an aggregator-only Springdoc surface, and a reactive `GatewayCorrelationIdFilter` -- but with a `permitAll()` security chain (D-14: Phase 3 will flip to JWT). All 5 Phase-1 infra services boot to (healthy) in 25s -- well under SC-1's 60s budget; `/actuator/gateway/routes` returns 200 (Phase-1 demonstrable success criterion #1).**

## Performance

- **Duration:** ~16m 45s (1005s wall-clock)
- **Started:** 2026-04-28T19:53:25Z
- **Completed:** 2026-04-28T20:10:11Z
- **Tasks:** 6 (5 atomic source-change commits + 1 SC-1 smoke log + final metadata)
- **Files created:** 7 (4 Java sources + 1 bootstrap YAML + 1 config-repo YAML + 1 SC-1 smoke log)
- **Files modified:** 2 (api-gateway/build.gradle.kts -- replaced 01-01 stub then dropped :common-logging dep; docker-compose.yml -- additive append of api-gateway service block)
- **Cold-boot smoke timing observed:** all 5 services to (healthy) in 25s from `docker compose up -d` on a warm Docker daemon (Jib images pre-populated). Well under the 60s SC-1 budget.

## Accomplishments

- **api-gateway runnable Boot app** at port 8080. `@SpringBootApplication` boot main; reactive `permitAll()` `SecurityWebFilterChain` with CORS for `http://localhost:5173` (Phase 10 Vite dev origin); two reactive `@Component` `GlobalFilter`s registered automatically:
  - `GatewayCorrelationIdFilter` (D-09): generates UUID for absent `X-Correlation-Id`, forwards downstream, echoes on response, writes Reactor `Context` keyed `correlationId`
  - `GatewayHeaderInjectionFilter` (T-01-09): strips inbound `X-User-Id` / `X-User-Roles` so a public client cannot impersonate identity-service-issued claims while the chain is `permitAll()`
- **Pitfall #2 structurally locked down**: `configurations.all { exclude(...) }` in `build.gradle.kts` denies `spring-boot-starter-tomcat`, `spring-boot-starter-web`, `springdoc-openapi-starter-webmvc-ui`. `./gradlew :api-gateway:dependencies --configuration runtimeClasspath` shows zero matches for any of those.
- **Northfields starter coordinate verified**: `spring-cloud-starter-gateway-server-webflux` resolves to `spring-cloud-gateway-server-webflux:4.3.0` via the spring-cloud-dependencies BOM 2025.0.0. The OLD pre-2025 starter (`spring-cloud-starter-gateway`) does NOT appear anywhere -- verified by grep.
- **Property prefix binding verified (Cross-Cutting #6)**: the YAML at `config-server/src/main/resources/config/api-gateway.yml` uses the canonical Northfields prefix `spring.cloud.gateway.server.webflux.discovery.locator.enabled` and binds without redirection. No `PropertyMigratorListener` deprecation warning in the running container's logs => the Northfields prefix IS what Spring is binding to.
- **GlobalFilter import package verified**: `org.springframework.cloud.gateway.filter.GlobalFilter` -- UNCHANGED in Northfields/4.3.0 (the rename was property-prefix-only and starter-coordinate-only; class packages stayed the same). Verified by `unzip -l spring-cloud-gateway-server-4.3.0.jar | grep GlobalFilter`.
- **CD-05 config-overlay verified**: `curl http://localhost:8888/api-gateway/default` returns `propertySources: [classpath:/config/api-gateway.yml, classpath:/config/application.yml]` -- the gateway-specific YAML overlays the shared baseline correctly. `management.endpoints.web.exposure.include` resolves to `'health,info,gateway'` (T-01-04 explicit allowlist).
- **D-14 Phase 1 posture demonstrable**: `/actuator/gateway/routes` returns 200 with one self-route entry (`ReactiveCompositeDiscoveryClient_API-GATEWAY` -- discovery-locator auto-routes the gateway's own Eureka registration under `/api-gateway/**`). No business services registered yet (Phase 3+ will add them; they appear automatically). The SC-1 endpoint is reachable; the success criterion is satisfied.
- **All 5 services healthy in 25s**: `docker compose down -v && docker compose up -d` reaches `(healthy)` for postgres + rabbitmq + eureka-server + config-server + api-gateway in 25 seconds on a warm Docker daemon. Recorded in `01-06-SC1-SMOKE.log`. SC-1 budget 60s; margin > 50%.
- **Phase 8 SSE forward-reference shipped**: `api-gateway.yml` contains a commented-out `routes:` block keyed `id: ai-service-chat-stream` with `predicates: [Path=/api/v1/chat/stream/**]`, `metadata: { response-timeout: -1, connect-timeout: 5000 }`, and an inline NOTE about no `ModifyResponseBody` and no `RetryFilter` on SSE routes. Phase 8 just uncomments and points `uri: lb://ai-service`.
- **Phase 4+ Springdoc aggregator append-shape locked**: `springdoc.swagger-ui.urls: []` empty list (T-01-10 mitigation -- the aggregator surfaces NOTHING in Phase 1; Phase 4+ explicitly append per-service entries -- never via wildcard).

## Task Commits

Each task was committed atomically:

1. **Task 1: api-gateway/build.gradle.kts** -- `8508db6` (feat) -- Northfields starter, Tomcat exclusion, properties-migrator, internal :common-error + :common-logging deps (the :common-logging dep was DROPPED later in the Task-5 commit -- see Deviations)
2. **Task 2: ApiGatewayApplication + SecurityConfig + bootstrap application.yml** -- `13507fb` (feat) -- @SpringBootApplication boot main; reactive permitAll() SecurityWebFilterChain with CORS; bootstrap-only application.yml with spring.config.import to config-server
3. **Task 3: GatewayCorrelationIdFilter + GatewayHeaderInjectionFilter** -- `040c394` (feat) -- two reactive @Component GlobalFilters; D-09 correlation propagation + T-01-09 header strip; GlobalFilter package verified at org.springframework.cloud.gateway.filter (UNCHANGED in Northfields)
4. **Task 4: config-server/.../config/api-gateway.yml** -- `905eff9` (feat) -- discovery-locator on, lower-case-service-id, httpclient response-timeout 60s, exposure include 'health,info,gateway', urls=[] aggregator empty, commented-out Phase 8 SSE forward-ref
5. **Task 5 + 6: docker-compose append + drop :common-logging dep + SC-1 smoke** -- `6890116` (feat) -- additive append of api-gateway service block; Rule-1 fix (drop :common-logging dep due to servlet-filter-on-reactive-runtime bug); Rule-3 fix (rebuild config-server Jib image to bake in api-gateway.yml); SC-1 cold-boot 25s recorded in 01-06-SC1-SMOKE.log

**Plan metadata commit:** to follow (after this SUMMARY.md write).

## Files Created/Modified

### api-gateway/

- `api-gateway/build.gradle.kts` -- REPLACED 01-01 stub. Boot + Jib plugins applied. Northfields starter `spring-cloud-starter-gateway-server-webflux` + `spring-boot-properties-migrator` runtime bridge + `spring-cloud-starter-netflix-eureka-client` + `spring-cloud-starter-config` + reactive WebFlux + actuator + reactive Spring Security + Springdoc -webflux-ui (NOT -webmvc-ui) + `:common-error` only (the `:common-logging` dep was dropped after a Rule-1 bug; see Deviations). `configurations.all { exclude(...) }` denies tomcat/web/webmvc-ui transitives. Jib block produces `n11/api-gateway:dev` from `eclipse-temurin:21-jre` with port 8080 and G1GC.
- `api-gateway/src/main/java/com/n11/gateway/ApiGatewayApplication.java` -- trivial `@SpringBootApplication` boot main; Javadoc documents D-14 Phase 1 posture and explains why the gateway intentionally does NOT depend on `:common-logging`.
- `api-gateway/src/main/java/com/n11/gateway/SecurityConfig.java` -- reactive `SecurityWebFilterChain` with `.csrf().disable()` + CORS for `http://localhost:5173` + `.anyExchange().permitAll()`. Javadoc warns Phase 3 to REPLACE this file wholesale (not patch) when JWT validation comes online.
- `api-gateway/src/main/java/com/n11/gateway/GatewayCorrelationIdFilter.java` -- D-09 reactive `GlobalFilter` at `Ordered.HIGHEST_PRECEDENCE`. Reads inbound `X-Correlation-Id`, generates UUID if absent or blank, mutates the forwarded request to add the header, echoes on the response, and writes the value to Reactor `Context` keyed `correlationId`.
- `api-gateway/src/main/java/com/n11/gateway/GatewayHeaderInjectionFilter.java` -- T-01-09 mitigation, Phase 1 STUB. Strips inbound `X-User-Id` / `X-User-Roles` before forwarding. Javadoc breadcrumbs Phase 3 to REPLACE this stub wholesale with full JWT-claim injection.
- `api-gateway/src/main/resources/application.yml` -- bootstrap-only per Cross-Cutting #2. `spring.application.name=api-gateway` + `spring.config.import=configserver:http://config-server:8888?fail-fast=true&max-attempts=10&max-interval=1500&multiplier=1.2&initial-interval=1100`.

### config-server/

- `config-server/src/main/resources/config/api-gateway.yml` -- gateway runtime config served by config-server (CD-05). `spring.cloud.gateway.server.webflux.discovery.locator.enabled=true` + `lower-case-service-id=true`, `httpclient.response-timeout=60s`, `management.endpoints.web.exposure.include='health,info,gateway'`, `management.endpoint.gateway.access=read_only`, `springdoc.swagger-ui.urls=[]`, commented-out Phase 8 SSE forward-ref keyed `id: ai-service-chat-stream`.

### docker-compose.yml

- ADDITIVELY EXTENDED. The 4 prior services (`postgres`, `rabbitmq`, `eureka-server`, `config-server`) are preserved exactly once each (verified by `grep -c '^  <svc>:'`). New `api-gateway` service block: `image: n11/api-gateway:dev`, `container_name: n11-api-gateway`, env vars (`SPRING_CONFIG_IMPORT`, `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`, `SPRING_PROFILES_ACTIVE=docker`), port `8080:8080`, `depends_on` with `service_healthy` on both eureka-server and config-server, wget-based healthcheck, `restart: unless-stopped`, `n11-net` network.

### .planning/phases/01-foundations-day-1-contracts/

- `01-06-SC1-SMOKE.log` -- SC-1 cold-boot timing record (25s, budget 60s -- PASS) + endpoint smoke results + property prefix binding verification.

## Decisions Made

See frontmatter `key-decisions` for the full list. Highlights:

1. **Drop `:common-logging` dependency from api-gateway entirely** (Rule 1 deviation). Spring's `ConfigurationClassParser` reads class metadata for `@Import` targets BEFORE `@ConditionalOnClass` gates fire, so common-logging's `RestClientConfig` (which `@Import`s the servlet `CorrelationIdFilter`) cannot be loaded on the reactive runtime even with the gate. The dependency-drop is the cleanest structural fix -- narrower than `@SpringBootApplication(exclude=...)` or `@ComponentScan` exclusion filters. The gateway has its own reactive `GatewayCorrelationIdFilter`; the two systems stay in sync via the wire-format `X-Correlation-Id` header name, not via a shared Java constant. Both 01-04-SUMMARY and 01-05-SUMMARY flagged this as a known landmine for 01-06.
2. **`GlobalFilter` import package is `org.springframework.cloud.gateway.filter`** (UNCHANGED in Northfields/4.3.0). The Spring Cloud 2025.0 rename was property-prefix-only (`spring.cloud.gateway.*` → `spring.cloud.gateway.server.webflux.*`) and starter-coordinate-only; the class packages stayed the same. Verified by `unzip -l spring-cloud-gateway-server-4.3.0.jar`.
3. **`/actuator/gateway/routes` returns ONE self-route at Phase 1**, NOT literally `[]` as the plan's D-14 wording anticipated. The gateway is itself a Eureka client and discovery-locator auto-routes its own service id `API-GATEWAY` under `/api-gateway/**`. With no business services registered, this is the only route. The actual single self-route is correct Northfields behavior; the SC-1 success criterion "routes are visible in /actuator/gateway/routes" is satisfied (200 + valid JSON body).
4. **Stale config-server Jib image hazard**: editing `config-server/src/main/resources/config/api-gateway.yml` requires `./gradlew :config-server:jibDockerBuild` BEFORE `docker compose up -d` will see the file (Spring Cloud Config native profile reads `classpath:/config/` which is JAR-bound at build time). Recommend documenting this as a Wave-2+ workflow rule for any plan that touches a config-server YAML.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] :common-logging dep crashes the reactive gateway at startup**

- **Found during:** Task 5 (first cold-boot attempt -- `docker compose up -d` reached api-gateway `Restarting (1)` instead of `(healthy)`)
- **Issue:** api-gateway crashed at startup with `Caused by: java.io.FileNotFoundException: class path resource [jakarta/servlet/Filter.class] cannot be opened because it does not exist`. Root cause: `:common-logging`'s `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` lists `RestClientConfig` and `RabbitTemplateConfig`, both of which use `@Import({ ... CorrelationIdFilter.class })` to pull the servlet-side filter into the @Configuration class graph. `CorrelationIdFilter` extends `OncePerRequestFilter` which extends `GenericFilterBean` which references `jakarta.servlet.Filter`. The `jakarta.servlet` API is correctly absent on the gateway's reactive runtime (Pitfall #2 lockdown excludes `spring-boot-starter-web` + `spring-boot-starter-tomcat`). Spring's `ConfigurationClassParser` reads class metadata for `@Import` targets BEFORE `@ConditionalOnClass` gates fire, so the gate-on-RestClient/RabbitTemplate doesn't help here.
- **Fix:** Drop the `:common-logging` dependency from api-gateway's `build.gradle.kts`. The gateway has its own reactive `GatewayCorrelationIdFilter` (Task 3) on the reactive runtime; the two systems stay in sync via the wire-format `X-Correlation-Id` header name, not via a shared Java constant. The plan's interface block (Task 1) carried the `:common-logging` dep forward from 01-04's notes ("the gateway depends on `:common-logging` only for the `CorrelationIdFilter.HEADER` and `MDC_KEY` constants") but on inspection the constants are trivially redefinable in `GatewayCorrelationIdFilter` itself.
- **Files modified:** `api-gateway/build.gradle.kts` (removed `implementation(project(":common-logging"))` line; expanded the comment block above the remaining `:common-error` dep to document the rationale); `api-gateway/src/main/java/com/n11/gateway/ApiGatewayApplication.java` (Javadoc updated to note the intentional non-dependency).
- **Verification:** `./gradlew :api-gateway:compileJava --quiet` exits 0; `./gradlew :api-gateway:jibDockerBuild --quiet` exits 0; cold-boot smoke run #2 (after the fix) reaches all 5 services `(healthy)` in 25s; api-gateway logs show clean Spring context refresh with no FileNotFoundException.
- **Committed in:** `6890116` (Task 5/6 combined commit -- the fix is on the same `build.gradle.kts` file as the Task 1 commit, so capturing it in the dep-revision commit preserves a clean graph rather than amending the original).

**2. [Rule 3 - Blocking issue] Stale config-server Jib image lacks api-gateway.yml**

- **Found during:** Task 5 (first cold-boot attempt -- after the Rule-1 fix above, api-gateway reached `(healthy)` in 31s but `/actuator/gateway/routes` returned 404 instead of 200 + JSON)
- **Issue:** `curl http://localhost:8080/actuator/gateway/routes` returned 404 (endpoint not exposed). Inspection of `curl http://localhost:8080/actuator` showed only `health` + `info` exposed (T-01-04 baseline from `application.yml`), NOT `health,info,gateway` from the Task 4 `api-gateway.yml`. Inspection of `curl http://localhost:8888/api-gateway/default` showed only `propertySources: [classpath:/config/application.yml]` -- the `api-gateway.yml` we just created was NOT being served. Root cause: the running `n11/config-server:dev` Jib image was built by 01-05 BEFORE Task 4 added `api-gateway.yml`. Spring Cloud Config native profile reads `classpath:/config/` which is JAR-bound at image-build time; updating the source file does not propagate to the container.
- **Fix:** Re-run `./gradlew :config-server:jibDockerBuild` to bake `api-gateway.yml` into the image; tear down and re-cold-boot via `docker compose down -v && docker compose up -d`.
- **Files modified:** None (the fix is a Gradle invocation + container restart, not a source change).
- **Verification:** Second cold-boot reaches all 5 services `(healthy)` in 25s; `curl http://localhost:8888/api-gateway/default` returns `propertySources: [classpath:/config/api-gateway.yml, classpath:/config/application.yml]` -- correct CD-05 overlay; `curl http://localhost:8080/actuator` exposes `[health, info, gateway]`; `curl http://localhost:8080/actuator/gateway/routes` returns 200 with valid JSON.
- **Committed in:** `6890116` (Task 5/6 -- the fix produces no source change, only a workflow rule documented in this SUMMARY's Decisions section).
- **Note for future plans:** any plan that edits `config-server/src/main/resources/config/*.yml` MUST run `./gradlew :config-server:jibDockerBuild` before `docker compose up -d` for the change to take effect. Recommend baking this rule into 01-07 (service-template) and Phase 3+ planning narratives.

---

**Total deviations:** 2 auto-fixed (1 Rule-1 bug in plan-cited dep wiring; 1 Rule-3 stale-image workflow gap).
**Impact on plan:** Both auto-fixes were necessary for the gateway to actually boot and for the Phase-1 SC-1 success criterion (`/actuator/gateway/routes` reachable) to be verifiable. No scope creep -- the dep-drop simplifies the gateway's classpath; the Jib-rebuild is a one-line workflow rule, not a code change. Tasks 1-4 acceptance criteria all pass as originally specified; Task 5/6 acceptance criteria pass with the noted nuances (single self-route at `/actuator/gateway/routes` instead of `[]`, 25s cold-boot with margin > 50% of the 60s SC-1 budget).

## Issues Encountered

Both deviations above (Rule 1 + Rule 3) were resolved cleanly during Task 5; no other issues encountered. The plan's Task 3 fallback for a Northfields-relocated `GlobalFilter` package was unnecessary (the package stayed at `org.springframework.cloud.gateway.filter`); the verify-by-jar-unzip discipline confirmed this in seconds.

The plan's Task 5 acceptance grep (`awk '/^  api-gateway:/,/^  [a-z]/'`) was intrinsically buggy: the same line `^  api-gateway:` matches both the start pattern (`/^  api-gateway:/`) and the end pattern (`/^  [a-z]/` since `a` is in `[a-z]`), so the awk range collapses to one line. Worked around with `awk '/^  api-gateway:/{flag=1;print;next} flag && /^[a-z]/{flag=0} flag'` which correctly captures the block. The structural intent (api-gateway block contains 8080:8080, eureka-server, config-server, service_healthy, healthcheck) was verified all-true.

The plan's expectation of "503 on unmatched paths" was not literally verified -- with no business services registered, Spring Cloud Gateway's `RoutePredicateHandlerMapping` returns 404 (no route matched) rather than 503 (route matched but upstream unreachable). This is correct WebFlux/Spring Cloud Gateway behavior; the 503 expectation in the plan would fire if discovery-locator auto-routed a service ID and the upstream was offline, which is a Phase 3+ scenario.

## User Setup Required

None for the build artifacts. For the local cold-boot smoke (already run as part of plan verification, recorded in `01-06-SC1-SMOKE.log`):

- `./gradlew :eureka-server:jibDockerBuild :config-server:jibDockerBuild :api-gateway:jibDockerBuild` -- one-time per code change to populate the local Docker daemon with all three Wave-2 Jib images. (Re-run `:config-server:jibDockerBuild` after editing any file under `config-server/src/main/resources/config/` -- the YAMLs are baked at image-build time.)
- `docker compose up -d` -- start the full 5-service stack. All reach `(healthy)` in ~25s.
- `curl http://localhost:8080/actuator/health` -- `{"status":"UP"}`.
- `curl http://localhost:8080/actuator/gateway/routes` -- 200 + JSON list (single self-route at Phase 1).
- `curl http://localhost:8761` -- Eureka dashboard 200.
- `curl http://localhost:8888/api-gateway/default` -- config-server returns the CD-05-overlay JSON for the gateway.

The smoke was run as part of plan verification (post-Task-5/6 commit) and torn down cleanly via `docker compose down`.

## Cold-boot timing (for the 01-07 planner)

**All 5 Phase-1 infra services reached `(healthy)` in 25 seconds** from `docker compose up -d` on a warm Docker daemon (Jib images already populated). Breakdown:

- t=0s: containers `Started` for postgres + rabbitmq + eureka-server + config-server
- t=~10s: eureka-server + config-server `(healthy)` -- now api-gateway can start (depends_on service_healthy)
- t=10-25s: api-gateway boots, registers with Eureka, fetches config from config-server, opens port 8080
- t=25s: api-gateway `(healthy)` -- all 5 services healthy

Pitfall #4 budget allowance was 60s for the full stack; we came in at less than half. Plan 01-07's service-template will add another Boot service to the stack; expected total `docker compose up -d` -> all-healthy time stays under 35s on a warm Docker daemon if 01-07 follows the same Jib pattern.

## Notes for downstream plans

**For Plan 01-07 (service-template archetype):**
- Apply the same `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` baseline (Plan 01-04 universal Gradle 8.10 + Boot BOM launcher fix; replicated in 01-05 + 01-06).
- service-template depends on `:common-error` + `:common-logging` + `:common-events` (per 01-04's Next Phase Readiness note). Unlike api-gateway, service-template is a SERVLET app (uses `spring-boot-starter-web` not `spring-boot-starter-webflux`), so common-logging's servlet-side filters bind cleanly there -- no need to drop the dep.
- service-template's `<svc>-service.yml` overlay lives at `config-server/src/main/resources/config/service-template.yml` (01-07 owns this file). REMEMBER: editing any file under `config-server/src/main/resources/config/` requires `./gradlew :config-server:jibDockerBuild` before `docker compose up -d` will see it (stale-Jib-image hazard documented above).
- service-template's bootstrap `application.yml` follows the same Cross-Cutting #2 shape as api-gateway: bootstrap-only `spring.application.name` + `spring.config.import=configserver:...?fail-fast=true&max-attempts=10&...`. NEVER use `bootstrap.yml`.
- service-template's Eureka client config baseline lives in the SHARED `config-server/.../config/application.yml` (owned by 01-05); 01-07 only adds Flyway concrete-schema overlay + service-template-specific keys.

**For Phase 3 (identity-service + JWT chain at the gateway):**
- `api-gateway/src/main/java/com/n11/gateway/SecurityConfig.java` is designed to be REPLACED, not patched. Phase 3's planner should expect to delete + rewrite the file: swap `.anyExchange().permitAll()` for `.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwkSetUri(...)))` against identity-service's JWKS endpoint. CORS allowedOrigins stays `[http://localhost:5173]` for dev (Phase 11 expands with the public tunnel hostname).
- `api-gateway/src/main/java/com/n11/gateway/GatewayHeaderInjectionFilter.java` is also designed to be REPLACED, not patched. Phase 3's version: continue stripping inbound `X-User-Id` / `X-User-Roles` (defense in depth -- never trust the wire), THEN read the validated JWT principal from the reactive `SecurityContext`, THEN inject trusted `X-User-Id` (from `sub` claim) + `X-User-Roles` (from `roles` claim), THEN strip the inbound `Authorization` header so downstream services don't see the raw JWT (D-09 + ARCHITECTURE.md §10 anti-pattern 4).
- `api-gateway.yml` Phase 3 add: `oauth2.resource-server.jwt.issuer-uri` block once identity-service publishes a JWKS endpoint.

**For Phase 4 (catalog -- product-service swagger surface):**
- `api-gateway.yml` Phase 4 add: append to `springdoc.swagger-ui.urls`:
  ```yaml
  springdoc:
    swagger-ui:
      urls:
        - name: identity-service    # Phase 3 actually owns the first append
          url: /api/v1/identity/v3/api-docs
        - name: product-service
          url: /api/v1/product/v3/api-docs
  ```
  NEVER use wildcard / never auto-discover. Each service is intentionally listed (T-01-10 mitigation).

**For Phase 8 (ai-service + SSE chat):**
- `api-gateway.yml` Phase 8 action: UNCOMMENT the existing routes block keyed `id: ai-service-chat-stream`. The shape is locked: `predicates: [Path=/api/v1/chat/stream/**]`, `metadata: { response-timeout: -1, connect-timeout: 5000 }`, `filters: [PreserveHostHeader=true]`. NO `ModifyResponseBody` filter, NO `RetryFilter` on SSE routes (both buffer the response and break token-by-token streaming).

**For Phase 11 (deploy + tunnel):**
- `SecurityConfig.java` CORS allowedOrigins expansion: append the chosen public tunnel hostname (Cloudflare Tunnel preferred; ngrok fallback) to the existing `[http://localhost:5173]` list. The `setExposedHeaders([X-Correlation-Id])` config means the tunnel's CORS preflight passes through the gateway's correlation-ID echo.
- `docker-compose.yml` Phase 11 may add a `cloudflared` sidecar service to expose the gateway's port 8080 on the tunnel.

**Wave 2 status:** Plan 01-06 was the second unit in Wave 2 (01-05 then 01-06). Wave 2 continues with Plan 01-07 (service-template archetype). Plan 01-08 (infra-tests Testcontainers cross-schema deny smoke) opens Wave 3 once Plan 01-07 ships. After 01-07 + 01-08, Phase 1 is complete and Phase 2 (Playwright recon for the n11 frontend) opens.

## Self-Check: PASSED

**Files verified to exist:**
- api-gateway/build.gradle.kts -- FOUND (modified)
- api-gateway/src/main/java/com/n11/gateway/ApiGatewayApplication.java -- FOUND
- api-gateway/src/main/java/com/n11/gateway/SecurityConfig.java -- FOUND
- api-gateway/src/main/java/com/n11/gateway/GatewayCorrelationIdFilter.java -- FOUND
- api-gateway/src/main/java/com/n11/gateway/GatewayHeaderInjectionFilter.java -- FOUND
- api-gateway/src/main/resources/application.yml -- FOUND
- config-server/src/main/resources/config/api-gateway.yml -- FOUND
- docker-compose.yml -- FOUND (modified, additively merged with api-gateway block)
- .planning/phases/01-foundations-day-1-contracts/01-06-SC1-SMOKE.log -- FOUND

**Commits verified to exist (in `git log`):**
- 8508db6 (Task 1: feat(01-06): wire api-gateway build.gradle.kts -- Northfields starter + Tomcat exclusion) -- FOUND
- 13507fb (Task 2: feat(01-06): api-gateway Spring Boot main + reactive SecurityConfig + bootstrap application.yml) -- FOUND
- 040c394 (Task 3: feat(01-06): two reactive GlobalFilters -- correlation-ID propagation + header strip stub) -- FOUND
- 905eff9 (Task 4: feat(01-06): config-server api-gateway.yml -- discovery locator + actuator + Springdoc aggregator) -- FOUND
- 6890116 (Task 5+6: feat(01-06): append api-gateway to docker-compose + drop :common-logging dep + SC-1 smoke) -- FOUND

**Build verified:**
- `./gradlew :api-gateway:build --quiet` -- BUILD SUCCESSFUL (exit 0); compile + tests pass (Phase 1 has no gateway-specific tests yet beyond what Boot ships)
- `./gradlew :api-gateway:jibDockerBuild --quiet` -- BUILD SUCCESSFUL (exit 0)
- Local Docker daemon shows `n11/api-gateway:dev` (alongside `n11/eureka-server:dev` and `n11/config-server:dev` from 01-05)

**Compose merge integrity verified:**
- `docker compose config --quiet` -- exit 0
- `grep -c '^  postgres:' docker-compose.yml` -- 1 (preserved)
- `grep -c '^  rabbitmq:' docker-compose.yml` -- 1 (preserved)
- `grep -c '^  eureka-server:' docker-compose.yml` -- 1 (preserved)
- `grep -c '^  config-server:' docker-compose.yml` -- 1 (preserved)
- `grep -c '^  api-gateway:' docker-compose.yml` -- 1 (added)

**Pitfall #2 lockdown verified:**
- `./gradlew :api-gateway:dependencies --configuration runtimeClasspath --quiet | grep -E 'spring-boot-starter-tomcat|spring-boot-starter-web[^f]|springdoc-openapi-starter-webmvc-ui'` -- 0 matches
- `grep -E 'spring-cloud-starter-gateway[^-]' api-gateway/build.gradle.kts` -- 0 matches

**Cold-boot smoke verified (post-Task-5/6 commit, before tear-down):**
- `docker compose up -d` -- all 5 services reached `(healthy)` in 25s (recorded in `01-06-SC1-SMOKE.log`)
- `curl http://localhost:8080/actuator/health` -- 200, `{"status":"UP"}`
- `curl http://localhost:8080/actuator` -- 200, exposes `[health, info, gateway]`
- `curl http://localhost:8080/actuator/gateway/routes` -- 200, JSON with single self-route entry (`ReactiveCompositeDiscoveryClient_API-GATEWAY` -- discovery-locator auto-routes the gateway's own Eureka registration)
- `curl http://localhost:8761` -- 200, Eureka dashboard HTML
- `curl http://localhost:8888/api-gateway/default` -- 200, JSON with `propertySources: [classpath:/config/api-gateway.yml, classpath:/config/application.yml]` (correct CD-05 overlay)
- `curl http://localhost:15672` -- 200, RabbitMQ management UI
- `docker compose exec -T postgres pg_isready -U postgres -d n11` -- accepting connections

**Property prefix binding verified (Cross-Cutting #6):**
- No `PropertyMigratorListener` deprecation warning in api-gateway logs (`docker logs n11-api-gateway 2>&1 | grep -iE 'discover|locator|migrator|deprecat'`) => the YAML uses `spring.cloud.gateway.server.webflux.*` (Northfields canonical) and binds without redirection
- `RouteDefinitionRouteLocator: Loaded RoutePredicateFactory [Path]` (and 13 other predicate factories) confirms the gateway loaded its discovery-locator runtime

**GlobalFilter package verified:**
- `unzip -l ~/.gradle/caches/modules-2/files-2.1/org.springframework.cloud/spring-cloud-gateway-server/4.3.0/.../spring-cloud-gateway-server-4.3.0.jar | grep GlobalFilter` -- shows `org/springframework/cloud/gateway/filter/GlobalFilter.class` (UNCHANGED in Northfields)

---
*Phase: 01-foundations-day-1-contracts*
*Completed: 2026-04-28*
