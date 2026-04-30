package com.n11.ai.infrastructure.llm;

import com.n11.ai.port.dto.ChatMessage;
import com.n11.ai.port.dto.ChatResponse;
import com.n11.ai.port.dto.MessageRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Gemini smoke test — gated on GEMINI_API_KEY. Skip-if-absent matches
 * the deferred payment-service test in
 * .planning/phases/06-payment-iyzico/deferred-items.md D-06-01.
 *
 * <p>This test verifies the full adapter stack including Pitfall #1 model probe.
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiSmokeTest {

    @Test
    void real_gemini_returns_non_empty_response() {
        GeminiChatAdapter adapter = new GeminiChatAdapter(
            "gemini-3-flash-preview",
            "gemini-2.5-flash",
            System.getenv("GEMINI_API_KEY")
        );
        ChatResponse r = adapter.chat(
            List.of(new ChatMessage(MessageRole.USER, "Tek kelimeyle: merhaba.", null, null)),
            List.of()
        );
        assertThat(r.text()).as("Gemini text response").isNotBlank();
    }
}
