package com.n11.ai.domain.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.agent.AgentTool;
import com.n11.agent.ToolRegistry;
import com.n11.agent.ToolResult;
import com.n11.ai.application.ConversationStore;
import com.n11.ai.domain.tools.ToolDispatcher;
import com.n11.ai.port.ChatProvider;
import com.n11.ai.port.dto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ChatProvider chatProvider;
    @Mock ToolDispatcher toolDispatcher;
    @Mock ToolRegistry toolRegistry;
    @Mock SystemPromptProvider systemPrompt;
    @Mock ConversationStore store;
    @InjectMocks ChatService chatService;

    ObjectMapper json = new ObjectMapper();

    @Test
    void search_products_tool_result_emits_structured_product_data() throws Exception {
        UUID convId = UUID.randomUUID();
        when(store.conversationId()).thenReturn(convId);
        when(store.userIdOrNull()).thenReturn(null);
        when(store.history()).thenReturn(new ArrayList<>());
        when(store.seenIds()).thenReturn(new HashSet<>());
        when(systemPrompt.prompt()).thenReturn("test prompt");

        ToolCallRequest call = new ToolCallRequest("call-1", "search_products", "{\"query\":\"laptop\"}");
        ChatResponse response = new ChatResponse(null, List.of(call), null);
        when(chatProvider.chat(any(), any())).thenReturn(response);

        JsonNode productData = json.readTree("[{\"id\":\"p1\",\"name\":\"MacBook Pro\",\"priceGross\":49999.00,\"stockQty\":5}]");
        when(toolDispatcher.dispatch(eq("search_products"), any(), isNull(), eq("corr-1"), any()))
            .thenReturn(ToolResult.ok(productData));

        when(toolRegistry.all()).thenReturn(List.of());

        List<Map<String, Object>> capturedEvents = new ArrayList<>();
        BiConsumer<String, Object> emit = (name, payload) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("_eventName", name);
            if (payload instanceof Map<?,?> map) {
                for (Map.Entry<?,?> e : map.entrySet()) {
                    m.put((String) e.getKey(), e.getValue());
                }
            }
            capturedEvents.add(m);
        };

        chatService.handleStream(store, "laptop ara", "corr-1", emit);

        Optional<Map<String, Object>> toolResultOpt = capturedEvents.stream()
            .filter(e -> "tool_result".equals(e.get("_eventName")))
            .findFirst();

        assertThat(toolResultOpt).isPresent();
        Map<String, Object> tr = toolResultOpt.get();
        assertThat(tr.get("callId")).isEqualTo("call-1");
        assertThat(tr.get("toolName")).isEqualTo("search_products");
        assertThat(tr.get("ok")).isEqualTo(true);
        assertThat(tr.get("resultType")).isEqualTo("products");
        assertThat(tr.get("summary")).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) tr.get("data");
        assertThat(data).isNotNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> products = (List<Map<String, Object>>) data.get("products");
        assertThat(products).hasSize(1);
        assertThat(products.get(0).get("id")).isEqualTo("p1");
        assertThat(products.get(0).get("name")).isEqualTo("MacBook Pro");
    }

    @Test
    void tool_result_includes_concise_turkish_summary() throws Exception {
        UUID convId = UUID.randomUUID();
        when(store.conversationId()).thenReturn(convId);
        when(store.userIdOrNull()).thenReturn(null);
        when(store.history()).thenReturn(new ArrayList<>());
        when(store.seenIds()).thenReturn(new HashSet<>());
        when(systemPrompt.prompt()).thenReturn("test prompt");

        ToolCallRequest call = new ToolCallRequest("call-2", "get_product", "{\"productId\":\"p1\"}");
        when(chatProvider.chat(any(), any())).thenReturn(new ChatResponse(null, List.of(call), null));

        JsonNode productData = json.readTree("{\"id\":\"p1\",\"name\":\"Kablosuz Mouse\"}");
        when(toolDispatcher.dispatch(eq("get_product"), any(), isNull(), any(), any()))
            .thenReturn(ToolResult.ok(productData));

        when(toolRegistry.all()).thenReturn(List.of());

        List<Map<String, Object>> captured = new ArrayList<>();
        BiConsumer<String, Object> emit = (name, payload) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("_eventName", name);
            if (payload instanceof Map<?,?> map) {
                for (Map.Entry<?,?> e : map.entrySet()) {
                    m.put((String) e.getKey(), e.getValue());
                }
            }
            captured.add(m);
        };

        chatService.handleStream(store, "ürünü göster", "corr-2", emit);

        Optional<Map<String, Object>> trOpt = captured.stream()
            .filter(e -> "tool_result".equals(e.get("_eventName")))
            .findFirst();

        assertThat(trOpt).isPresent();
        Map<String, Object> tr = trOpt.get();
        assertThat(tr.get("summary")).isInstanceOf(String.class);
        String summary = (String) tr.get("summary");
        assertThat(summary.length()).isLessThanOrEqualTo(201);
    }
}