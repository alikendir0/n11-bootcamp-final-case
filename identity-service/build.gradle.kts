plugins {
    id("org.springframework.boot")
    id("com.google.cloud.tools.jib")
}

dependencies {
    // Boot core
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    // JWT signing (NimbusJwtEncoder, JwtClaimsSet, JwsHeader from spring-security-oauth2-jose).
    // Identity-service does NOT validate JWTs (D-15: gateway is the only validator) — but the
    // starter ships the encoder classes and we need them.
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Spring Security — provides BCryptPasswordEncoder + servlet SecurityFilterChain (D-16, AUTH-07)
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Spring Cloud — discovery + centralized config
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")

    // Springdoc per-service (QUAL-01 per-service half)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")

    // Flyway 12.5 (D-11.4, ARCH-10 non-owner half)
    implementation("org.flywaydb:flyway-core:12.5.0")
    implementation("org.flywaydb:flyway-database-postgresql:12.5.0")

    // JDBC driver (version managed by Boot BOM)
    runtimeOnly("org.postgresql:postgresql")

    // Logback JSON (D-11.2, QUAL-06)
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:8.0")

    // Cross-cutting modules from 01-04
    implementation(project(":common-error"))
    implementation(project(":common-logging"))
    implementation(project(":common-events"))

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:rabbitmq")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("identity-service")
}

jib {
    from {
        image = "eclipse-temurin:21-jre"
    }
    to {
        image = "n11/identity-service:dev"
    }
    container {
        ports = listOf("8081")
        jvmFlags = listOf("-XX:+UseG1GC", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0")
    }
}
