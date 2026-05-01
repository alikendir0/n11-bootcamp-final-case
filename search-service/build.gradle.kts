plugins {
    id("org.springframework.boot")
    id("com.google.cloud.tools.jib")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // NO springdoc -- D-09: no REST endpoints in v1
    // NO amqp -- skeleton

    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")

    implementation("org.flywaydb:flyway-core:12.5.0")
    implementation("org.flywaydb:flyway-database-postgresql:12.5.0")

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:8.0")

    implementation(project(":ai-port"))
    implementation(project(":common-error"))
    // NO common-logging -- search-service is a skeleton with no REST endpoints (D-09);
    // common-logging's CorrelationIdMessagePostProcessor requires spring-rabbit on the
    // classpath, but search-service intentionally has no AMQP dependency in v1.
    // NO google-genai -- search-service uses only EmbeddingProvider port (D-09)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("search-service")
}

jib {
    from { image = "eclipse-temurin:21-jre" }
    to { image = "n11/search-service:dev" }
    container {
        ports = listOf("8089")
        jvmFlags = listOf("-XX:+UseG1GC", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0")
    }
}
