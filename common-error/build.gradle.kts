plugins {
    `java-library`
}

dependencies {
    // Spring Web brings ProblemDetail (Spring 6.1+), @RestControllerAdvice, @ExceptionHandler.
    // compileOnly so consumers (which already pull spring-boot-starter-web transitively) decide the runtime version.
    compileOnly("org.springframework.boot:spring-boot-starter-web")

    // Servlet API for HttpServletRequest in handlers — provided by the consumer's web starter at runtime.
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    // SLF4J MDC (transitive via slf4j-api in Spring Boot's logging starter; declared explicitly to make the dependency obvious).
    compileOnly("org.slf4j:slf4j-api")

    // Tests: full Spring Web + JUnit 5 + Spring Test for MockMvc.
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter")

    // [Rule 3] Gradle 8.10 ships an older junit-platform-launcher than the
    // junit-platform-engine pulled by Spring Boot 3.5.14 BOM (5.13.x vs 5.10.x mismatch
    // -> "OutputDirectoryProvider not available"). Pin the launcher onto the test
    // runtime classpath so the platform engine + launcher are version-aligned.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
