// config-server/build.gradle.kts -- Spring Cloud Config Server (ARCH-03, CD-05).
//
// Boot plugin + Jib plugin applied here per Cross-Cutting #1 (root build.gradle.kts
// declares both `apply false`). java + dependency-management + the Java 21 toolchain
// + spring-boot/spring-cloud BOM imports are inherited from the root subprojects { }
// block (Plan 01-01 Task 2).
//
// Versions: spring-cloud-config-server resolves from the spring-cloud-dependencies
// BOM 2025.0.0 -- no inline version pin.
plugins {
    id("org.springframework.boot")
    id("com.google.cloud.tools.jib")
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-config-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Plan 01-04 fix: pin junit-platform-launcher to the BOM version so the test JVM
    // can boot ("OutputDirectoryProvider not available" on Gradle 8.10 + Boot 3.5.14).
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// SC-1 (60s budget): same Jib pattern as eureka-server. Build with
// `./gradlew :config-server:jibDockerBuild` before `docker compose up`.
jib {
    from {
        image = "eclipse-temurin:21-jre"
    }
    to {
        image = "n11/config-server:dev"
    }
    container {
        ports = listOf("8888")
        jvmFlags = listOf("-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75")
        environment = mapOf("SPRING_PROFILES_ACTIVE" to "native")
    }
}
