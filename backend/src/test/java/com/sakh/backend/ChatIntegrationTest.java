package com.sakh.backend;

import com.sakh.entity.ChatSession;
import com.sakh.entity.Department;
import com.sakh.entity.Role;
import com.sakh.entity.User;
import com.sakh.enums.UserStatus;
import com.sakh.repository.ChatSessionRepository;
import com.sakh.repository.DepartmentRepository;
import com.sakh.repository.RoleRepository;
import com.sakh.repository.UserRepository;
import com.sakh.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VectorStore vectorStore;

    @MockBean
    private ChatModel chatModel;

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

        Document doc = Document.builder()
                .text("Company policy requires 30 days notice for resignation.")
                .metadata(Map.of(
                        "documentId", 99999L,
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
        
        // Manually set up authentication for the test
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin@sakh.com", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(auth);
        
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\": " + session.getId() + ", \"question\": \"What is the resignation policy?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").isString())
                .andExpect(jsonPath("$.answer").isNotEmpty())
                .andExpect(jsonPath("$.confidence").isNumber())
                .andExpect(jsonPath("$.citations").isArray())
                .andExpect(jsonPath("$.citations.length()").value(1));
    }

    @Test
    void sendMessage_withEmptyQuestion_returnsBadRequest() throws Exception {
        ChatSession session = setupTestSession();
        
        // Manually set up authentication for the test
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin@sakh.com", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(auth);
        
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\": " + session.getId() + ", \"question\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendMessage_withInvalidSession_returnsNotFound() throws Exception {
        setupTestSession();
        
        // Manually set up authentication for the test
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin@sakh.com", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(auth);
        
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\": 999999, \"question\": \"What is the policy?\"}"))
                .andExpect(status().isNotFound());
    }
}
