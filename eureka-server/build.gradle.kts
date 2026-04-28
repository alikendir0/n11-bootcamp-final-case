// eureka-server/build.gradle.kts — Spring Cloud Netflix Eureka Server (ARCH-02).
//
// Boot plugin + Jib plugin applied here per Cross-Cutting #1 (root build.gradle.kts
// declares both `apply false`; this module opts in). java + dependency-management +
// the Java 21 toolchain + spring-boot/spring-cloud BOM imports are inherited from the
// root subprojects { } block (Plan 01-01 Task 2).
//
// Versions: spring-cloud-starter-netflix-eureka-server resolves from the
// spring-cloud-dependencies BOM 2025.0.0 — no inline version pin.
plugins {
    id("org.springframework.boot")
    id("com.google.cloud.tools.jib")
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Plan 01-04 fix: Gradle 8.10 ships an older bundled junit-platform-launcher than
    // the engine pulled by the Spring Boot 3.5.14 BOM. Pin the launcher to the BOM
    // version so the test JVM can boot ("OutputDirectoryProvider not available").
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// SC-1 (60s budget): build a local OCI image so docker-compose pulls a pre-built
// runtime instead of compiling at boot. Run `./gradlew :eureka-server:jibDockerBuild`
// before `docker compose up` (or wire it into the compose `up` script). The image
// lands in the local Docker daemon as `n11/eureka-server:dev`.
jib {
    from {
        image = "eclipse-temurin:21-jre"
    }
    to {
        image = "n11/eureka-server:dev"
    }
    container {
        ports = listOf("8761")
        jvmFlags = listOf("-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75")
    }
}
