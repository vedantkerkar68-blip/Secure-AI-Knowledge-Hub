package com.sakh.controller;

import com.sakh.dto.chat.ChatRequest;
import com.sakh.dto.chat.ChatResponse;
import com.sakh.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/chat")
@Tag(name = "Chat", description = "AI-powered chat with RAG-based document retrieval and streaming")
@SecurityRequirement(name = "JWT")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Send chat message", description = "Sends a question and returns an AI-generated answer with citations and confidence score")
    public ResponseEntity<ChatResponse> chat(@RequestBody @Valid ChatRequest request) {
        ChatResponse response = chatService.sendMessage(request.getSessionId(), request.getQuestion());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/stream")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Stream chat response", description = "Sends a question and streams the AI-generated answer token by token via SSE")
    public SseEmitter chatStream(@RequestBody @Valid ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);

        chatService.sendMessageStream(request.getSessionId(), request.getQuestion())
                .subscribe(
                        text -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("token")
                                        .data(text, MediaType.TEXT_PLAIN));
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );

        return emitter;
    }
}
