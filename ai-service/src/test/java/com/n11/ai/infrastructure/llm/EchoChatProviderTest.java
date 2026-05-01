package com.n11.ai.infrastructure.llm;

import com.n11.ai.port.dto.ChatMessage;
import com.n11.ai.port.dto.ChatResponse;
import com.n11.ai.port.dto.MessageRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EchoChatProviderTest {

    private final EchoChatProvider echo = new EchoChatProvider();

    @Test
    void chat_echoes_last_user_message() {
        ChatResponse r = echo.chat(
            List.of(new ChatMessage(MessageRole.USER, "merhaba", null, null)),
            List.of()
        );
        assertThat(r.text()).isEqualTo("[ECHO] merhaba");
        assertThat(r.toolCalls()).isEmpty();
        assertThat(r.finishReason()).isEqualTo("STOP");
    }

    @Test
    void chat_handles_empty_history() {
        ChatResponse r = echo.chat(List.of(), List.of());
        assertThat(r.text()).isEqualTo("[ECHO] (boş mesaj)");
    }

    @Test
    void chatStream_emits_single_delta_then_completes() {
        List<String> deltas = new ArrayList<>();
        boolean[] completed = { false };
        echo.chatStream(
            List.of(new ChatMessage(MessageRole.USER, "test", null, null)),
            List.of(),
            deltas::add,
            () -> completed[0] = true,
            t -> { throw new AssertionError(t); }
        );
        assertThat(deltas).containsExactly("[ECHO] test");
        assertThat(completed[0]).isTrue();
    }
}
