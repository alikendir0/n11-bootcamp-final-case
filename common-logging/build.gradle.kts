plugins {
    `java-library`
}

dependencies {
    // Spring core for @Configuration, @Component, @Bean, @Order.
    api("org.springframework:spring-context")

    // Spring Boot autoconfigure — provides @AutoConfiguration support and is required at runtime.
    api("org.springframework.boot:spring-boot-autoconfigure")

    // Spring Web for OncePerRequestFilter, ClientHttpRequestInterceptor, RestClient.
    compileOnly("org.springframework.boot:spring-boot-starter-web")

    // Servlet API for OncePerRequestFilter's HttpServletRequest/Response.
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    // Spring AMQP for MessagePostProcessor, RabbitTemplate, ConnectionFactory, @RabbitListener (aspect).
    compileOnly("org.springframework.amqp:spring-rabbit")

    // SLF4J MDC.
    api("org.slf4j:slf4j-api")

    // Spring AOP for the @Around aspect.
    compileOnly("org.springframework:spring-aop")
    compileOnly("org.aspectj:aspectjweaver")

    // logstash-logback-encoder ships with consumers (it's a runtime dep, not API).
    // service-template/build.gradle.kts pulls it explicitly; declared here ONLY so the auto-config
    // imports compile reference is resolvable at module-build time.
    compileOnly(libs.logstash.logback)

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // [Rule 3 - same as common-error] align Gradle 8.10's bundled junit-platform-launcher
    // with the Spring Boot BOM's junit-platform-engine.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
