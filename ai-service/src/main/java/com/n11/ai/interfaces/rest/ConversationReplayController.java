package com.n11.ai.interfaces.rest;

import com.n11.ai.domain.chat.Message;
import com.n11.ai.infrastructure.persistence.ConversationRepository;
import com.n11.ai.infrastructure.persistence.MessageRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/conversations")
public class ConversationReplayController {

    private static final String HEADER_USER_ID = "X-User-Id";

    private final ConversationRepository conversations;
    private final MessageRepository messages;

    public ConversationReplayController(ConversationRepository conversations, MessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    public record TurnDto(String role, String content, String toolCallJson, String toolResultJson, int sequenceNo) {}

    @GetMapping("/{id}")
    public List<TurnDto> replay(@PathVariable UUID id, HttpServletRequest req) {
        UUID userId = requireUserId(req);
        var conv = conversations.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Konuşma bulunamadı"));
        if (conv.getUserId() == null || !conv.getUserId().equals(userId)) {
            // guest conversations are not replayable; cross-user access denied
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Konuşma bulunamadı");
        }
        return messages.findByConversationIdOrderBySequenceNoAsc(id).stream()
            .map(m -> new TurnDto(
                m.getRole().name(),
                m.getContent(),
                m.getToolCallJson(),
                m.getToolResultJson(),
                m.getSequenceNo()))
            .toList();
    }

    private static UUID requireUserId(HttpServletRequest req) {
        String h = req.getHeader(HEADER_USER_ID);
        if (h == null || h.isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Kimlik doğrulaması gerekli");
        try { return UUID.fromString(h); }
        catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz kullanıcı kimliği");
        }
    }
}
