plugins {
    `java-library`
}

dependencies {
    // ZERO external dependencies — ai-port must compile with no runtime deps.
    // Verify: ./gradlew :ai-port:dependencies | grep "com.google.genai" => 0

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
