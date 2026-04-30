plugins { `java-library` }

dependencies {
    api(project(":common-events"))                                // re-export Envelope record so consumers don't need to add common-events explicitly
    api("com.fasterxml.jackson.core:jackson-databind")            // ObjectMapper for envelope parsing in post-processor

    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")  // OutboxEvent JPA + JpaRepository
    compileOnly("org.springframework.amqp:spring-rabbit")                  // RabbitTemplate, MessagePostProcessor, MessageProperties
    compileOnly("org.springframework:spring-context")                      // @Scheduled, @Transactional propagation
    compileOnly("org.springframework:spring-tx")                           // @Transactional

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.amqp:spring-rabbit")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
