package com.sakh.rag;

import com.sakh.dto.chat.CitationDTO;
import com.sakh.entity.Department;
import com.sakh.entity.Document;
import com.sakh.entity.Role;
import com.sakh.entity.User;
import com.sakh.enums.AccessScope;
import com.sakh.enums.DocumentStatus;
import com.sakh.enums.UserStatus;
import com.sakh.repository.DepartmentRepository;
import com.sakh.repository.DocumentRepository;
import com.sakh.repository.RoleRepository;
import com.sakh.repository.UserRepository;
import com.sakh.service.CitationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GUEST behaviour at the HTTP boundary, plus the citation-metadata policy.
 *
 * <p>Runs against the real database and the real Spring Security filter chain, so
 * both the route-level {@code @PreAuthorize} rules and the service-layer and
 * query-layer checks are exercised together.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuestCitationAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CitationService citationService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdDocumentIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdDepartmentIds = new ArrayList<>();
    private final List<Path> tempFiles = new ArrayList<>();
    private Long guestId;
    private String guestEmail;
    private String ownerEmail;
    private final Map<String, String> roleEmails = new java.util.HashMap<>();
    private String owningDepartmentName;
    private Long sharedDocumentId;
    private Long departmentScopedDocumentId;
    private Long pendingSharedDocumentId;

    @BeforeEach
    void setUp() throws IOException {
        Department dept = new Department();
        dept.setName("cite-dept-" + System.nanoTime());
        Department savedDept = departmentRepository.saveAndFlush(dept);
        createdDepartmentIds.add(savedDept.getId());
        owningDepartmentName = savedDept.getName();

        User guest = newUser("cite-guest@test.com", "GUEST", savedDept);
        guestId = guest.getId();
        guestEmail = guest.getEmail();
        User owner = newUser("cite-owner@test.com", "EMPLOYEE", savedDept);
        ownerEmail = owner.getEmail();
        roleEmails.put("EMPLOYEE", ownerEmail);
        roleEmails.put("MANAGER", newUser("cite-mgr@test.com", "MANAGER", savedDept).getEmail());
        roleEmails.put("ADMIN", newUser("cite-admin@test.com", "ADMIN", savedDept).getEmail());

        sharedDocumentId = newDocument("public handbook", savedDept, owner,
                AccessScope.ALL, DocumentStatus.READY);
        departmentScopedDocumentId = newDocument("internal handbook", savedDept, owner,
                AccessScope.DEPARTMENT, DocumentStatus.READY);
        pendingSharedDocumentId = newDocument("draft handbook", savedDept, owner,
                AccessScope.ALL, DocumentStatus.PENDING);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        // Dependency order: documents -> users -> departments. Every created id is
        // tracked explicitly so cleanup does not depend on rows still existing.
        for (Long id : createdDocumentIds) {
            jdbcTemplate.update("DELETE FROM document_metadata WHERE document_id = ?", id);
            jdbcTemplate.update("DELETE FROM chunks WHERE document_id = ?", id);
            jdbcTemplate.update("DELETE FROM vector_store WHERE metadata ->> 'documentId' = ?", String.valueOf(id));
            jdbcTemplate.update("DELETE FROM documents WHERE id = ?", id);
        }
        // The download endpoint records an activity log against the acting user, so those
        // rows must go before the user is deleted.
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM activity_logs WHERE user_id = ?", userId);
        }
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        }
        for (Long deptId : createdDepartmentIds) {
            jdbcTemplate.update("DELETE FROM departments WHERE id = ?", deptId);
        }
        createdDocumentIds.clear();
        createdUserIds.clear();
        createdDepartmentIds.clear();
        for (Path f : tempFiles) {
            try {
                Files.deleteIfExists(f);
            } catch (IOException ignored) {
                // best effort cleanup of a temp file
            }
        }
        tempFiles.clear();
    }

    // --- citation metadata ---

    @Test
    void guestCitation_redactsOwningDepartment() {
        var citation = citationService.createCitations(List.of(chunk(sharedDocumentId)), "GUEST").get(0);

        assertEquals(sharedDocumentId, citation.getDocumentId());
        assertEquals("public handbook", citation.getDocumentTitle());
        assertNull(citation.getDepartment(), "GUEST citations must not disclose the owning department");
    }

    @Test
    void nonGuestCitation_stillIncludesOwningDepartment() {
        for (String role : List.of("EMPLOYEE", "MANAGER", "ADMIN")) {
            var citation = citationService.createCitations(List.of(chunk(sharedDocumentId)), role).get(0);
            assertEquals(owningDepartmentName, citation.getDepartment(),
                    role + " must keep the existing department disclosure");
        }
    }

    // --- permitted read-only endpoints ---

    @Test
    void guest_canListDocuments() throws Exception {
        authenticateAsGuest();
        mockMvc.perform(get("/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void guest_canReadSharedReadyDocument() throws Exception {
        authenticateAsGuest();
        mockMvc.perform(get("/documents/{id}", sharedDocumentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sharedDocumentId));
    }

    @Test
    void guest_cannotReadDepartmentScopedDocument_returns404() throws Exception {
        authenticateAsGuest();
        mockMvc.perform(get("/documents/{id}", departmentScopedDocumentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void guest_cannotReadNonReadySharedDocument_returns404() throws Exception {
        authenticateAsGuest();
        mockMvc.perform(get("/documents/{id}", pendingSharedDocumentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void guestList_excludesDepartmentScopedDocuments() throws Exception {
        authenticateAsGuest();
        String body = mockMvc.perform(get("/documents"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(!body.contains("internal handbook"), "GUEST list leaked a department-scoped document");
        assertTrue(body.contains("public handbook"), "GUEST list must contain the shared document");
    }

    // --- denied operations ---

    @Test
    void guest_cannotUpload() throws Exception {
        authenticateAsGuest();
        mockMvc.perform(multipart("/documents").file(new org.springframework.mock.web.MockMultipartFile(
                        "file", "x.txt", "text/plain", "hello".getBytes())))
                .andExpect(status().isForbidden());
    }

    @Test
    void guest_cannotDeleteDocument() throws Exception {
        authenticateAsGuest();
        mockMvc.perform(delete("/documents/{id}", sharedDocumentId))
                .andExpect(status().isForbidden());
    }

    @Test
    void guest_cannotChangeDocumentStatus() throws Exception {
        authenticateAsGuest();
        mockMvc.perform(patch("/documents/{id}/status", sharedDocumentId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FAILED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void guest_cannotReadDocumentVersions() throws Exception {
        authenticateAsGuest();
        mockMvc.perform(get("/documents/{id}/versions", sharedDocumentId))
                .andExpect(status().isForbidden());
    }

    // --- preview and download ---

    @Test
    void guest_canPreviewSharedReadyDocument() throws Exception {
        authenticateAsGuest();

        String body = mockMvc.perform(get("/documents/{id}/preview", sharedDocumentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("public handbook"))
                .andReturn().getResponse().getContentAsString();

        // The preview must not disclose the owning department or the uploader identity.
        assertFalse(body.contains(owningDepartmentName),
                "GUEST preview disclosed the owning department: " + body);
        assertFalse(body.contains(ownerEmail),
                "GUEST preview disclosed the uploader email: " + body);
    }

    @Test
    void nonGuestRoles_previewRetainsDepartmentAndUploaderMetadata() throws Exception {
        for (String role : List.of("EMPLOYEE", "MANAGER", "ADMIN")) {
            authenticateAs(role);

            String body = mockMvc.perform(get("/documents/{id}/preview", sharedDocumentId))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertTrue(body.contains(owningDepartmentName),
                    role + " must retain the owning department in preview: " + body);
            assertTrue(body.contains(ownerEmail),
                    role + " must retain the uploader email in preview: " + body);
        }
    }

    @Test
    void guest_previewDoesNotMutateThePersistedDocument() throws Exception {
        authenticateAsGuest();
        mockMvc.perform(get("/documents/{id}/preview", sharedDocumentId))
                .andExpect(status().isOk());

        Document reloaded = documentRepository.findById(sharedDocumentId).orElseThrow();
        assertEquals(owningDepartmentName, reloaded.getDepartment().getName(),
                "Preview redaction must not modify the persisted document");
        assertEquals(ownerEmail, reloaded.getUploadedBy().getEmail(),
                "Preview redaction must not modify the persisted uploader");
    }

    @Test
    void guest_cannotPreviewDepartmentScopedDocument_returns404() throws Exception {
        authenticateAsGuest();
        mockMvc.perform(get("/documents/{id}/preview", departmentScopedDocumentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void guest_cannotPreviewNonReadySharedDocument_returns404() throws Exception {
        authenticateAsGuest();
        mockMvc.perform(get("/documents/{id}/preview", pendingSharedDocumentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void guest_canDownloadSharedReadyDocument() throws Exception {
        authenticateAsGuest();
        mockMvc.perform(get("/documents/{id}/download", sharedDocumentId))
                .andExpect(status().isOk());
    }

    @Test
    void guest_cannotDownloadDepartmentScopedDocument_returns404() throws Exception {
        authenticateAsGuest();
        mockMvc.perform(get("/documents/{id}/download", departmentScopedDocumentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void guest_cannotDownloadNonReadySharedDocument_returns404() throws Exception {
        authenticateAsGuest();
        mockMvc.perform(get("/documents/{id}/download", pendingSharedDocumentId))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private void authenticateAsGuest() {
        authenticateAs("GUEST");
    }

    private void authenticateAs(String role) {
        String email = "GUEST".equals(role) ? guestEmail : roleEmails.get(role);
        UserDetails details = org.springframework.security.core.userdetails.User.builder()
                .username(email)
                .password("irrelevant")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + role)))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private org.springframework.ai.document.Document chunk(Long documentId) {
        return org.springframework.ai.document.Document.builder()
                .text("public content")
                .metadata(Map.of("documentId", documentId, "chunkId", 1L, "chunkIndex", 0))
                .score(0.9)
                .build();
    }

    private Long newDocument(String title, Department dept, User uploader,
                             AccessScope scope, DocumentStatus status) {
        // A real temporary file so the download endpoint can serve content.
        Path file = null;
        try {
            file = Files.createTempFile("sakh-guest-", ".txt");
            Files.writeString(file, "public content");
            tempFiles.add(file);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create temporary test file", e);
        }

        Document doc = new Document();
        doc.setTitle(title);
        doc.setOriginalFilename(title + ".txt");
        doc.setStoredFilename(title + ".txt");
        doc.setStoragePath(file.toAbsolutePath().toString());
        doc.setFileType("text/plain");
        doc.setFileSize(10L);
        doc.setDepartment(dept);
        doc.setUploadedBy(uploader);
        doc.setAccessScope(scope);
        doc.setStatus(status);
        doc.setGroupId(System.nanoTime());
        doc.setVersion(1);
        doc.setIsLatest(true);
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        Long id = documentRepository.saveAndFlush(doc).getId();
        createdDocumentIds.add(id);
        return id;
    }

    private User newUser(String email, String roleName, Department dept) {
        Role role = roleRepository.findAll().stream()
                .filter(r -> roleName.equals(r.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName(roleName);
                    return roleRepository.saveAndFlush(r);
                });
        User u = new User();
        u.setFirstName("Cite");
        u.setLastName("User");
        u.setEmail(email + "-" + System.nanoTime() + "@example.test");
        u.setPasswordHash("$2a$10$irrelevantfortests");
        u.setRole(role);
        u.setDepartment(dept);
        u.setStatus(UserStatus.ACTIVE);
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        User saved = userRepository.saveAndFlush(u);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private static void assertTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message);
    }
}