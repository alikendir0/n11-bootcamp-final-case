package com.n11.ai.interfaces.sse;

import com.n11.ai.application.ChatUseCase;
import com.n11.ai.interfaces.rest.dto.ChatRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/chat/stream")
public class ChatStreamController {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamController.class);
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final long EMITTER_TIMEOUT_MS = 0L;
    private static final long HEARTBEAT_INTERVAL_MS = 15_000L;

    private final ChatUseCase useCase;
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat"); t.setDaemon(true); return t;
    });
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "chat-stream"); t.setDaemon(true); return t;
    });

    public ChatStreamController(ChatUseCase useCase) { this.useCase = useCase; }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletRequest req, @Valid @RequestBody ChatRequest body) {
        UUID userId = resolveOptionalUserId(req);
        String correlationId = req.getHeader(HEADER_CORRELATION_ID);
        String cid = (correlationId == null || correlationId.isBlank())
            ? UUID.randomUUID().toString() : correlationId;

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        AtomicBoolean completed = new AtomicBoolean(false);
        emitter.onCompletion(() -> completed.set(true));
        emitter.onTimeout(() -> { completed.set(true); emitter.complete(); });
        emitter.onError(t -> { completed.set(true); });

        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!completed.get()) {
                try {
                    emitter.send(SseEmitter.event().comment("keepalive"));
                } catch (Exception ignored) {
                    completed.set(true);
                }
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        executor.execute(() -> {
            MDC.put(MDC_CORRELATION_ID, cid);
            try {
                useCase.handleStream(body.conversationId(), body.message(), userId, cid,
                    (eventName, payload) -> sendIfOpen(emitter, completed, eventName, payload));
                if (completed.compareAndSet(false, true)) emitter.complete();
            } catch (Exception e) {
                log.error("Chat stream failed", e);
                sendIfOpen(emitter, completed, SseEvents.ERROR,
                    Map.of("code", "INTERNAL_ERROR", "messageTr", "Bir hata oluştu: " + e.getMessage()));
                if (completed.compareAndSet(false, true)) emitter.completeWithError(e);
            } finally {
                heartbeat.cancel(false);
                MDC.clear();
            }
        });

        return emitter;
    }

    private static void sendIfOpen(SseEmitter emitter, AtomicBoolean completed,
                                   String eventName, Object data) {
        if (completed.get()) return;
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            completed.set(true);
        }
    }

    private static UUID resolveOptionalUserId(HttpServletRequest req) {
        String h = req.getHeader(HEADER_USER_ID);
        if (h == null || h.isBlank()) return null;
        try { return UUID.fromString(h); }
        catch (IllegalArgumentException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                "Geçersiz kullanıcı kimliği");
        }
    }
}
