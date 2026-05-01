plugins {
    id("org.springframework.boot")
    id("com.google.cloud.tools.jib")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // NO spring-boot-starter-amqp -- ai-service publishes/consumes no events in v1

    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")

    implementation("org.flywaydb:flyway-core:12.5.0")
    implementation("org.flywaydb:flyway-database-postgresql:12.5.0")

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:8.0")
    // google-genai:1.52.0 registers KotlinModule via META-INF/services/com.fasterxml.jackson.databind.Module
    // but does not include jackson-module-kotlin itself. LogstashEncoder calls
    // ObjectMapper.findAndRegisterModules() and fails on startup without this (Rule 1 fix).
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
    // common-logging includes CorrelationIdMessagePostProcessor which implements
    // spring-amqp MessagePostProcessor; ai-service has no AMQP publisher/consumer but
    // the component scan (com.n11.*) will try to load the class, requiring the parent
    // interface to be present at class-loading time. Using spring-amqp (core only) as
    // runtimeOnly provides MessagePostProcessor without triggering RabbitAutoConfiguration
    // (that's in spring-rabbit, not spring-amqp). spring.autoconfigure.exclude in
    // ai-service.yml provides belt-and-braces (Rule 1 fix).
    runtimeOnly("org.springframework.amqp:spring-amqp")

    // Phase 8 modules
    implementation(project(":ai-port"))
    implementation(project(":agent-toolset"))
    implementation(project(":common-error"))
    implementation(project(":common-logging"))
    // NO :common-outbox, NO :common-events -- ai-service has no AMQP

    // Gemini SDK -- ONLY ai-service imports com.google.genai (D-01)
    implementation("com.google.genai:google-genai:1.52.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.awaitility:awaitility:4.2.0")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("ai-service")
}

jib {
    from { image = "eclipse-temurin:21-jre" }
    to { image = "n11/ai-service:dev" }
    container {
        ports = listOf("8088")
        jvmFlags = listOf("-XX:+UseG1GC", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0")
    }
}
