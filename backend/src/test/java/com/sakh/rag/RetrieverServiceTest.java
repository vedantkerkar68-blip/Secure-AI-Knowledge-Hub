package com.sakh.rag;

import com.sakh.entity.Department;
import com.sakh.entity.Role;
import com.sakh.entity.User;
import com.sakh.enums.AccessScope;
import com.sakh.enums.DocumentStatus;
import com.sakh.repository.ChunkRepository;
import com.sakh.repository.DepartmentRepository;
import com.sakh.repository.DocumentRepository;
import com.sakh.repository.UserRepository;
import com.sakh.security.DocumentAuthorizationService;
import com.sakh.service.DepartmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for RAG candidate generation vs. the centralized
 * authorization policy in {@link DocumentAuthorizationService}.
 *
 * <p>Candidate generation must be a superset of the permitted set (recall), and
 * the centralized {@code isAccessibleForRag} filter must still remove every
 * unauthorized chunk before it can reach the LLM prompt (security).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetrieverServiceTest {

    private static final Long ROOT_DEPT_ID = 1L;
    private static final Long CHILD_DEPT_ID = 10L;
    private static final Long UNRELATED_DEPT_ID = 99L;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChunkRepository chunkRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private QueryRewriter queryRewriter;

    @Mock
    private DepartmentService departmentService;

    @Mock
    private DepartmentRepository departmentRepository;

    private RetrieverService retriever;

    @BeforeEach
    void setUp() {
        retriever = new RetrieverService(
                vectorStore, chunkRepository, documentRepository, userRepository,
                queryRewriter,
                new DocumentAuthorizationService(departmentService, departmentRepository));

        when(queryRewriter.rewrite(anyString())).thenAnswer(inv -> inv.getArgument(0));

        // Root -> Child hierarchy: an employee in CHILD can see CHILD and ROOT (ancestors).
        Department root = department(ROOT_DEPT_ID, null);
        Department child = department(CHILD_DEPT_ID, root);
        List<Department> all = List.of(root, child);
        when(departmentService.getAllDepartmentsList()).thenReturn(all);
        when(departmentService.collectAncestorIds(any(), any())).thenAnswer(inv -> {
            List<Department> depts = inv.getArgument(0);
            Long deptId = inv.getArgument(1);
            List<Long> ids = new ArrayList<>();
            Department current = depts.stream()
                    .filter(d -> d.getId().equals(deptId))
                    .findFirst().orElse(null);
            while (current != null) {
                ids.add(current.getId());
                current = current.getParent();
            }
            return ids;
        });
    }

    @Test
    void retrieve_sharedAllScopeDocumentInUnrelatedDepartment_isReturned() {
        User user = user("emp@sakh.com", "EMPLOYEE", CHILD_DEPT_ID);

        com.sakh.entity.Document shared = entityDocument(500L, UNRELATED_DEPT_ID, AccessScope.ALL,
                DocumentStatus.READY, "owner@sakh.com");

        stubSemantic(shared.getId());
        stubKeywordRow(shared.getId(), UNRELATED_DEPT_ID, AccessScope.ALL, "owner@sakh.com");
        when(documentRepository.findAllById(any())).thenReturn(List.of(shared));

        List<Document> results = retriever.retrieve("policy", user, 5);

        assertEquals(1, results.size(),
                "A READY AccessScope.ALL document must be retrievable regardless of department");
    }

    @Test
    void retrieve_ancestorDepartmentDocument_isReturned() {
        User user = user("emp@sakh.com", "EMPLOYEE", CHILD_DEPT_ID);

        com.sakh.entity.Document ancestorDoc = entityDocument(600L, ROOT_DEPT_ID, AccessScope.DEPARTMENT,
                DocumentStatus.READY, "owner@sakh.com");

        stubSemantic(ancestorDoc.getId());
        stubKeywordRow(ancestorDoc.getId(), ROOT_DEPT_ID, AccessScope.DEPARTMENT, "owner@sakh.com");
        when(documentRepository.findAllById(any())).thenReturn(List.of(ancestorDoc));

        List<Document> results = retriever.retrieve("policy", user, 5);

        assertEquals(1, results.size(),
                "A document in an ancestor department must be retrievable");
    }

    @Test
    void retrieve_employeeWithoutDepartment_findsOwnUpload() {
        User user = user("orphan@sakh.com", "EMPLOYEE", null);

        com.sakh.entity.Document ownUpload = entityDocument(700L, UNRELATED_DEPT_ID, AccessScope.DEPARTMENT,
                DocumentStatus.READY, "orphan@sakh.com");

        stubSemantic(ownUpload.getId());
        stubKeywordRow(ownUpload.getId(), UNRELATED_DEPT_ID, AccessScope.DEPARTMENT, "orphan@sakh.com");
        when(documentRepository.findAllById(any())).thenReturn(List.of(ownUpload));

        List<Document> results = retriever.retrieve("policy", user, 5);

        assertEquals(1, results.size(),
                "An employee with no department must still retrieve their own upload");
    }

    @Test
    void retrieve_unrelatedDepartmentDocument_isFilteredOutBeforePrompt() {
        User user = user("emp@sakh.com", "EMPLOYEE", CHILD_DEPT_ID);

        com.sakh.entity.Document foreign = entityDocument(800L, UNRELATED_DEPT_ID, AccessScope.DEPARTMENT,
                DocumentStatus.READY, "owner@sakh.com");

        // Both candidate sources return the chunk, but it must not survive authorization.
        stubSemantic(foreign.getId());
        stubKeywordRow(foreign.getId(), UNRELATED_DEPT_ID, AccessScope.DEPARTMENT, "owner@sakh.com");
        when(documentRepository.findAllById(any())).thenReturn(List.of(foreign));

        List<Document> results = retriever.retrieve("policy", user, 5);

        assertTrue(results.isEmpty(),
                "An unauthorized document must be filtered out before prompt construction");
    }

    @Test
    void retrieve_nonReadySharedDocument_isFilteredOut() {
        User user = user("emp@sakh.com", "EMPLOYEE", CHILD_DEPT_ID);

        com.sakh.entity.Document processing = entityDocument(900L, UNRELATED_DEPT_ID, AccessScope.ALL,
                DocumentStatus.PROCESSING, "owner@sakh.com");

        stubSemantic(processing.getId());
        stubKeywordRow(processing.getId(), UNRELATED_DEPT_ID, AccessScope.ALL, "owner@sakh.com");
        when(documentRepository.findAllById(any())).thenReturn(List.of(processing));

        List<Document> results = retriever.retrieve("policy", user, 5);

        assertTrue(results.isEmpty(),
                "A non-READY document must not reach the prompt even when shared");
    }

    @Test
    void retrieve_missingDocumentMetadata_failsClosed() {
        User user = user("emp@sakh.com", "EMPLOYEE", CHILD_DEPT_ID);

        // Chunk claims documentId 1234 but the repository returns nothing.
        stubSemantic(1234L);
        stubKeywordRow(1234L, UNRELATED_DEPT_ID, AccessScope.DEPARTMENT, "owner@sakh.com");
        when(documentRepository.findAllById(any())).thenReturn(List.of());

        List<Document> results = retriever.retrieve("policy", user, 5);

        assertTrue(results.isEmpty(), "Chunks referencing an unknown document must fail closed");
    }

    @Test
    void semanticSearch_admin_appliesNoFilter() {
        User admin = user("admin@sakh.com", "ADMIN", ROOT_DEPT_ID);
        stubSemantic(1L);

        retriever.retrieve("policy", admin, 5);

        assertNull(captureFilter(), "ADMIN must not be restricted by a vector filter");
    }

    @Test
    void semanticSearch_departmentlessEmployee_getsBoundedFilter() {
        User user = user("orphan@sakh.com", "EMPLOYEE", null);
        stubSemantic(1L);

        retriever.retrieve("policy", user, 5);

        assertNotNull(captureFilter(),
                "A department-less employee must get a bounded filter, not an unfiltered scan");
    }

    @Test
    void retrieve_unrecognisedAccessScopeValue_failsClosedToDepartment() {
        User user = user("emp@sakh.com", "EMPLOYEE", CHILD_DEPT_ID);

        // A malformed/legacy scope value must not grant shared-document access,
        // and must not abort retrieval either.
        com.sakh.entity.Document doc = entityDocument(950L, UNRELATED_DEPT_ID, AccessScope.DEPARTMENT,
                DocumentStatus.READY, "owner@sakh.com");

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(chunkRepository.findKeywordSearchGlobal(anyString(), anyInt()))
                .thenReturn(List.<Object[]>of(new Object[]{
                        1L, doc.getId(), 0, "keyword chunk", 1, "Section",
                        UNRELATED_DEPT_ID, "owner@sakh.com", "LEGACY_SCOPE", 1.0d
                }));
        when(documentRepository.findAllById(any())).thenReturn(List.of(doc));

        List<Document> results = retriever.retrieve("policy", user, 5);

        assertTrue(results.isEmpty(),
                "An unrecognised access scope must not be treated as shared access");
    }

    private Object captureFilter() {
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        return captor.getValue().getFilterExpression();
    }

    // --- helpers ---

    private void stubSemantic(Long documentId) {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                Document.builder()
                        .text("semantic chunk")
                        .metadata(Map.of("documentId", documentId, "chunkId", 1L))
                        .score(0.9)
                        .build()));
    }

    /**
     * Row layout must match findKeywordSearchGlobal:
     * 0 chunkId, 1 documentId, 2 chunkIndex, 3 text, 4 page, 5 section,
     * 6 departmentId, 7 uploaderEmail, 8 accessScope, 9 rank.
     */
    private void stubKeywordRow(Long documentId, Long departmentId, AccessScope scope, String uploadedBy) {
        when(chunkRepository.findKeywordSearchGlobal(anyString(), anyInt()))
                .thenReturn(List.<Object[]>of(new Object[]{
                        1L, documentId, 0, "keyword chunk", 1, "Section",
                        departmentId, uploadedBy, scope.name(), 1.0d
                }));
    }

    private static Department department(Long id, Department parent) {
        Department d = new Department();
        d.setId(id);
        d.setName("dept-" + id);
        d.setParent(parent);
        return d;
    }

    private static User user(String email, String roleName, Long departmentId) {
        Role role = new Role();
        role.setName(roleName);

        Department department = departmentId != null ? department(departmentId, null) : null;

        User u = new User();
        u.setEmail(email);
        u.setRole(role);
        u.setDepartment(department);
        return u;
    }

    private static com.sakh.entity.Document entityDocument(Long id, Long departmentId, AccessScope scope,
                                                             DocumentStatus status, String uploaderEmail) {
        User uploader = new User();
        uploader.setEmail(uploaderEmail);

        com.sakh.entity.Document doc = new com.sakh.entity.Document();
        doc.setId(id);
        doc.setDepartment(department(departmentId, null));
        doc.setAccessScope(scope);
        doc.setStatus(status);
        doc.setUploadedBy(uploader);
        return doc;
    }
}
