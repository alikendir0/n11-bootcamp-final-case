plugins {
    `java-library`
}

dependencies {
    implementation(project(":ai-port"))
    // Jackson JsonNode for ToolResult.Ok payloads + tool args parsing
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Spring context for @Component on tool implementations (Plan 02)
    implementation("org.springframework:spring-context")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
