package com.sakh.rag;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Compatibility and crowding regressions proven against the real PgVectorStore
 * and the real database, rather than against a mocked VectorStore that would
 * ignore the filter expression entirely.
 */
@SpringBootTest
@ActiveProfiles("test")
class RagRetrievalCompatibilityIntegrationTest {

    private static final int DIMENSIONS = 3072;

    @Autowired
    private RetrieverService retrieverService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @MockBean
    private QueryRewriter queryRewriter;

    @MockBean
    private org.springframework.ai.embedding.EmbeddingModel embeddingModel;

    private final List<Long> createdDocumentIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdDepartmentIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(queryRewriter.rewrite(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(embeddingModel.embed(anyString())).thenReturn(queryVector());
    }

    @AfterEach
    void cleanUp() {
        // Raw JDBC so cleanup does not require an ambient JPA transaction.
        for (Long docId : createdDocumentIds) {
            jdbcTemplate.update("DELETE FROM vector_store WHERE metadata ->> 'documentId' = ?", String.valueOf(docId));
            jdbcTemplate.update("DELETE FROM chunks WHERE document_id = ?", docId);
        }
        for (Long docId : createdDocumentIds) {
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

    /**
     * Legacy-vector regression.
     *
     * <p>A READY AccessScope.ALL document in an unrelated department, uploaded by
     * somebody else, whose vector predates the accessScope metadata field. Before
     * the V11 backfill the vector matches none of the semantic filter clauses and is
     * unreachable; after the backfill it is retrievable by an authorized requester.
     *
     * <p>No row is inserted into {@code chunks} for this document, so the keyword
     * path cannot contribute it - the semantic path is exercised in isolation.
     */
    @Test
    void legacyVectorWithoutAccessScope_isUnreachableUntilBackfilled_thenRetrievable() {
        Department requesterDept = newDepartment("legacy-requester-dept");
        Department unrelatedDept = newDepartment("legacy-unrelated-dept");
        User requester = newUser("legacy-requester@test.com", "EMPLOYEE", requesterDept);
        User owner = newUser("legacy-owner@test.com", "EMPLOYEE", unrelatedDept);

        Document shared = newDocument("legacy shared policy", unrelatedDept, owner,
                AccessScope.ALL, DocumentStatus.READY);

        insertVector(shared.getId(), unrelatedDept.getId(), owner.getEmail(),
                "zebrafish shared policy text", /* includeAccessScope= */ false);

        List<org.springframework.ai.document.Document> before =
                retrieverService.retrieve("zebrafish", requester, 5);
        assertFalse(containsDocument(before, shared.getId()),
                "Precondition: a vector lacking accessScope must not match the shared-scope clause");

        // Apply exactly the statement shipped in V11.
        jdbcTemplate.update("""
                UPDATE vector_store vs
                SET metadata = vs.metadata || jsonb_build_object('accessScope', d.access_scope)
                FROM documents d
                WHERE vs.metadata ? 'documentId'
                  AND NOT (vs.metadata ? 'accessScope')
                  AND (vs.metadata ->> 'documentId') ~ '^[0-9]+$'
                  AND d.id = (vs.metadata ->> 'documentId')::bigint
                """);

        List<org.springframework.ai.document.Document> after =
                retrieverService.retrieve("zebrafish", requester, 5);
        assertTrue(containsDocument(after, shared.getId()),
                "After the V11 backfill the legacy shared vector must be retrievable by an authorized requester");
    }

    /**
     * A legacy vector that belongs to a document the requester may NOT see must
     * stay unreachable. The backfill must not turn it into a leak.
     */
    @Test
    void legacyVectorForUnauthorizedDepartmentScopedDocument_staysUnreachable() {
        Department requesterDept = newDepartment("crowd-requester-dept");
        Department unrelatedDept = newDepartment("crowd-unrelated-dept");
        User requester = newUser("crowd-requester@test.com", "EMPLOYEE", requesterDept);
        User owner = newUser("crowd-owner@test.com", "EMPLOYEE", unrelatedDept);

        Document foreign = newDocument("foreign policy", unrelatedDept, owner,
                AccessScope.DEPARTMENT, DocumentStatus.READY);

        insertVector(foreign.getId(), unrelatedDept.getId(), owner.getEmail(),
                "wombat department policy text", false);

        jdbcTemplate.update("""
                UPDATE vector_store vs
                SET metadata = vs.metadata || jsonb_build_object('accessScope', d.access_scope)
                FROM documents d
                WHERE vs.metadata ? 'documentId'
                  AND NOT (vs.metadata ? 'accessScope')
                  AND (vs.metadata ->> 'documentId') ~ '^[0-9]+$'
                  AND d.id = (vs.metadata ->> 'documentId')::bigint
                """);

        List<org.springframework.ai.document.Document> results =
                retrieverService.retrieve("wombat", requester, 5);

        assertFalse(containsDocument(results, foreign.getId()),
                "A department-scoped document in an unrelated department must never reach the prompt");
    }

    /**
     * A shared document that is not READY must stay unreachable even though the
     * shared-scope clause matches it at the vector layer.
     */
    @Test
    void sharedButNotReadyDocument_neverReachesThePrompt() {
        Department requesterDept = newDepartment("status-requester-dept");
        Department unrelatedDept = newDepartment("status-unrelated-dept");
        User requester = newUser("status-requester@test.com", "EMPLOYEE", requesterDept);
        User owner = newUser("status-owner@test.com", "EMPLOYEE", unrelatedDept);

        Document processing = newDocument("processing shared policy", unrelatedDept, owner,
                AccessScope.ALL, DocumentStatus.PROCESSING);

        insertVector(processing.getId(), unrelatedDept.getId(), owner.getEmail(),
                "aardvark shared processing text", true);
        insertChunk(processing.getId(), "aardvark shared processing chunk text");

        List<org.springframework.ai.document.Document> results =
                retrieverService.retrieve("aardvark", requester, 5);

        assertFalse(containsDocument(results, processing.getId()),
                "A non-READY shared document must be filtered out before prompt construction");
    }

    /**
     * Keyword crowding regression.
     *
     * <p>Unauthorized chunks outrank the authorized one, and there are more of them
     * than the keyword LIMIT. Because the authorization predicate is applied in SQL
     * before LIMIT, the authorized chunk must still be retrieved. With the previous
     * LIMIT-then-filter behaviour this assertion fails.
     */
    @Test
    void keywordSearch_unauthorizedTopRankedRowsDoNotCrowdOutAuthorizedMatches() {
        Department requesterDept = newDepartment("keyword-requester-dept");
        Department unrelatedDept = newDepartment("keyword-unrelated-dept");
        User requester = newUser("keyword-requester@test.com", "EMPLOYEE", requesterDept);
        User owner = newUser("keyword-owner@test.com", "EMPLOYEE", unrelatedDept);

        // Six unauthorized chunks that rank above the authorized one (repeated term).
        for (int i = 0; i < 6; i++) {
            Document foreign = newDocument("noise doc " + i, unrelatedDept, owner,
                    AccessScope.DEPARTMENT, DocumentStatus.READY);
            insertChunk(foreign.getId(), "pangolin pangolin pangolin pangolin pangolin pangolin noise " + i);
        }

        Document authorized = newDocument("authorized policy", requesterDept, owner,
                AccessScope.DEPARTMENT, DocumentStatus.READY);
        insertChunk(authorized.getId(), "pangolin single mention of the term");

        // topK=2 => keyword fetchSize = 4, fewer than the 6 higher-ranked unauthorized rows.
        int keywordFetchSize = 4;

        // Demonstrate the pre-fix behaviour: when LIMIT is applied before authorization,
        // the top-N window is filled entirely by unauthorized rows and the authorized
        // chunk is crowded out. This is the exact regression the SQL predicate prevents.
        Long authorizedChunkId = jdbcTemplate.queryForObject(
                "SELECT id FROM chunks WHERE document_id = ? ORDER BY id LIMIT 1",
                Long.class, authorized.getId());

        List<Long> preFixWindow = jdbcTemplate.queryForList(
                "SELECT c.id FROM chunks c "
                        + "JOIN documents d ON c.document_id = d.id "
                        + "JOIN users u ON d.uploaded_by = u.id "
                        + "WHERE to_tsvector('english', c.chunk_text) @@ plainto_tsquery('english', ?) "
                        + "AND d.status = 'READY' "
                        + "ORDER BY ts_rank_cd(to_tsvector('english', c.chunk_text), plainto_tsquery('english', ?)) DESC "
                        + "LIMIT ?",
                Long.class, "pangolin", "pangolin", keywordFetchSize);

        assertFalse(preFixWindow.contains(authorizedChunkId),
                "Precondition: with LIMIT before authorization the authorized chunk is crowded out");

        List<org.springframework.ai.document.Document> results =
                retrieverService.retrieve("pangolin", requester, 2);

        assertTrue(containsDocument(results, authorized.getId()),
                "Authorized matches must not be crowded out of the candidate pool by unauthorized rows");
    }

    // --- helpers ---

    private static boolean containsDocument(List<org.springframework.ai.document.Document> docs, Long documentId) {
        return docs.stream().anyMatch(d -> {
            Object v = d.getMetadata().get("documentId");
            return v instanceof Number n && n.longValue() == documentId;
        });
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
                .orElseGet(() -> roleRepository.saveAndFlush(new Role(null, roleName, null)));

        User u = new User();
        u.setFirstName("Test");
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
        doc.setFileSize(128L);
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

    private void insertVector(Long documentId, Long departmentId, String uploaderEmail,
                              String content, boolean includeAccessScope) {
        String metadata = includeAccessScope
                ? String.format("{\"chunkId\":%d,\"documentId\":%d,\"departmentId\":%d,\"uploadedBy\":\"%s\",\"accessScope\":\"ALL\"}",
                        documentId * 1000, documentId, departmentId, uploaderEmail)
                : String.format("{\"chunkId\":%d,\"documentId\":%d,\"departmentId\":%d,\"uploadedBy\":\"%s\"}",
                        documentId * 1000, documentId, departmentId, uploaderEmail);

        jdbcTemplate.update(
                "INSERT INTO vector_store (content, metadata, embedding) VALUES (?, ?::jsonb, ?::vector)",
                content, metadata, vectorLiteral());
    }

    private void insertChunk(Long documentId, String text) {
        jdbcTemplate.update(
                "INSERT INTO chunks (document_id, chunk_index, chunk_text, page_number, section_title, token_count) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                documentId, 0, text, 1, "Section", text.split("\\s+").length);
    }

    private static float[] queryVector() {
        float[] v = new float[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            v[i] = 0.01f;
        }
        return v;
    }

    private static String vectorLiteral() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < DIMENSIONS; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("0.01");
        }
        return sb.append(']').toString();
    }
}
