// api-gateway/build.gradle.kts -- Spring Cloud Gateway 2025.0 (Northfields), reactive WebFlux (ARCH-04, D-14).
//
// Boot plugin + Jib plugin applied here per Cross-Cutting #1 (root build.gradle.kts declares
// both `apply false`; this module opts in). java + dependency-management + the Java 21 toolchain
// + spring-boot/spring-cloud BOM imports are inherited from the root subprojects { } block
// (Plan 01-01 Task 2).
//
// Versions: spring-cloud-starter-gateway-server-webflux + spring-cloud-starter-config +
// spring-cloud-starter-netflix-eureka-client all resolve from the spring-cloud-dependencies
// BOM 2025.0.0 -- no inline version pins.
plugins {
    id("org.springframework.boot")
    id("com.google.cloud.tools.jib")
}

// ──────────────────────────────────────────────────────────────────
// Pitfall #2 lockdown (RESEARCH §4.1 lines 221-229 + PITFALLS.md #2):
// Spring Cloud Gateway is REACTIVE (WebFlux). It MUST NOT pull in
// spring-boot-starter-web (Tomcat) or springdoc-openapi-starter-webmvc-ui.
// Even one transitive Tomcat will silently flip the gateway to MVC and
// break reactive streaming (and SSE in Phase 8).
// ──────────────────────────────────────────────────────────────────
configurations.all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-web")
    // Defensive: deny -webmvc-ui Springdoc variant (would re-pull Tomcat). Use -webflux-ui only.
    exclude(group = "org.springdoc",            module = "springdoc-openapi-starter-webmvc-ui")
}

dependencies {
    // ── Northfields starter (RESEARCH §4.1 headline -- DO NOT use the deprecated
    //    flat-named pre-2025 starter; the -server-webflux suffix below is the Spring
    //    Cloud 2025.0 rename and the only correct coordinate going forward.)
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")

    // Properties-migrator: bridges any flat `spring.cloud.gateway.*` strays in YAML to
    // the new `spring.cloud.gateway.server.webflux.*` prefix and logs the canonical
    // binding on first :bootRun. Required for Cross-Cutting #6 verify-at-bootRun.
    runtimeOnly("org.springframework.boot:spring-boot-properties-migrator")

    // Eureka client -- discovery-locator drives routing (D-14)
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")

    // Spring Cloud Config client -- gateway pulls config from config-server
    implementation("org.springframework.cloud:spring-cloud-starter-config")

    // Reactive web stack (transitively from gateway-server-webflux but be explicit
    // so a future dep audit doesn't accidentally remove the gateway starter and
    // collapse the reactive runtime). Stays compatible with the exclusion block.
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Actuator -- required for /actuator/gateway/routes (D-14 success criterion #1)
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Reactive Spring Security -- Phase 1 ships permitAll(); Phase 3 swaps in JWT chain.
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Springdoc -- gateway aggregator surface only. MUST be the WEBFLUX variant
    // (the -webmvc-ui variant would silently pull Tomcat back in via its transitive
    // spring-boot-starter-web; see exclusion block above for defense in depth.)
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.17")

    // Internal cross-cutting module from 01-04 (common-error only).
    //
    // ── Why NOT :common-logging? ──────────────────────────────────────
    // common-logging's AutoConfiguration.imports lists RestClientConfig +
    // RabbitTemplateConfig, both of which @Import the servlet-side
    // CorrelationIdFilter (extends OncePerRequestFilter -> GenericFilterBean ->
    // references jakarta.servlet.Filter). Even with @ConditionalOnClass on the
    // RestClient/RabbitTemplate beans, Spring's ConfigurationClassParser reads
    // the class metadata for the @Import targets BEFORE the conditional gates
    // fire, causing FileNotFoundException: jakarta/servlet/Filter.class on the
    // reactive gateway runtime (jakarta.servlet API is correctly absent thanks
    // to the spring-boot-starter-web exclusion above).
    //
    // The gateway has its own reactive GatewayCorrelationIdFilter (Task 3) that
    // uses the same X-Correlation-Id header name as common-logging's servlet
    // filter, so the two systems stay in sync via the wire-format header --
    // not via a shared Java constant.
    //
    // 01-04-SUMMARY and 01-05-SUMMARY both flagged this as a known landmine for
    // 01-06; the dependency-drop here is the cleanest structural fix (per
    // executor deviation Rule 1: bug found in plan-cited dependency wiring;
    // fix narrowly without architectural change).
    // ──────────────────────────────────────────────────────────────────
    implementation(project(":common-error"))

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    // Plan 01-04 fix: pin junit-platform-launcher to the BOM version so the test JVM
    // can boot ("OutputDirectoryProvider not available" on Gradle 8.10 + Boot 3.5.14).
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// SC-1 (60s budget): same Jib pattern as eureka-server + config-server. Build with
// `./gradlew :api-gateway:jibDockerBuild` before `docker compose up`. The image lands
// in the local Docker daemon as `n11/api-gateway:dev`.
jib {
    from {
        image = "eclipse-temurin:21-jre"
    }
    to {
        image = "n11/api-gateway:dev"
    }
    container {
        ports = listOf("8080")
        jvmFlags = listOf("-XX:+UseG1GC", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0")
    }
}
