package com.sakh.service;

import com.sakh.entity.ChatMessage;
import com.sakh.entity.ChatSession;
import com.sakh.entity.User;
import com.sakh.llm.LLMResponse;
import com.sakh.llm.LLMService;
import com.sakh.repository.ChatMessageRepository;
import com.sakh.repository.ChatSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final LLMService llmService;

    public ChatService(ChatSessionRepository sessionRepository,
                       ChatMessageRepository messageRepository,
                       LLMService llmService) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.llmService = llmService;
    }

    @Transactional
    public ChatSession createSession(User user, String title) {
        ChatSession session = new ChatSession();
        session.setUser(user);
        session.setTitle(title != null ? title : "New Chat");
        session.setCreatedAt(Instant.now());
        return sessionRepository.save(session);
    }

    public List<ChatSession> getUserSessions(Long userId) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public ChatMessage sendMessage(Long sessionId, Long userId, String message) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (!session.getUser().getId().equals(userId)) {
            throw new SecurityException("Access denied to session");
        }

        // Save user message
        ChatMessage userMessage = new ChatMessage();
        userMessage.setSession(session);
        userMessage.setMessageRole("user");
        userMessage.setMessage(message);
        userMessage.setCreatedAt(Instant.now());
        messageRepository.save(userMessage);

        // Get LLM response
        LLMResponse response = llmService.answerQuestion(message, session.getUser());

        // Save assistant message with citations
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setSession(session);
        assistantMessage.setMessageRole("assistant");
        assistantMessage.setMessage(response.getAnswer());
        
        // Store citations as JSON in the message (could be a separate column)
        if (response.getCitations() != null && !response.getCitations().isEmpty()) {
            String citationsJson = response.getCitations().stream()
                    .map(c -> String.format("{\"documentId\":%d,\"documentTitle\":\"%s\",\"pageNumber\":%s,\"sectionTitle\":\"%s\",\"chunkIndex\":%d,\"chunkText\":\"%s\"}",
                            c.getDocumentId(),
                            c.getDocumentTitle() != null ? c.getDocumentTitle().replace("\"", "\\\"") : "",
                            c.getPageNumber() != null ? c.getPageNumber() : "null",
                            c.getSectionTitle() != null ? c.getSectionTitle().replace("\"", "\\\"") : "",
                            c.getChunkIndex(),
                            c.getChunkText() != null ? c.getChunkText().replace("\"", "\\\"") : ""))
                    .collect(Collectors.joining(",", "[", "]"));
            assistantMessage.setCitations(citationsJson);
        }
        
        assistantMessage.setCreatedAt(Instant.now());
        messageRepository.save(assistantMessage);

        return assistantMessage;
    }

    public List<ChatMessage> getSessionMessages(Long sessionId, Long userId) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (!session.getUser().getId().equals(userId)) {
            throw new SecurityException("Access denied to session");
        }

        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public void deleteSession(Long sessionId, Long userId) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (!session.getUser().getId().equals(userId)) {
            throw new SecurityException("Access denied to session");
        }

        sessionRepository.delete(session);
    }
}