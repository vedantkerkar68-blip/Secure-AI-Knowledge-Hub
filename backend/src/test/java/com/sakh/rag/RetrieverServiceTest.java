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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RAG candidate generation vs. the centralized authorization policy in
 * {@link DocumentAuthorizationService}.
 *
 * <p>Scope of this class - what it does and does not prove:
 * <ul>
 *   <li>It DOES prove the final centralized {@code isAccessibleForRag} filter removes
 *       unauthorized chunks before they can reach the LLM prompt: here the candidate
 *       sources are controlled, so the authorization decision is the thing under test.</li>
 *   <li>It does NOT prove the semantic filter expression actually selects the right
 *       vectors. {@code VectorStore} is mocked and returns fixed results regardless of
 *       the filter, so these tests cannot demonstrate filter effectiveness.</li>
 * </ul>
 *
 * <p>Those two concerns are covered elsewhere: the filter's textual content is asserted
 * in {@code DocumentAuthorizationServiceTest} (including that the real
 * {@code SearchRequest} parser accepts it), and its effect against a real PgVectorStore
 * and database is proven in {@code RagRetrievalCompatibilityIntegrationTest}.
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
        when(chunkRepository.findKeywordSearchGlobal(anyString(), anyInt(), anyList(), anyString(),
                anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(List.<Object[]>of(new Object[]{
                        1L, doc.getId(), 0, "keyword chunk", 1, "Section",
                        UNRELATED_DEPT_ID, "owner@sakh.com", "LEGACY_SCOPE", 1.0d
                }));
        when(documentRepository.findAllById(any())).thenReturn(List.of(doc));

        List<Document> results = retriever.retrieve("policy", user, 5);

        assertTrue(results.isEmpty(),
                "An unrecognised access scope must not be treated as shared access");
    }

    /**
     * Candidate generation is permissive for recall: the vector filter has no status
     * clause, so a document in the user's own department that is not READY can still
     * produce candidate chunks. Those candidates must be discarded by the authoritative
     * authorization check WITHOUT consuming the topK window and pushing authorized
     * chunks that ranked just below it out of the result set.
     */
    @Test
    void retrieve_notReadableCandidatesDoNotDisplaceAuthorizedChunks() {
        User user = user("emp@sakh.com", "EMPLOYEE", CHILD_DEPT_ID);

        // High-scoring candidates from a document the user can see by department but
        // that is NOT READY, so the authoritative check rejects them.
        com.sakh.entity.Document notReady = entityDocument(910L, CHILD_DEPT_ID, AccessScope.DEPARTMENT,
                DocumentStatus.PROCESSING, "owner@sakh.com");
        // Authorized READY document whose chunks rank lower.
        com.sakh.entity.Document ready = entityDocument(911L, CHILD_DEPT_ID, AccessScope.DEPARTMENT,
                DocumentStatus.READY, "owner@sakh.com");

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                chunkDoc(1L, notReady.getId(), 0.95),
                chunkDoc(2L, notReady.getId(), 0.90),
                chunkDoc(3L, ready.getId(), 0.50),
                chunkDoc(4L, ready.getId(), 0.40)));
        when(chunkRepository.findKeywordSearchGlobal(anyString(), anyInt(), anyList(), anyString(),
                anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(List.of());
        when(documentRepository.findAllById(any())).thenReturn(List.of(notReady, ready));

        // topK = 2, so fetchSize = 4 and all four candidates are ranked before authorizing.
        List<Document> results = retriever.retrieve("policy", user, 2);

        assertEquals(2, results.size(),
                "Authorized chunks ranked just below unauthorized ones must still be returned");
        for (Document d : results) {
            assertEquals(ready.getId(), ((Number) d.getMetadata().get("documentId")).longValue(),
                    "Only the READY, authorized document may appear: " + results);
        }
    }

    /**
 * Global retrieval must not deliver a non-READY document's chunks to the prompt,
 * including for ADMIN. The centralized isAccessibleForRag check is the authority.
 */
@Test
    void retrieve_adminNonReadyDocument_isNotDeliveredGlobally() {
        User admin = user("admin@sakh.com", "ADMIN", CHILD_DEPT_ID);

        com.sakh.entity.Document processing = entityDocument(920L, UNRELATED_DEPT_ID, AccessScope.ALL,
                DocumentStatus.PROCESSING, "owner@sakh.com");

        stubSemantic(processing.getId());
        when(chunkRepository.findKeywordSearchGlobal(anyString(), anyInt(), anyList(), anyString(),
                anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(List.of());
        when(documentRepository.findAllById(any())).thenReturn(List.of(processing));

        assertTrue(retriever.retrieve("policy", admin, 5).isEmpty(),
                "A PROCESSING document must not reach the prompt via global retrieval, even for ADMIN");
    }

    /**
     * Document-scoped retrieval must apply the same authoritative policy. verifyDocumentAccess
     * deliberately still lets ADMIN through checkDocumentViewAccess and requireReadable, so the
     * final isAccessibleForRag check is what must stop the non-READY content.
     */
    @Test
    void retrieve_adminNonReadyDocument_isNotDeliveredWhenScoped() {
        User admin = user("admin@sakh.com", "ADMIN", CHILD_DEPT_ID);
        Long docId = 930L;

        com.sakh.entity.Document failed = entityDocument(docId, UNRELATED_DEPT_ID, AccessScope.DEPARTMENT,
                DocumentStatus.FAILED, "owner@sakh.com");

        when(documentRepository.findById(docId)).thenReturn(Optional.of(failed));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                Document.builder()
                        .text("scoped chunk")
                        .metadata(java.util.Map.of("chunkId", 1L, "documentId", docId))
                        .score(0.9)
                        .build()));
        when(chunkRepository.findKeywordSearchInDocument(anyString(), anyLong(), anyInt()))
                .thenReturn(List.<Object[]>of(new Object[]{
                        1L, docId, 0, "scoped keyword chunk", 1, "Section",
                        UNRELATED_DEPT_ID, "owner@sakh.com", "DEPARTMENT", 1.0d
                }));

        assertTrue(retriever.retrieve("policy", admin, 5, docId).isEmpty(),
                "A FAILED document must not reach the prompt via scoped retrieval, even for ADMIN");
    }

    /** Global and scoped retrieval must agree on the same authoritative decision. */
    @Test
    void retrieve_readyDocument_isDeliveredBothGloballyAndWhenScoped() {
        User admin = user("admin@sakh.com", "ADMIN", CHILD_DEPT_ID);
        Long docId = 940L;

        com.sakh.entity.Document ready = entityDocument(docId, UNRELATED_DEPT_ID, AccessScope.ALL,
                DocumentStatus.READY, "owner@sakh.com");

        when(documentRepository.findById(docId)).thenReturn(Optional.of(ready));
        when(documentRepository.findAllById(any())).thenReturn(List.of(ready));

        // Global path
        stubSemantic(docId);
        when(chunkRepository.findKeywordSearchGlobal(anyString(), anyInt(), anyList(), anyString(),
                anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(List.of());
        assertFalse(retriever.retrieve("policy", admin, 5).isEmpty(),
                "A READY document must be retrievable globally for ADMIN");

        // Scoped path
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                Document.builder()
                        .text("scoped chunk")
                        .metadata(java.util.Map.of("chunkId", 1L, "documentId", docId))
                        .score(0.9)
                        .build()));
        when(chunkRepository.findKeywordSearchInDocument(anyString(), anyLong(), anyInt()))
                .thenReturn(List.<Object[]>of(new Object[]{
                        2L, docId, 0, "scoped keyword chunk", 1, "Section",
                        UNRELATED_DEPT_ID, "owner@sakh.com", "ALL", 1.0d
                }));

        assertFalse(retriever.retrieve("policy", admin, 5, docId).isEmpty(),
                "A READY document must be retrievable when scoped, for ADMIN");
    }

    /**
 * GUEST may retrieve public/shared knowledge only. Candidate generation is permissive,
 * so these tests prove the authoritative authorization check rejects department-scoped
 * content even when the vector store and keyword query return it as candidates.
 */
@Test
    void retrieve_guestGlobalRag_returnsOnlySharedReadyContent() {
        User guest = user("guest@sakh.com", "GUEST", CHILD_DEPT_ID);

        com.sakh.entity.Document shared = entityDocument(960L, UNRELATED_DEPT_ID, AccessScope.ALL,
                DocumentStatus.READY, "owner@sakh.com");
        com.sakh.entity.Document deptScoped = entityDocument(961L, CHILD_DEPT_ID, AccessScope.DEPARTMENT,
                DocumentStatus.READY, "owner@sakh.com");

        // Both are offered as candidates; only the shared one may survive.
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                chunkDoc(1L, shared.getId(), 0.9),
                chunkDoc(2L, deptScoped.getId(), 0.8)));
        when(chunkRepository.findKeywordSearchGlobal(anyString(), anyInt(), anyList(), anyString(),
                anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(List.of());
        when(documentRepository.findAllById(any())).thenReturn(List.of(shared, deptScoped));

        List<Document> results = retriever.retrieve("policy", guest, 5);

        assertEquals(1, results.size(),
                "GUEST must receive only the READY shared document");
        assertEquals(shared.getId(), ((Number) results.get(0).getMetadata().get("documentId")).longValue());
    }

    @Test
    void retrieve_guestGlobalRag_cannotRetrieveOwnDepartmentContent() {
        User guest = user("guest@sakh.com", "GUEST", CHILD_DEPT_ID);

        // A document in the guest's OWN department must still be out of reach.
        com.sakh.entity.Document ownDept = entityDocument(962L, CHILD_DEPT_ID, AccessScope.DEPARTMENT,
                DocumentStatus.READY, "owner@sakh.com");

        stubSemantic(ownDept.getId());
        when(chunkRepository.findKeywordSearchGlobal(anyString(), anyInt(), anyList(), anyString(),
                anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(List.of());
        when(documentRepository.findAllById(any())).thenReturn(List.of(ownDept));

        assertTrue(retriever.retrieve("policy", guest, 5).isEmpty(),
                "GUEST must not retrieve department-scoped content from its own department");
    }

    @Test
    void retrieve_guestScopedRag_deniesDepartmentScopedDocument() {
        User guest = user("guest@sakh.com", "GUEST", CHILD_DEPT_ID);
        Long docId = 963L;

        com.sakh.entity.Document deptScoped = entityDocument(docId, CHILD_DEPT_ID, AccessScope.DEPARTMENT,
                DocumentStatus.READY, "owner@sakh.com");
        when(documentRepository.findById(docId)).thenReturn(Optional.of(deptScoped));

        assertThrows(com.sakh.exception.ResourceNotFoundException.class,
                () -> retriever.retrieve("policy", guest, 5, docId),
                "Document-scoped RAG must reject a department-scoped document for GUEST");
    }

    @Test
    void retrieve_guestScopedRag_allowsSharedReadyDocument() {
        User guest = user("guest@sakh.com", "GUEST", CHILD_DEPT_ID);
        Long docId = 964L;

        com.sakh.entity.Document shared = entityDocument(docId, UNRELATED_DEPT_ID, AccessScope.ALL,
                DocumentStatus.READY, "owner@sakh.com");
        when(documentRepository.findById(docId)).thenReturn(Optional.of(shared));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                Document.builder()
                        .text("shared chunk")
                        .metadata(java.util.Map.of("chunkId", 1L, "documentId", docId))
                        .score(0.9)
                        .build()));
        when(chunkRepository.findKeywordSearchInDocument(anyString(), anyLong(), anyInt()))
                .thenReturn(List.of());

        assertFalse(retriever.retrieve("policy", guest, 5, docId).isEmpty(),
                "GUEST must retain document-scoped RAG over public/shared knowledge");
    }

    private static Document chunkDoc(Long chunkId, Long documentId, double score) {
        return Document.builder()
                .text("chunk " + chunkId)
                .metadata(java.util.Map.of("chunkId", chunkId, "documentId", documentId))
                .score(score)
                .build();
    }

    /**
     * Structural guard: the semantic-only overload that once existed on
     * {@link RetrieverService} (and bypassed user-aware authorization) was removed in
     * 8112f1f. This prevents a semantic-only retrieval entry point being reintroduced,
     * since every externally reachable retrieval path must take the requesting user.
     */
    @Test
    void retrieverService_hasNoPublicRetrievalPathThatOmitsTheRequestingUser() {
        for (java.lang.reflect.Method method : RetrieverService.class.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())
                    || !method.getName().startsWith("retrieve")) {
                continue;
            }
            boolean takesUser = java.util.Arrays.stream(method.getParameterTypes())
                    .anyMatch(com.sakh.entity.User.class::equals);
            assertTrue(takesUser,
                    "Public retrieval method '" + method.getName()
                            + "' must accept the requesting User so centralized authorization applies");
        }
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
        when(chunkRepository.findKeywordSearchGlobal(anyString(), anyInt(), anyList(), anyString(),
                anyBoolean(), anyBoolean(), anyBoolean()))
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
