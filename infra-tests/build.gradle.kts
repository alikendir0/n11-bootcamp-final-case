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
    testImplementation("org.testcontainers:junit-jupiter")

    // Postgres JDBC driver — version managed by Spring Boot BOM
    testImplementation("org.postgresql:postgresql")

    // JUnit 5 — version managed by Spring Boot BOM
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // SLF4J binding for Testcontainers logs
    testRuntimeOnly("org.slf4j:slf4j-simple")
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
