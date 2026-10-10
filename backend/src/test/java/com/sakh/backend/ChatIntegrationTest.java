package com.sakh.backend;

import com.sakh.dto.chat.ChatResponse;
import com.sakh.entity.ChatSession;
import com.sakh.entity.Chunk;
import com.sakh.entity.Department;
import com.sakh.entity.Role;
import com.sakh.entity.User;
import com.sakh.entity.Document;
import com.sakh.enums.AccessScope;
import com.sakh.enums.DocumentStatus;
import com.sakh.enums.UserStatus;
import com.sakh.exception.AiServiceException;
import com.sakh.exception.ResourceNotFoundException;
import com.sakh.repository.ChatSessionRepository;
import com.sakh.repository.ChunkRepository;
import com.sakh.repository.DepartmentRepository;
import com.sakh.repository.DocumentRepository;
import com.sakh.repository.RoleRepository;
import com.sakh.repository.UserRepository;
import com.sakh.service.ChatService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Execution(ExecutionMode.SAME_THREAD)
class ChatIntegrationTest {

    @MockBean
    private VectorStore vectorStore;

    @MockBean
    private ChatModel chatModel;

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private ChunkRepository chunkRepository;

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ChatSessionRepository sessionRepository;

    private ChatSession session;

    private static Long docId;

    @BeforeAll
    static void initDocId() {
        docId = System.currentTimeMillis() + (long)(Math.random() * 10000);
    }

    @BeforeEach
    void setUp() {
        // Clear SecurityContext to avoid leakage between tests
        // SecurityContextHolder.clearContext() - not needed with manual auth setup
    }

    private ChatSession setupTestSession() {
        Role role = roleRepository.findAll().stream()
                .filter(r -> "ADMIN".equals(r.getName()))
                .findFirst()
                .orElseGet(() -> roleRepository.save(new Role(null, "ADMIN", null)));

        Department department = new Department();
        department.setName("Engineering");
        Department savedDepartment = departmentRepository.save(department);

        // Check if user already exists to avoid unique constraint violations
        // when transactional rollback doesn't clean up in time between tests
        String testEmail = "admin@sakh.com";
        User user = userRepository.findByEmail(testEmail).orElse(null);
        if (user == null) {
            User newUser = new User();
            newUser.setFirstName("Admin");
            newUser.setLastName("User");
            newUser.setEmail(testEmail);
            newUser.setPasswordHash("$2a$10$dummyhash");
            newUser.setRole(role);
            newUser.setDepartment(savedDepartment);
            newUser.setStatus(UserStatus.ACTIVE);
            newUser.setCreatedAt(Instant.now());
            newUser.setUpdatedAt(Instant.now());
            user = userRepository.save(newUser);
            userRepository.flush();
        }

        ChatSession session = chatService.createSession(user, "Test Chat");
        sessionRepository.flush();

        // Create a document entity for mocking
        com.sakh.entity.Document entityDoc = new com.sakh.entity.Document();
        entityDoc.setId(docId);
        entityDoc.setTitle("Resignation Policy");
        entityDoc.setOriginalFilename("resignation-policy.txt");
        entityDoc.setStoredFilename("resignation-policy.txt");
        entityDoc.setStoragePath("/tmp/resignation-policy.txt");
        entityDoc.setFileType("txt");
        entityDoc.setFileSize(1024L);
        entityDoc.setDepartment(savedDepartment);
        entityDoc.setUploadedBy(user);
        entityDoc.setStatus(com.sakh.enums.DocumentStatus.READY);
        entityDoc.setAccessScope(com.sakh.enums.AccessScope.DEPARTMENT);
        entityDoc.setGroupId(1L);
        entityDoc.setVersion(1);
        entityDoc.setIsLatest(true);
        entityDoc.setCreatedAt(Instant.now());
        entityDoc.setUpdatedAt(Instant.now());

        // Mock documentRepository to return the document
        when(documentRepository.findById(docId)).thenReturn(java.util.Optional.of(entityDoc));
        when(documentRepository.findAllById(any(Iterable.class))).thenReturn(List.of(entityDoc));

        // Mock chunkRepository to return empty results for keyword search
        when(chunkRepository.findKeywordSearchGlobal(anyString(), anyInt())).thenReturn(List.of());
        when(chunkRepository.findKeywordSearchInDocument(anyString(), anyLong(), anyInt())).thenReturn(List.of());

        // Mock vector store to return a document with the correct metadata
        org.springframework.ai.document.Document doc = org.springframework.ai.document.Document.builder()
                .text("Company policy requires 30 days notice for resignation.")
                .metadata(Map.of(
                        "documentId", docId,
                        "chunkId", docId,  // use same ID for chunkId
                        "chunkIndex", 0,
                        "pageNumber", 1,
                        "sectionTitle", "Resignation Policy",
                        "departmentId", savedDepartment.getId(),
                        "uploadedBy", testEmail
                ))
                .score(0.95)
                .build();

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc));

        AssistantMessage assistantMessage = new AssistantMessage(
                "The resignation policy requires 30 days notice.");
        Generation generation = new Generation(assistantMessage);
        org.springframework.ai.chat.model.ChatResponse aiResponse =
                new org.springframework.ai.chat.model.ChatResponse(List.of(generation));

        when(chatModel.call(any(Prompt.class))).thenReturn(aiResponse);

        return session;
    }

    @Test
    void sendMessage_withValidRequest_returnsAnswerWithCitationsAndConfidence() throws Exception {
        ChatSession session = setupTestSession();
        
        // Set up security context
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin@sakh.com", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(auth);
        
        ChatResponse response = chatService.sendMessage(session.getId(), "What is the resignation policy?", null);
        
        assertNotNull(response.getAnswer());
        assertFalse(response.getAnswer().isEmpty());
        assertNotNull(response.getConfidence());
        assertNotNull(response.getCitations());
        assertEquals(1, response.getCitations().size());
    }

    @Test
    void sendMessage_withEmptyQuestion_returnsBadRequest() throws Exception {
        ChatSession session = setupTestSession();
        
        // Set up security context
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin@sakh.com", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(auth);
        
        assertThrows(AiServiceException.class, () -> {
            chatService.sendMessage(session.getId(), "", null);
        });
    }

    @Test
    void sendMessage_withInvalidSession_returnsNotFound() throws Exception {
        setupTestSession();
        
        // Set up security context
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin@sakh.com", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(auth);
        
        assertThrows(ResourceNotFoundException.class, () -> {
            chatService.sendMessage(999999L, "What is the policy?", null);
        });
    }
}