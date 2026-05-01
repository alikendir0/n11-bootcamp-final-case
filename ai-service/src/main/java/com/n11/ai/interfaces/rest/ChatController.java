package com.n11.ai.interfaces.rest;

import com.n11.ai.application.ChatUseCase;
import com.n11.ai.interfaces.rest.dto.ChatReply;
import com.n11.ai.interfaces.rest.dto.ChatRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    private final ChatUseCase useCase;

    public ChatController(ChatUseCase useCase) { this.useCase = useCase; }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public ChatReply chat(HttpServletRequest req, @Valid @RequestBody ChatRequest body) {
        UUID userId = resolveOptionalUserId(req);
        String correlationId = req.getHeader(HEADER_CORRELATION_ID);
        ChatUseCase.ChatReply reply = useCase.chat(
            body.conversationId(), body.message(), userId,
            correlationId == null ? UUID.randomUUID().toString() : correlationId);
        return new ChatReply(reply.conversationId(), reply.text());
    }

    static UUID resolveOptionalUserId(HttpServletRequest req) {
        String h = req.getHeader(HEADER_USER_ID);
        if (h == null || h.isBlank()) return null;          // guest — D-03
        try {
            return UUID.fromString(h);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz kullanıcı kimliği");
        }
    }
}
