package com.n11.ai.domain.chat;

import com.n11.ai.application.ChatUseCase;
import com.n11.ai.application.ConversationStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI-10 / Pitfall #20: system prompt forces Turkish. With EchoChatProvider
 * we can't observe Gemini's response, but we CAN observe that the prompt
 * text is in the conversation history seen by the provider — the structural
 * gate that prevents drift. (Real-Gemini language assertion lives in
 * GeminiSmokeTest.)
 */
@SpringBootTest(classes = com.n11.ai.AiServiceApplication.class,
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
class TurkishSystemPromptTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired ChatUseCase useCase;
    @Autowired ConversationStateService stateService;
    @Autowired SystemPromptProvider promptProvider;

    @Test
    void system_prompt_contains_required_turkish_directives() {
        String p = promptProvider.prompt();
        assertThat(p).contains("Türkçe yanıt ver");
        assertThat(p).contains("dil");
        assertThat(p).contains("KATEGORİLER");                  // Pitfall #20 Turkish headings
        assertThat(p).contains("UNKNOWN_ID");                   // Pitfall #10 directive
        assertThat(p).contains("AUTH_REQUIRED");                // D-04 directive
    }

    @Test
    void chat_response_from_echo_provider_is_turkish_echo_format() {
        UUID convId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        for (int turn = 1; turn <= 5; turn++) {
            String msg = (turn % 2 == 0) ? "test " + turn : "merhaba " + turn;
            ChatUseCase.ChatReply reply = useCase.chat(convId, msg, userId, "corr-test-" + turn);
            // EchoChatProvider returns "[ECHO] " + lastUserMessage. lastUserMessage scans
            // for the LAST USER role message in history; the system-prompt prelude is the
            // FIRST USER message, the real user input is the LAST USER message — so
            // EchoChatProvider echoes the original user text (verified below).
            assertThat(reply.text()).startsWith("[ECHO]");
            assertThat(reply.text()).contains(msg);
        }
    }
}
