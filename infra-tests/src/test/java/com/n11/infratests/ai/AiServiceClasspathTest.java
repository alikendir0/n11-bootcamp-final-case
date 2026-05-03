package com.n11.infratests.ai;

import com.n11.agent.ToolRegistry;
import com.n11.ai.application.ConversationStateService;
import com.n11.ai.port.ChatProvider;
import com.n11.infratests.saga.AiServiceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 08-05 smoke: ai-service boots in the multi-service infra-tests classpath
 * with bean disambiguation (no NoUniqueBeanDefinitionException, no scan-explosion).
 */
@SpringBootTest(classes = AiServiceTestConfig.class,
                webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@TestPropertySource(properties = {
    "ai.provider=echo",
    "spring.cloud.config.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "eureka.client.enabled=false",
    "spring.flyway.locations=classpath:db/migration/ai",
    "spring.flyway.schemas=public",
    "spring.flyway.default-schema=public",
    "spring.jpa.properties.hibernate.default_schema=public",
    "spring.datasource.hikari.connection-init-sql=CREATE EXTENSION IF NOT EXISTS pgcrypto"
})
class AiServiceClasspathTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired ChatProvider chatProvider;
    @Autowired ToolRegistry toolRegistry;
    @Autowired ConversationStateService stateService;

    @Test
    void context_boots_with_echo_provider_and_11_tools() {
        assertThat(chatProvider).isNotNull();
        assertThat(toolRegistry.all()).hasSize(11);
        assertThat(stateService).isNotNull();
    }
}
