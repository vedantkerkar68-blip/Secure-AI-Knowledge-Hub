package com.sakh.controller;

import com.sakh.dto.chat.ChatRequest;
import com.sakh.dto.chat.ChatResponse;
import com.sakh.dto.chat.ChatSessionDetailResponse;
import com.sakh.dto.chat.ChatSessionRequest;
import com.sakh.dto.chat.ChatSessionResponse;
import com.sakh.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/chat")
@Tag(name = "Chat", description = "AI-powered chat with RAG-based document retrieval and streaming")
@SecurityRequirement(name = "JWT")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create chat session", description = "Creates a new chat session for the authenticated user")
    public ResponseEntity<ChatSessionResponse> createSession(@RequestBody ChatSessionRequest request) {
        return ResponseEntity.ok(chatService.createSession(request.getTitle()));
    }

    @GetMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List chat sessions", description = "Returns the authenticated user's chat sessions ordered by most recent activity")
    public ResponseEntity<List<ChatSessionResponse>> listSessions() {
        return ResponseEntity.ok(chatService.getSessions());
    }

    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get chat session", description = "Returns the authenticated user's chat session including its full message history")
    public ResponseEntity<ChatSessionDetailResponse> getSession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(chatService.getSessionDetail(sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete chat session", description = "Deletes the authenticated user's chat session and its messages")
    public ResponseEntity<Void> deleteSession(@PathVariable Long sessionId) {
        chatService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Send chat message", description = "Sends a question and returns an AI-generated answer with citations and confidence score")
    public ResponseEntity<ChatResponse> chat(@RequestBody @Valid ChatRequest request) {
        ChatResponse response = chatService.sendMessage(request.getSessionId(), request.getQuestion(), request.getDocumentId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/stream")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Stream chat response", description = "Sends a question and streams the AI-generated answer token by token via SSE")
    public SseEmitter chatStream(@RequestBody @Valid ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);

        chatService.sendMessageStream(request.getSessionId(), request.getQuestion(), request.getDocumentId())
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
