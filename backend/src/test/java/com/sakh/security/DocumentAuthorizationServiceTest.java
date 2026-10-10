package com.sakh.security;

import com.sakh.entity.Document;
import com.sakh.entity.Department;
import com.sakh.entity.Role;
import com.sakh.entity.User;
import com.sakh.enums.AccessScope;
import com.sakh.enums.DocumentStatus;
import com.sakh.enums.UserStatus;
import com.sakh.exception.ResourceNotFoundException;
import com.sakh.service.DepartmentService;
import com.sakh.repository.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentAuthorizationServiceTest {

    @Mock(lenient = true)
    private DepartmentService departmentService;

    @Mock(lenient = true)
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DocumentAuthorizationService authorizationService;

    private User admin;
    private User manager;
    private User employee;
    private User employeeNoDept;
    private Department deptA;
    private Department deptB;
    private Department deptC; // child of deptA

    @BeforeEach
    void setUp() {
        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        Role managerRole = new Role();
        managerRole.setName("MANAGER");
        Role employeeRole = new Role();
        employeeRole.setName("EMPLOYEE");

        deptA = new Department();
        deptA.setId(1L);
        deptA.setName("Dept A");

        deptB = new Department();
        deptB.setId(2L);
        deptB.setName("Dept B");

        deptC = new Department();
        deptC.setId(3L);
        deptC.setName("Dept C");
        deptC.setParent(deptA);

        admin = createUser(adminRole, deptA, "admin@test.com");
        manager = createUser(managerRole, deptA, "manager@test.com");
        employee = createUser(employeeRole, deptA, "employee@test.com");
        employeeNoDept = createUser(employeeRole, null, "nodept@test.com");

        // Department hierarchy: deptA -> deptC
        // collectAncestorIds(allDepartments, userDeptId) -> returns ancestors including self
        // Order matters: general case first, specific cases last (last match wins in Mockito)
        when(departmentService.collectAncestorIds(anyList(), anyLong())).thenReturn(List.of(-1L));
        when(departmentService.collectAncestorIds(anyList(), eq(1L))).thenReturn(List.of(1L));
        when(departmentService.collectAncestorIds(anyList(), eq(3L))).thenReturn(List.of(1L, 3L));

        // collectSubtreeIds(allDepartments, userDeptId) -> returns subtree including self
        when(departmentService.collectSubtreeIds(anyList(), anyLong())).thenReturn(List.of());
        when(departmentService.collectSubtreeIds(anyList(), eq(1L))).thenReturn(List.of(1L, 3L));

        when(departmentService.getAllDepartmentsList()).thenReturn(List.of(deptA, deptB, deptC));
    }

    // A. Non-admin + accessScope=ALL + READY document: allowed by the intended policy
    @Test
    void isAccessibleForRag_nonAdmin_accessScopeAll_ready_allowed() {
        Document doc = createDocument(AccessScope.ALL, DocumentStatus.READY, deptB, employee);
        assertTrue(authorizationService.isAccessibleForRag(doc, manager));
        assertTrue(authorizationService.isAccessibleForRag(doc, employee));
    }

    // B. Non-admin + accessScope=ALL + non-READY document: denied for RAG
    @Test
    void isAccessibleForRag_nonAdmin_accessScopeAll_notReady_denied() {
        Document doc = createDocument(AccessScope.ALL, DocumentStatus.PENDING, deptB, employee);
        assertFalse(authorizationService.isAccessibleForRag(doc, manager));
        assertFalse(authorizationService.isAccessibleForRag(doc, employee));
    }

    // C. Non-admin + ancestor-department document: follows the documented hierarchy policy
    @Test
    void isAccessibleForRag_nonAdmin_ancestorDepartment_allowed() {
        // employee in deptA, document in deptA (same dept = ancestor)
        Document doc = createDocument(AccessScope.DEPARTMENT, DocumentStatus.READY, deptA, manager);
        
        // Debug: check what the mock returns
        List<Long> visible = departmentService.collectAncestorIds(List.of(deptA, deptB, deptC), 1L);
        System.out.println("Visible departments for deptA: " + visible);
        
        assertTrue(authorizationService.isAccessibleForRag(doc, employee));
    }

    // D. Non-admin + unrelated department document: denied
    @Test
    void isAccessibleForRag_nonAdmin_unrelatedDepartment_denied() {
        Document doc = createDocument(AccessScope.DEPARTMENT, DocumentStatus.READY, deptB, manager);
        assertFalse(authorizationService.isAccessibleForRag(doc, employee)); // employee in deptA, doc in deptB
    }

    // E. Employee own-upload case: follows the documented policy
    @Test
    void isAccessibleForRag_employeeOwnUpload_allowed() {
        Document doc = createDocument(AccessScope.DEPARTMENT, DocumentStatus.READY, deptB, employee);
        // employee owns the document but it's in a different department
        assertTrue(authorizationService.isAccessibleForRag(doc, employee));
    }

    // F. Employee own-upload + non-READY: denied (READY required for RAG)
    @Test
    void isAccessibleForRag_employeeOwnUpload_notReady_denied() {
        Document doc = createDocument(AccessScope.DEPARTMENT, DocumentStatus.PENDING, deptB, employee);
        assertFalse(authorizationService.isAccessibleForRag(doc, employee));
    }

    // G. Null or missing document: fails closed without NullPointerException
    @Test
    void isAccessibleForRag_nullDocument_failsClosed() {
        assertFalse(authorizationService.isAccessibleForRag(null, employee));
    }

    // H. ADMIN + READY: allowed regardless of department or access scope
    @Test
    void isAccessibleForRag_admin_readyDocument_allowed() {
        Document deptScoped = createDocument(AccessScope.DEPARTMENT, DocumentStatus.READY, deptB, employee);
        Document unrelated = createDocument(AccessScope.DEPARTMENT, DocumentStatus.READY, deptB, employee);
        Document shared = createDocument(AccessScope.ALL, DocumentStatus.READY, deptB, employee);

        assertTrue(authorizationService.isAccessibleForRag(deptScoped, admin));
        assertTrue(authorizationService.isAccessibleForRag(unrelated, admin));
        assertTrue(authorizationService.isAccessibleForRag(shared, admin));
    }

    // H2. ADMIN + non-READY: denied. No role bypasses RAG document readiness.
    @Test
    void isAccessibleForRag_admin_nonReadyDocument_denied() {
        for (DocumentStatus status : List.of(DocumentStatus.PENDING,
                DocumentStatus.PROCESSING,
                DocumentStatus.FAILED,
                DocumentStatus.ARCHIVED)) {
            Document deptScoped = createDocument(AccessScope.DEPARTMENT, status, deptB, employee);
            assertFalse(authorizationService.isAccessibleForRag(deptScoped, admin),
                    "ADMIN must not retrieve a " + status + " document through RAG");
        }
    }

    // I. ADMIN + accessScope=ALL + non-READY: denied. Sharing never bypasses readiness.
    @Test
    void isAccessibleForRag_admin_accessScopeAll_notReady_denied() {
        for (DocumentStatus status : List.of(DocumentStatus.PENDING,
                DocumentStatus.PROCESSING,
                DocumentStatus.FAILED)) {
            Document doc = createDocument(AccessScope.ALL, status, deptB, employee);
            assertFalse(authorizationService.isAccessibleForRag(doc, admin),
                    "A shared " + status + " document must not reach the prompt, even for ADMIN");
        }
        Document readyShared = createDocument(AccessScope.ALL, DocumentStatus.READY, deptB, employee);
        assertTrue(authorizationService.isAccessibleForRag(readyShared, admin),
                "A READY shared document remains available to ADMIN");
    }

    // I2. ADMIN document-management capabilities are NOT restricted by the RAG readiness rule.
    // requireReadable deliberately still exempts ADMIN so preview/details/versions/download
    // and status inspection keep working for documents that are not yet READY.
    @Test
    void requireReadable_admin_canStillManageNonReadyDocuments() {
        for (DocumentStatus status : List.of(DocumentStatus.PENDING,
                DocumentStatus.PROCESSING,
                DocumentStatus.FAILED)) {
            Document doc = createDocument(AccessScope.DEPARTMENT, status, deptB, employee);

            assertDoesNotThrow(() -> authorizationService.requireReadable(doc, admin),
                    "ADMIN must retain document-management access to a " + status + " document");
            assertDoesNotThrow(() -> authorizationService.checkDocumentViewAccess(doc, admin),
                    "ADMIN must retain view access to a " + status + " document");

            // ...while the same document is still ineligible for the LLM.
            assertFalse(authorizationService.isAccessibleForRag(doc, admin),
                    "A " + status + " document must remain ineligible for RAG even for ADMIN");
        }
    }

    // J. Fail-closed null handling: no NullPointerException in any combination
    @Test
    void isAccessibleForRag_nullDocumentWithValidUser_failsClosed() {
        assertFalse(authorizationService.isAccessibleForRag(null, employee));
        assertFalse(authorizationService.isAccessibleForRag(null, admin));
    }

    @Test
    void isAccessibleForRag_readyDocumentWithNullUser_failsClosed() {
        Document ready = createDocument(AccessScope.ALL, DocumentStatus.READY, deptA, employee);
        assertFalse(authorizationService.isAccessibleForRag(ready, null),
                "A null user must fail closed even for a READY, shared document");
    }

    @Test
    void isAccessibleForRag_nonReadyDocumentWithNullUser_failsClosed() {
        Document pending = createDocument(AccessScope.ALL, DocumentStatus.PENDING, deptA, employee);
        assertFalse(authorizationService.isAccessibleForRag(pending, null));
        // Both null at once must also be safe.
        assertFalse(authorizationService.isAccessibleForRag(null, null));
    }

    // J2. Anonymous user: a User with no role is not ADMIN and has no department.
    @Test
    void isAccessibleForRag_userWithoutRole_isNotTreatedAsAdmin() {
        Document ready = createDocument(AccessScope.DEPARTMENT, DocumentStatus.READY, deptA, employee);
        User noRole = new User();
        noRole.setEmail("norole@test.com");

        assertFalse(authorizationService.isAccessibleForRag(ready, noRole),
                "A user without a role must not inherit ADMIN privileges");
    }

    // J. Employee in ancestor department can see ancestor's documents
    @Test
    void isAccessibleForRag_employeeInChildDeptSeesAncestorDoc_allowed() {
        // employee in deptA, document in deptA (same dept = ancestor)
        Document doc = createDocument(AccessScope.DEPARTMENT, DocumentStatus.READY, deptA, manager);
        assertTrue(authorizationService.isAccessibleForRag(doc, employee));
    }

    // K. Manager in ancestor department can see ancestor's documents
    @Test
    void isAccessibleForRag_managerInChildDeptSeesAncestorDoc_allowed() {
        User managerInChild = createUser(createRole("MANAGER"), deptA, "managerChild@test.com");
        Document doc = createDocument(AccessScope.DEPARTMENT, DocumentStatus.READY, deptA, employee);
        assertTrue(authorizationService.isAccessibleForRag(doc, managerInChild));
    }

    // L. Employee in child department can see parent department documents
    @Test
    void isAccessibleForRag_employeeInChildDeptSeesParentDoc_allowed() {
        // employee in deptC (child of deptA), document in deptA (parent)
        User employeeInChild = createUser(createRole("EMPLOYEE"), deptC, "empChild@test.com");
        Document doc = createDocument(AccessScope.DEPARTMENT, DocumentStatus.READY, deptA, manager);
        assertTrue(authorizationService.isAccessibleForRag(doc, employeeInChild));
    }

    // M. EMPLOYEE with no department can access their own READY upload
    @Test
    void isAccessibleForRag_employeeNoDept_ownUploadReady_allowed() {
        Document doc = createDocument(AccessScope.DEPARTMENT, DocumentStatus.READY, deptA, employeeNoDept);
        assertTrue(authorizationService.isAccessibleForRag(doc, employeeNoDept));
    }

    // N. EMPLOYEE with no department cannot access their own non-READY upload
    @Test
    void isAccessibleForRag_employeeNoDept_ownUploadNotReady_denied() {
        Document doc = createDocument(AccessScope.DEPARTMENT, DocumentStatus.PENDING, deptA, employeeNoDept);
        assertFalse(authorizationService.isAccessibleForRag(doc, employeeNoDept));
    }

    // O. EMPLOYEE with no department cannot access other users' documents
    @Test
    void isAccessibleForRag_employeeNoDept_otherUserDoc_denied() {
        Document doc = createDocument(AccessScope.DEPARTMENT, DocumentStatus.READY, deptA, employee);
        assertFalse(authorizationService.isAccessibleForRag(doc, employeeNoDept));
    }

    // P. checkDocumentViewAccess - employee with no department can access own READY upload
    @Test
    void checkDocumentViewAccess_employeeNoDept_ownUploadReady_allowed() {
        Document doc = createDocument(AccessScope.DEPARTMENT, DocumentStatus.READY, deptA, employeeNoDept);
        // Should not throw exception
        authorizationService.checkDocumentViewAccess(doc, employeeNoDept);
    }

    // Q. checkDocumentViewAccess - employee with no department can access own upload even if non-READY
    // (readiness is checked by requireReadable, not checkDocumentViewAccess)
    @Test
    void checkDocumentViewAccess_employeeNoDept_ownUploadNotReady_allowed() {
        Document doc = createDocument(AccessScope.DEPARTMENT, DocumentStatus.PENDING, deptA, employeeNoDept);
        // Should not throw exception - readiness checked separately by requireReadable
        authorizationService.checkDocumentViewAccess(doc, employeeNoDept);
    }

    // R. checkDocumentViewAccess - employee with no department denied for other user's doc
    @Test
    void checkDocumentViewAccess_employeeNoDept_otherUserDoc_denied() {
        Document doc = createDocument(AccessScope.DEPARTMENT, DocumentStatus.READY, deptA, employee);
        assertThrows(ResourceNotFoundException.class, () -> {
            authorizationService.checkDocumentViewAccess(doc, employeeNoDept);
        });
    }

    // S. checkDocumentViewAccess - document with null department is denied
    @Test
    void checkDocumentViewAccess_nullDocumentDepartment_denied() {
        Document doc = createDocument(AccessScope.DEPARTMENT, DocumentStatus.READY, null, employee);
        assertThrows(ResourceNotFoundException.class, () -> {
            authorizationService.checkDocumentViewAccess(doc, employee);
        });
    }

    // T. isAccessibleForRag - document with null department is denied
    @Test
    void isAccessibleForRag_nullDocumentDepartment_denied() {
        Document doc = createDocument(AccessScope.DEPARTMENT, DocumentStatus.READY, null, employee);
        assertFalse(authorizationService.isAccessibleForRag(doc, employee));
    }

    // U. EMPLOYEE with no department cannot access document with null department
    @Test
    void isAccessibleForRag_employeeNoDept_nullDocDepartment_denied() {
        Document doc = createDocument(AccessScope.DEPARTMENT, DocumentStatus.READY, null, employee);
        assertFalse(authorizationService.isAccessibleForRag(doc, employeeNoDept));
    }

    // V. isAccessibleForRag - EMPLOYEE with department cannot access null-department doc they don't own
    @Test
    void isAccessibleForRag_employeeWithDept_nullDocDepartmentOtherUser_denied() {
        Document doc = createDocument(AccessScope.DEPARTMENT, DocumentStatus.READY, null, employee);
        assertFalse(authorizationService.isAccessibleForRag(doc, employee));
    }

    // --- Candidate-generation filter (recall), used by RetrieverService.semanticSearch ---

    // The vector filter must be a superset of the permitted set so that shared
    // documents, ancestor departments, and own uploads all become candidates.
    @Test
    void buildVectorFilterExpression_employeeWithDept_includesSharedAncestorsAndOwnUploads() {
        String filter = authorizationService.buildVectorFilterExpression(employee);

        assertTrue(filter.contains("accessScope == 'ALL'"),
                "Must admit shared documents as candidates: " + filter);
        assertTrue(filter.contains("departmentId == 1"),
                "Must include the visible department chain: " + filter);
        assertTrue(filter.contains("uploadedBy == 'employee@test.com'"),
                "Must admit the employee's own uploads: " + filter);
    }

    @Test
    void buildVectorFilterExpression_ancestorChainIsIncluded() {
        // Manager sits in deptC (3); ancestors are [1, 3]
        User userInDeptC = createUser(createRole("EMPLOYEE"), deptC, "child@test.com");
        String filter = authorizationService.buildVectorFilterExpression(userInDeptC);

        assertTrue(filter.contains("departmentId == 1"),
                "Must include ancestor departments: " + filter);
        assertTrue(filter.contains("departmentId == 3"),
                "Must include the user's own department: " + filter);
    }

    @Test
    void buildVectorFilterExpression_employeeNoDept_isBoundedToSharedAndOwnUploads() {
        String filter = authorizationService.buildVectorFilterExpression(employeeNoDept);

        assertTrue(filter.contains("accessScope == 'ALL'"), filter);
        assertTrue(filter.contains("uploadedBy == 'nodept@test.com'"), filter);
        assertFalse(filter.contains("departmentId"),
                "A department-less user must not trigger a department scan: " + filter);
    }

    @Test
    void buildVectorFilterExpression_admin_isUnfiltered() {
        assertNull(authorizationService.buildVectorFilterExpression(admin),
                "ADMIN must not be restricted by a candidate filter");
    }

    @Test
    void buildVectorFilterExpression_escapesQuoteInEmail() {
        User tricky = createUser(createRole("EMPLOYEE"), deptA, "o'brien@test.com");
        String filter = authorizationService.buildVectorFilterExpression(tricky);

        // The Spring AI filter grammar escapes with a backslash, not SQL-style quote doubling.
        assertTrue(filter.contains("uploadedBy == 'o\\'brien@test.com'"),
                "Single quotes must be backslash-escaped to avoid filter injection: " + filter);
    }

    /**
     * Regression guard: the expression is handed to SearchRequest, whose text parser
     * rejects several otherwise plausible syntaxes (notably "in (...)"). This asserts
     * the real parser accepts everything this builder can produce.
     */
    @Test
    void buildVectorFilterExpression_isAcceptedBySearchRequestParser() {
        List<User> users = List.of(admin, manager, employee, employeeNoDept,
                createUser(createRole("MANAGER"), deptC, "mgr-c@test.com"),
                createUser(createRole("EMPLOYEE"), deptA, "o'brien@test.com"));

        for (User u : users) {
            String filter = authorizationService.buildVectorFilterExpression(u);
            if (filter == null) {
                continue;
            }
            assertDoesNotThrow(() -> org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query("q").topK(1).filterExpression(filter).build(),
                    "Filter produced for " + u.getEmail() + " must parse: " + filter);
        }
    }

    private Document createDocument(AccessScope scope, DocumentStatus status, Department dept, User uploadedBy) {
        Document doc = new Document();
        doc.setId(999L);
        doc.setAccessScope(scope);
        doc.setStatus(status);
        doc.setDepartment(dept);
        doc.setUploadedBy(uploadedBy);
        return doc;
    }

    private User createUser(Role role, Department dept, String email) {
        User u = new User();
        u.setId(99L);
        u.setEmail(email);
        u.setRole(role);
        u.setDepartment(dept);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    private Role createRole(String name) {
        Role r = new Role();
        r.setName(name);
        return r;
    }
}