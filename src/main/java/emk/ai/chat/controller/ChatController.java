package emk.ai.chat.controller;

import emk.ai.chat.dto.ChatRequest;
import emk.ai.chat.dto.SessionResponse;
import emk.ai.chat.dto.SessionStartRequest;
import emk.ai.chat.entity.ChatSession;
import emk.ai.chat.service.ChatSessionService;
import emk.ai.chat.service.ChatService;
import emk.ai.chat.util.Utils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final ChatSessionService chatSessionService;

    public ChatController(ChatService chatService, ChatSessionService chatSessionService) {
        this.chatService = chatService;
        this.chatSessionService = chatSessionService;
    }

    @PostMapping("/session/start")
    public ResponseEntity<SessionResponse> startSession(HttpServletRequest httpRequest,
                                                        @RequestBody(required = false) SessionStartRequest body) {

        String fingerprint = body != null ? body.fingerprint() : null;
        ChatSession chatSession = chatSessionService.createChatSession(httpRequest, fingerprint);

        return ResponseEntity.ok(new SessionResponse(
                chatSession.getToken(),
                chatSession.getExpiresAt(),
                chatSession.getMessagesLimit()
        ));
    }

    /**
     * Endpoint streaming SSE (Server-Sent Events).
     * Produce text/event-stream
     * Can be read via fetch API + Stream Reader.
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@Valid @RequestBody ChatRequest chatRequest, HttpServletRequest httpRequest) {

        String ip = Utils.getClientIp(httpRequest);

        // Check session and update quota
        ChatSession chatSession = this.chatSessionService.validateAndConsume(chatRequest.token(), ip);
        log.info("Remaining messages: {}", chatSession.getMessagesLimit() - chatSession.getMessagesUsed());

        return chatService.chatStream(chatRequest)
                .onErrorResume(e -> {
                    log.error("Error when calling LLM", e);
                    return Flux.just("ERROR: Chat service request failed.");
                });
    }
}
