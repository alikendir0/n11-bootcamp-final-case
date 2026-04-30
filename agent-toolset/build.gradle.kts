plugins {
    `java-library`
}

dependencies {
    implementation(project(":ai-port"))
    // Jackson JsonNode for ToolResult.Ok payloads + tool args parsing
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Spring context for @Component on tool implementations (Plan 02)
    implementation("org.springframework:spring-context")
    // Spring Web brings RestClient (Spring 6.1+); we use blocking HTTP from tools (D-05).
    implementation("org.springframework:spring-web")
    // spring-boot-autoconfigure for @ConditionalOnMissingBean in ToolHttpClients
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
