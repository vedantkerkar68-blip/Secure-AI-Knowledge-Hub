package com.sakh.service;

import com.sakh.dto.chat.ChatResponse;
import com.sakh.dto.chat.ChatSessionResponse;
import com.sakh.dto.chat.CitationDTO;
import com.sakh.entity.ChatMessage;
import com.sakh.entity.ChatSession;
import com.sakh.entity.User;
import com.sakh.enums.ActivityType;
import com.sakh.exception.AiServiceException;
import com.sakh.exception.ResourceNotFoundException;
import com.sakh.llm.LLMService;
import com.sakh.rag.AnswerVerifier;
import com.sakh.rag.ConversationSummarizer;
import com.sakh.rag.PromptBuilder;
import com.sakh.rag.RetrieverService;
import com.sakh.rag.TitleGenerator;
import com.sakh.repository.ChatMessageRepository;
import com.sakh.repository.ChatSessionRepository;
import com.sakh.repository.UserRepository;
import com.sakh.security.PromptSecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private static final int TOP_K = 5;
    private static final int SUMMARIZE_EVERY = 20;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final RetrieverService retrieverService;
    private final PromptBuilder promptBuilder;
    private final LLMService llmService;
    private final CitationService citationService;
    private final AnswerVerifier answerVerifier;
    private final TitleGenerator titleGenerator;
    private final ConversationSummarizer conversationSummarizer;
    private final PromptSecurityService promptSecurityService;
    private final MetricsCollector metricsCollector;
    private final ActivityLogService activityLogService;
    private final UserRepository userRepository;

    public ChatService(ChatSessionRepository sessionRepository,
                       ChatMessageRepository messageRepository,
                       RetrieverService retrieverService,
                       PromptBuilder promptBuilder,
                       LLMService llmService,
                       CitationService citationService,
                       AnswerVerifier answerVerifier,
                       TitleGenerator titleGenerator,
                       ConversationSummarizer conversationSummarizer,
                       PromptSecurityService promptSecurityService,
                       MetricsCollector metricsCollector,
                       ActivityLogService activityLogService,
                       UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.retrieverService = retrieverService;
        this.promptBuilder = promptBuilder;
        this.llmService = llmService;
        this.citationService = citationService;
        this.answerVerifier = answerVerifier;
        this.titleGenerator = titleGenerator;
        this.conversationSummarizer = conversationSummarizer;
        this.promptSecurityService = promptSecurityService;
        this.metricsCollector = metricsCollector;
        this.activityLogService = activityLogService;
        this.userRepository = userRepository;
    }

    @Transactional
    public ChatSession createSession(User user, String title) {
        ChatSession session = new ChatSession();
        session.setUser(user);
        session.setTitle(title != null ? title : "New Chat");
        session.setCreatedAt(Instant.now());
        return sessionRepository.save(session);
    }

    @Transactional
    public ChatSessionResponse createSession(String title) {
        User user = getCurrentUser();
        ChatSession session = createSession(user, title);
        return ChatSessionResponse.builder()
                .id(session.getId())
                .title(session.getTitle())
                .createdAt(session.getCreatedAt())
                .build();
    }

    @Transactional
    public ChatResponse sendMessage(Long sessionId, String message) {
        if (message == null || message.isBlank()) {
            throw new AiServiceException("Question cannot be empty.");
        }

        promptSecurityService.validate(message);

        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + sessionId));

        User currentUser = getCurrentUser();

        if (!session.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Access denied to session");
        }

        ChatMessage userMessage = new ChatMessage();
        userMessage.setSession(session);
        userMessage.setMessageRole("user");
        userMessage.setMessage(message);
        userMessage.setCreatedAt(Instant.now());
        messageRepository.save(userMessage);

        generateTitleIfNeeded(session, message);

        long startTime = System.currentTimeMillis();

        List<Document> documents = retrieverService.retrieve(message, currentUser, TOP_K);

        for (Document doc : documents) {
            double score = doc.getScore() != null ? doc.getScore() : 0.0;
            metricsCollector.recordRetrieval(score);
        }

        if (documents.isEmpty()) {
            throw new AiServiceException("No relevant documents found for your query. Try rephrasing or check document access permissions.");
        }

        String history = buildConversationHistory(sessionId);
        Prompt prompt = promptBuilder.build(message, documents, history);

        String answer = llmService.generateAnswer(prompt);

        var verification = answerVerifier.verify(answer, documents);
        answer = verification.text();
        metricsCollector.recordHallucination(verification.totalSentences(), verification.removedSentences());

        List<CitationDTO> citations = citationService.createCitations(documents);

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setSession(session);
        assistantMessage.setMessageRole("assistant");
        assistantMessage.setMessage(answer);
        if (!citations.isEmpty()) {
            assistantMessage.setCitations(toJson(citations));
        }
        assistantMessage.setCreatedAt(Instant.now());
        messageRepository.save(assistantMessage);

        summarizeIfNeeded(session);

        long elapsed = System.currentTimeMillis() - startTime;
        metricsCollector.recordQuery(elapsed);

        activityLogService.log(currentUser, ActivityType.CHAT,
                "Session: " + session.getId());

        BigDecimal confidence = calculateConfidence(citations);

        return ChatResponse.builder()
                .answer(answer)
                .confidence(confidence)
                .citations(citations)
                .build();
    }

    @Transactional
    public Flux<String> sendMessageStream(Long sessionId, String message) {
        if (message == null || message.isBlank()) {
            return Flux.error(new AiServiceException("Question cannot be empty."));
        }

        promptSecurityService.validate(message);

        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + sessionId));

        User currentUser = getCurrentUser();

        if (!session.getUser().getId().equals(currentUser.getId())) {
            return Flux.error(new AccessDeniedException("Access denied to session"));
        }

        ChatMessage userMessage = new ChatMessage();
        userMessage.setSession(session);
        userMessage.setMessageRole("user");
        userMessage.setMessage(message);
        userMessage.setCreatedAt(Instant.now());
        messageRepository.save(userMessage);

        generateTitleIfNeeded(session, message);

        List<Document> documents = retrieverService.retrieve(message, currentUser, TOP_K);

        for (Document doc : documents) {
            double score = doc.getScore() != null ? doc.getScore() : 0.0;
            metricsCollector.recordRetrieval(score);
        }

        if (documents.isEmpty()) {
            return Flux.error(new AiServiceException("No relevant documents found for your query."));
        }

        String history = buildConversationHistory(sessionId);
        Prompt prompt = promptBuilder.build(message, documents, history);

        long startTime = System.currentTimeMillis();
        StringBuilder fullAnswer = new StringBuilder();

        return llmService.streamAnswer(prompt)
                .doOnNext(fullAnswer::append)
                .doFinally(signalType -> {
                    var verification = answerVerifier.verify(fullAnswer.toString(), documents);
                    String verified = verification.text();
                    metricsCollector.recordHallucination(verification.totalSentences(), verification.removedSentences());
                    List<CitationDTO> citations = citationService.createCitations(documents);

                    ChatMessage assistantMessage = new ChatMessage();
                    assistantMessage.setSession(session);
                    assistantMessage.setMessageRole("assistant");
                    assistantMessage.setMessage(verified);
                    if (!citations.isEmpty()) {
                        assistantMessage.setCitations(toJson(citations));
                    }
                    assistantMessage.setCreatedAt(Instant.now());
                    messageRepository.save(assistantMessage);

                    summarizeIfNeeded(session);

                    long elapsed = System.currentTimeMillis() - startTime;
                    metricsCollector.recordQuery(elapsed);

                    activityLogService.log(currentUser, ActivityType.CHAT,
                            "Session: " + session.getId());
                });
    }

    private BigDecimal calculateConfidence(List<CitationDTO> citations) {
        if (citations == null || citations.isEmpty()) {
            return BigDecimal.ZERO;
        }

        double avg = citations.stream()
                .mapToDouble(c -> c.getSimilarityScore() != null ? c.getSimilarityScore() : 0.0)
                .average()
                .orElse(0.0);

        double max = citations.stream()
                .mapToDouble(c -> c.getSimilarityScore() != null ? c.getSimilarityScore() : 0.0)
                .max()
                .orElse(0.0);

        double coverage = (double) citations.size() / TOP_K;

        double confidence = avg * 0.5
                + Math.min(coverage, 1.0) * 0.2
                + max * 0.3;

        return BigDecimal.valueOf(Math.min(confidence, 1.0))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String email;
        if (principal instanceof UserDetails userDetails) {
            email = userDetails.getUsername();
        } else {
            email = principal.toString();
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private String toJson(List<CitationDTO> citations) {
        return citations.stream()
                .map(c -> String.format(
                        "{\"documentId\":%d,\"documentTitle\":\"%s\",\"pageNumber\":%s,\"sectionTitle\":\"%s\",\"chunkIndex\":%d,\"similarityScore\":%s}",
                        c.getDocumentId(),
                        c.getDocumentTitle() != null ? escape(c.getDocumentTitle()) : "",
                        c.getPageNumber() != null ? c.getPageNumber() : "null",
                        c.getSectionTitle() != null ? escape(c.getSectionTitle()) : "",
                        c.getChunkIndex(),
                        c.getSimilarityScore() != null ? c.getSimilarityScore() : "null"))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String buildConversationHistory(Long sessionId) {
        ChatSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return "";
        }

        if (session.getSummary() != null && !session.getSummary().isBlank()) {
            String summary = session.getSummary();
            List<ChatMessage> recent = messageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId);
            if (!recent.isEmpty()) {
                Collections.reverse(recent);
                String recentText = recent.stream()
                        .map(msg -> {
                            String role = "user".equals(msg.getMessageRole()) ? "User" : "Assistant";
                            return role + ": " + msg.getMessage();
                        })
                        .collect(Collectors.joining("\n\n"));
                return "Conversation summary so far:\n" + summary + "\n\nRecent messages:\n" + recentText;
            }
            return "Conversation summary:\n" + summary;
        }

        List<ChatMessage> recent = messageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId);
        if (recent.isEmpty()) {
            return "";
        }
        Collections.reverse(recent);
        return recent.stream()
                .map(msg -> {
                    String role = "user".equals(msg.getMessageRole()) ? "User" : "Assistant";
                    return role + ": " + msg.getMessage();
                })
                .collect(Collectors.joining("\n\n"));
    }

    private void summarizeIfNeeded(ChatSession session) {
        long count = messageRepository.countBySessionId(session.getId());
        if (count > 0 && count % SUMMARIZE_EVERY == 0) {
            List<ChatMessage> all = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
            String conversation = all.stream()
                    .map(msg -> {
                        String role = "user".equals(msg.getMessageRole()) ? "User" : "Assistant";
                        return role + ": " + msg.getMessage();
                    })
                    .collect(Collectors.joining("\n\n"));

            String summary = conversationSummarizer.summarize(conversation);
            if (summary != null) {
                session.setSummary(summary);
                sessionRepository.save(session);
                logger.info("Generated summary for session {} at {} messages", session.getId(), count);
            }
        }
    }

    private void generateTitleIfNeeded(ChatSession session, String message) {
        if (!"New Chat".equals(session.getTitle())) {
            return;
        }
        String title = titleGenerator.generate(message);
        if (title != null) {
            session.setTitle(title);
            sessionRepository.save(session);
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
