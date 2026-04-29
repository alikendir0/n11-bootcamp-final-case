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

    // Spring Cloud — discovery + centralized config
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")

    // Springdoc per-service (QUAL-01 per-service half, PROD-07)
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
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Testcontainers for integration tests
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:rabbitmq")
    testImplementation("org.testcontainers:junit-jupiter")

    // networknt JSON-Schema validator for OrderCreatedConsumerIntegrationTest drift gate
    testImplementation(libs.networknt.json.schema)

    // Awaitility for async assertion in OrderCreatedConsumerIntegrationTest
    testImplementation("org.awaitility:awaitility:4.2.0")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("inventory-service")
}

jib {
    from {
        image = "eclipse-temurin:21-jre"
    }
    to {
        image = "n11/inventory-service:dev"
    }
    container {
        ports = listOf("8083")
        jvmFlags = listOf("-XX:+UseG1GC", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0")
    }
}
