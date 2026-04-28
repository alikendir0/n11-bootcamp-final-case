plugins {
    `java-library`
}

dependencies {
    // Envelope is Jackson-serializable; consumers also rely on Jackson for payload JsonNode.
    api("com.fasterxml.jackson.core:jackson-databind")

    // Spring AMQP for RetryInterceptorBuilder, StatefulRetryOperationsInterceptor,
    // RejectAndDontRequeueRecoverer. compileOnly because non-AMQP consumers (rare)
    // can still use Envelope without pulling spring-rabbit.
    compileOnly("org.springframework.amqp:spring-rabbit")

    // Spring Retry — required for RetryInterceptorBuilder.
    compileOnly("org.springframework.retry:spring-retry")

    // Spring Boot autoconfigure — provides @ConditionalOnClass for RabbitRetryConfig.
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")

    // Test-time: full Spring AMQP + Spring Retry for RabbitRetryConfig wiring tests
    // and networknt JSON Schema validator for AbstractEventSchemaTest.
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation(libs.networknt.json.schema)
    testImplementation("org.springframework.amqp:spring-rabbit")
    testImplementation("org.springframework.retry:spring-retry")

    // [Rule 3 - same as common-error] align Gradle 8.10's bundled junit-platform-launcher
    // with the Spring Boot BOM's junit-platform-engine.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Make sure resources under saga-schemas/ ship on the test classpath
sourceSets {
    test {
        resources {
            srcDirs("src/main/resources", "src/test/resources")
        }
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
