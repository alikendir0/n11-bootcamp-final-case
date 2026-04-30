package com.n11.ai.port;

import com.n11.ai.port.dto.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave 0 gate test — Pitfall #7 (leaky ChatProvider abstraction):
 * verify the port surface exposes ONLY neutral DTOs.
 *
 * The Gradle dependency guard (./gradlew :ai-port:dependencies | grep -c
 * "com.google.genai") enforces zero-dep at build time; this test enforces
 * type-shape integrity at compile time.
 */
class AiPortContractTest {

    @Test
    void chatProvider_methods_use_only_neutral_dtos() throws Exception {
        Method chat = ChatProvider.class.getMethod("chat", java.util.List.class, java.util.List.class);
        assertEquals(ChatResponse.class, chat.getReturnType(), "chat() must return neutral ChatResponse");
        assertTrue(Modifier.isPublic(chat.getModifiers()), "chat() must be public");

        // Confirm no class loaded from this module is in the com.google.genai package
        // (defense-in-depth: dep guard catches the gradle metadata leak; this catches
        // accidental fully-qualified references in source).
        assertNull(getClass().getClassLoader().getResource("com/google/genai/"),
            "ai-port classpath must not contain com.google.genai resources");
    }

    @Test
    void embeddingProvider_signature_neutral() throws Exception {
        Method embed = EmbeddingProvider.class.getMethod("embed", String.class, int.class);
        assertEquals(float[].class, embed.getReturnType(), "embed() must return float[]");
    }

    @Test
    void chatMessage_record_carries_neutral_role_enum() {
        ChatMessage m = new ChatMessage(MessageRole.USER, "merhaba", null, null);
        assertEquals(MessageRole.USER, m.role());
        assertEquals("merhaba", m.content());
    }

    @Test
    void toolCallRequest_record_uses_string_args_json() {
        ToolCallRequest req = new ToolCallRequest("call-1", "search_products", "{\"q\":\"laptop\"}");
        assertEquals("call-1", req.callId());
        assertEquals("search_products", req.name());
    }
}
