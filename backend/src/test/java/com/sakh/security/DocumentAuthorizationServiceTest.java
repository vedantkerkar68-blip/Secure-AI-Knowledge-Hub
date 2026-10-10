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

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    // H. ADMIN behavior: matches the documented policy
    @Test
    void isAccessibleForRag_admin_alwaysAllowed() {
        Document docNotReady = createDocument(AccessScope.DEPARTMENT, DocumentStatus.PENDING, deptB, employee);
        Document docUnrelated = createDocument(AccessScope.DEPARTMENT, DocumentStatus.READY, deptB, employee);
        Document docAll = createDocument(AccessScope.ALL, DocumentStatus.PENDING, deptB, employee);

        assertTrue(authorizationService.isAccessibleForRag(docNotReady, admin));
        assertTrue(authorizationService.isAccessibleForRag(docUnrelated, admin));
        assertTrue(authorizationService.isAccessibleForRag(docAll, admin));
    }

    // I. ADMIN + accessScope=ALL + non-READY: allowed (admin bypasses readiness)
    @Test
    void isAccessibleForRag_admin_accessScopeAll_notReady_allowed() {
        Document doc = createDocument(AccessScope.ALL, DocumentStatus.PENDING, deptB, employee);
        assertTrue(authorizationService.isAccessibleForRag(doc, admin));
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