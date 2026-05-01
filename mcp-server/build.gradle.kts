plugins {
    id("org.springframework.boot")
    id("com.google.cloud.tools.jib")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Spring AI MCP Server (version managed by spring-ai-bom:1.1.5 in root build.gradle.kts).
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")

    // Phase 9 reuses Phase 8's agent-toolset verbatim — DRY proof (CLAUDE.md Rule #2).
    implementation(project(":agent-toolset"))
    implementation(project(":common-error"))
    implementation(project(":common-logging"))
    // NO :ai-port, NO :common-events, NO :common-outbox — mcp-server is AMQP-free + DB-free in v1.

    runtimeOnly("net.logstash.logback:logstash-logback-encoder:8.0")
    // common-logging includes CorrelationIdMessagePostProcessor (implements spring-amqp
    // MessagePostProcessor). Component scan loads the class even though mcp-server has
    // no AMQP usage; spring-amqp (core only) supplies the parent interface symbol
    // without triggering RabbitAutoConfiguration. Same fix ai-service uses (line 34
    // of ai-service/build.gradle.kts).
    runtimeOnly("org.springframework.amqp:spring-amqp")
    // common-logging also contributes RabbitListenerCorrelationAspect; provide
    // AspectJ runtime symbols without enabling any AMQP listener infrastructure.
    runtimeOnly("org.aspectj:aspectjweaver")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("mcp-server")
}

jib {
    from { image = "eclipse-temurin:21-jre" }
    to { image = "n11/mcp-server:dev" }
    container {
        ports = listOf("8090")
        jvmFlags = listOf("-XX:+UseG1GC", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0")
    }
}
