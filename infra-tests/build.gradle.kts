import java.time.Duration

// infra-tests/build.gradle.kts
//
// Test-only Gradle module. Hosts the cross-schema boundary smoke test (D-05) that
// verifies Plan 01-03's `infra/postgres/init.sh` enforces ARCH-09 at runtime.
//
// Plugin discipline (Cross-Cutting #1): `java` plugin ONLY, applied at root
// subprojects {} block. No Spring Boot plugin — this module never boots Spring.
// No `src/main`: pure test module.
//
// init.sh classpath strategy: single source of truth at `infra/postgres/init.sh`
// (root project, written by Plan 01-03). The `copyInitScript` task copies it into
// `src/test/resources/init.sh` before `processTestResources` so Testcontainers'
// `MountableFile.forClasspathResource("init.sh")` resolves it from the test JAR.
// Prevents drift between production init.sh and a hand-edited test fixture.

dependencies {
    // Testcontainers — versions managed by testcontainers-bom 2.0.5 (root build, Plan 01-01)
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:rabbitmq")
    testImplementation("org.testcontainers:junit-jupiter")

    // Postgres JDBC driver — version managed by Spring Boot BOM
    testImplementation("org.postgresql:postgresql")

    // JUnit 5 — version managed by Spring Boot BOM
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // SLF4J binding: use Logback (pulled in by spring-boot-starter-test via payment-service dependency)
    // Note: slf4j-simple is intentionally NOT added here because Spring Boot brings Logback,
    // and having both SLF4J bindings on the classpath causes "LoggerFactory is not a Logback LoggerContext".

    // D-10: ArchUnit gate for @RabbitListener ack-mode invariant
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")
    testImplementation("org.springframework.amqp:spring-rabbit")
    testImplementation("com.rabbitmq:amqp-client")  // for com.rabbitmq.client.Channel symbol resolution by ArchUnit

    // Awaitility for saga E2E test assertions
    testImplementation("org.awaitility:awaitility:4.2.0")

    // Services whose @RabbitListener classes ArchUnit must scan
    testImplementation(project(":common-events"))     // Envelope + RabbitRetryConfig (no @RabbitListener but pulls Spring AMQP types)
    testImplementation(project(":identity-service"))  // (no @RabbitListener today, but kept future-safe)
    testImplementation(project(":inventory-service")) // OrderCreatedConsumer + compensation consumers

    // payment-service: saga E2E test boots PaymentServiceApplication
    testImplementation(project(":payment-service"))
    // order-service: Plan 06-05 OrderPaymentFailureCompensationE2ETest boots
    // order-service to assert payment.failed → order CANCELLED (PAY-03 + QUAL-05).
    testImplementation(project(":order-service"))
    // notification-service: QUAL-04 saga closure E2E test (Plan 07-05) boots
    // notification-service to assert order.confirmed → notification row written.
    testImplementation(project(":notification-service"))
    testImplementation(project(":common-outbox"))
    // Phase 8 (Plan 08-05): ai-service + search-service classpath additions.
    // ai-port and agent-toolset are added explicitly because ai-service uses
    // 'implementation' (not 'api'), so their classes are on ai-service's runtime
    // classpath but NOT on infra-tests' compile classpath via transitive resolution.
    testImplementation(project(":ai-service"))
    testImplementation(project(":search-service"))
    testImplementation(project(":ai-port"))
    testImplementation(project(":agent-toolset"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // @EnableJpaRepositories needs spring-data-jpa on compile classpath for PaymentServiceTestConfig;
    // the dep comes transitively on runtimeClasspath via payment-service, but not compileClasspath
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
}

tasks.register<Copy>("copyInitScript") {
    description = "Copies infra/postgres/init.sh into the test classpath so " +
                  "MountableFile.forClasspathResource(\"init.sh\") resolves it."
    group = "build"
    from(rootProject.layout.projectDirectory.file("infra/postgres/init.sh"))
    into(layout.projectDirectory.dir("src/test/resources"))
    doFirst {
        val src = rootProject.layout.projectDirectory.file("infra/postgres/init.sh").asFile
        if (!src.exists()) {
            throw GradleException(
                "infra/postgres/init.sh is missing. Run Plan 01-03 first " +
                "(it writes the init script that this test verifies)."
            )
        }
    }
}

tasks.named("processTestResources") {
    dependsOn("copyInitScript")
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "info")
    timeout.set(Duration.ofMinutes(3))

    // Forward DOCKER_HOST so Testcontainers can find Docker Desktop on Windows where
    // the active context (`desktop-linux`) uses `npipe:////./pipe/dockerDesktopLinuxEngine`,
    // not the legacy `docker_engine` pipe Testcontainers tries first. CI runners typically
    // have DOCKER_HOST unset and use the default unix socket — pass-through-when-set
    // keeps both paths working.
    System.getenv("DOCKER_HOST")?.let { environment("DOCKER_HOST", it) }
}
