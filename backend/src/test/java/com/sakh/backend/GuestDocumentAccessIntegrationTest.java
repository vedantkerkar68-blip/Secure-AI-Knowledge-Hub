package com.sakh.backend;

import com.sakh.dto.document.DocumentListResponse;
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
import com.sakh.service.DocumentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * GUEST list/search enforcement, proven against the real database.
 *
 * <p>The GUEST restriction is applied inside the repository query rather than by
 * filtering results after the page is materialised. These tests assert the
 * consequence: a GUEST page contains only READY, AccessScope.ALL documents, and
 * {@code totalElements} counts permitted rows only. A post-query filter would return
 * a short page and an inflated total.
 */
@SpringBootTest
@ActiveProfiles("test")
class GuestDocumentAccessIntegrationTest {

    @Autowired
    private DocumentService documentService;

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
    private Long guestId;
    private String guestEmail;

    @BeforeEach
    void setUp() {
        Department guestDept = newDepartment("guest-doc-dept");
        Department otherDept = newDepartment("guest-other-dept");
        User guest = newUser("guest-doc@test.com", "GUEST", guestDept);
        guestId = guest.getId();
        guestEmail = guest.getEmail();
        User owner = newUser("guest-owner@test.com", "EMPLOYEE", otherDept);

        // Permitted for GUEST: READY + AccessScope.ALL
        newDocument("zebra public one", guestDept, owner, AccessScope.ALL, DocumentStatus.READY);
        newDocument("zebra public two", otherDept, owner, AccessScope.ALL, DocumentStatus.READY);
        newDocument("zebra public three", otherDept, owner, AccessScope.ALL, DocumentStatus.READY);

        // Disallowed for GUEST: department-scoped
        newDocument("zebra dept own", guestDept, owner, AccessScope.DEPARTMENT, DocumentStatus.READY);
        newDocument("zebra dept other", otherDept, owner, AccessScope.DEPARTMENT, DocumentStatus.READY);
        // Disallowed for GUEST: shared but not READY
        newDocument("zebra pending", otherDept, owner, AccessScope.ALL, DocumentStatus.PENDING);
        newDocument("zebra failed", otherDept, owner, AccessScope.ALL, DocumentStatus.FAILED);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        // Delete in dependency order: documents reference users and departments, and
        // users reference departments. Every created id is tracked explicitly so nothing
        // is inferred from rows that have already been removed.
        for (Long docId : createdDocumentIds) {
            jdbcTemplate.update("DELETE FROM document_metadata WHERE document_id = ?", docId);
            jdbcTemplate.update("DELETE FROM chunks WHERE document_id = ?", docId);
            jdbcTemplate.update("DELETE FROM vector_store WHERE metadata ->> 'documentId' = ?", String.valueOf(docId));
            jdbcTemplate.update("DELETE FROM documents WHERE id = ?", docId);
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
    }

    @Test
    void guestList_returnsOnlyReadyAllScopeDocuments() {
        authenticateAsGuest();

        Page<DocumentListResponse> page = documentService.getAllDocuments(null, null, null, PageRequest.of(0, 50));

        assertEquals(3, page.getTotalElements(),
                "GUEST total must count only permitted rows, not an in-memory-filtered page");
        for (DocumentListResponse doc : page.getContent()) {
            assertEquals("ALL", doc.getAccessScope(),
                    "GUEST must never receive a department-scoped document: " + doc.getTitle());
            assertEquals("READY", doc.getStatus(), "GUEST must never receive a non-READY document: " + doc.getTitle());
        }
    }

    @Test
    void guestSearch_returnsOnlyReadyAllScopeDocuments() {
        authenticateAsGuest();

        Page<DocumentListResponse> page = documentService.searchDocuments("zebra", PageRequest.of(0, 50));

        assertEquals(3, page.getTotalElements(),
                "GUEST search total must count only permitted rows");
        for (DocumentListResponse doc : page.getContent()) {
            assertEquals("ALL", doc.getAccessScope(), "GUEST search leaked: " + doc.getTitle());
            assertEquals("READY", doc.getStatus(), "GUEST search leaked: " + doc.getTitle());
        }
    }

    @Test
    void guestPagination_isNotCorruptedByDisallowedDocuments() {
        authenticateAsGuest();

        // 7 zebra documents exist but only 3 are permitted. A post-query filter would
        // return a short first page and a total of 7.
        Page<DocumentListResponse> first = documentService.getAllDocuments(null, null, null, PageRequest.of(0, 2));
        assertEquals(2, first.getContent().size(), "First page must be full");
        assertEquals(3, first.getTotalElements(), "Total must reflect permitted rows only");
        assertEquals(2, first.getTotalPages(), "Page count must be computed over permitted rows");

        Page<DocumentListResponse> second = documentService.getAllDocuments(null, null, null, PageRequest.of(1, 2));
        assertEquals(1, second.getContent().size(), "Second page holds the remaining permitted document");

        for (DocumentListResponse doc : first.getContent()) {
            assertEquals("ALL", doc.getAccessScope());
        }
        for (DocumentListResponse doc : second.getContent()) {
            assertEquals("ALL", doc.getAccessScope());
        }
    }

    @Test
    void guestSearchPagination_excludesDisallowedDocumentsFromEveryPage() {
        authenticateAsGuest();

        Page<DocumentListResponse> page = documentService.searchDocuments("zebra", PageRequest.of(0, 2));
        assertEquals(3, page.getTotalElements());
        for (DocumentListResponse doc : page.getContent()) {
            assertEquals("ALL", doc.getAccessScope(), "GUEST search page leaked: " + doc.getTitle());
        }
    }

    // --- helpers ---

    private void authenticateAsGuest() {
        UserDetails details = org.springframework.security.core.userdetails.User.builder()
                .username(guestEmail)
                .password("irrelevant")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_GUEST")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private Department newDepartment(String name) {
        Department d = new Department();
        d.setName(name + "-" + System.nanoTime());
        Department saved = departmentRepository.saveAndFlush(d);
        createdDepartmentIds.add(saved.getId());
        return saved;
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
        u.setFirstName("Guest");
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

    private Document newDocument(String title, Department dept, User uploader,
                                 AccessScope scope, DocumentStatus status) {
        Document doc = new Document();
        doc.setTitle(title);
        doc.setOriginalFilename(title + ".txt");
        doc.setStoredFilename(title + ".txt");
        doc.setStoragePath("/tmp/" + title + ".txt");
        doc.setFileType("text/plain");
        doc.setFileSize(64L);
        doc.setDepartment(dept);
        doc.setUploadedBy(uploader);
        doc.setAccessScope(scope);
        doc.setStatus(status);
        doc.setGroupId(System.nanoTime());
        doc.setVersion(1);
        doc.setIsLatest(true);
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        Document saved = documentRepository.saveAndFlush(doc);
        createdDocumentIds.add(saved.getId());
        return saved;
    }
}