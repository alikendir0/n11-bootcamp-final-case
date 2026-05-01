package com.n11.ai.interfaces.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.ai.application.ChatUseCase;
import com.n11.ai.interfaces.rest.dto.ChatRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * AI-08: SSE endpoint emits typed events (delta, done at minimum).
 * Uses a MockitoBean for ChatUseCase — no DB / Gemini calls needed.
 * The test verifies SSE wire format (event types, JSON data) only.
 */
@WebMvcTest(ChatStreamController.class)
class ChatStreamSseTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @MockitoBean ChatUseCase useCase;

    @Test
    void stream_emits_delta_and_done_events() throws Exception {
        UUID convId = UUID.randomUUID();
        ChatRequest req = new ChatRequest(convId, "merhaba");

        // Stub useCase.handleStream to emit a delta event via the BiConsumer
        doAnswer(invocation -> {
            java.util.function.BiConsumer<String, Object> emit = invocation.getArgument(4);
            emit.accept(SseEvents.DELTA, java.util.Map.of("text", "[ECHO] merhaba", "conversationId", convId.toString()));
            emit.accept(SseEvents.DONE, java.util.Map.of("conversationId", convId.toString(), "finalText", "[ECHO] merhaba"));
            return null;
        }).when(useCase).handleStream(any(), any(), any(), any(), any());

        MvcResult async = mockMvc.perform(
                post("/chat/stream")
                    .header("X-User-Id", UUID.randomUUID().toString())
                    .header("X-Correlation-Id", "corr-sse-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(req)))
            .andExpect(request().asyncStarted())
            .andReturn();

        String body = mockMvc.perform(asyncDispatch(async)).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("event:" + SseEvents.DELTA);
        assertThat(body).contains("event:" + SseEvents.DONE);
        assertThat(body).contains("[ECHO]");
    }
}
