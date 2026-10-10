package com.sakh.security;

import com.sakh.entity.Document;
import com.sakh.entity.User;
import com.sakh.enums.AccessScope;
import com.sakh.enums.DocumentStatus;
import com.sakh.exception.ResourceNotFoundException;
import com.sakh.repository.DepartmentRepository;
import com.sakh.service.DepartmentService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralized document authorization logic.
 * 
 * This is the single source of truth for document access decisions,
 * used by both DocumentService (API) and RetrieverService (RAG).
 * 
 * Policy reference: docs/07_ACCESS_CONTROL_POLICY.md
 */
@Service
public class DocumentAuthorizationService {

    private final DepartmentService departmentService;
    private final DepartmentRepository departmentRepository;

    public DocumentAuthorizationService(DepartmentService departmentService,
                                        DepartmentRepository departmentRepository) {
        this.departmentService = departmentService;
        this.departmentRepository = departmentRepository;
    }

    /**
     * Checks if a user can view/download a document (USER_CAN_VIEW).
     * Throws ResourceNotFoundException if access is denied (404 semantics).
     */
    public void checkDocumentViewAccess(Document document, User currentUser) {
        String userRole = currentUser.getRole() != null ? currentUser.getRole().getName() : "";

        // ADMIN can access all documents
        if ("ADMIN".equals(userRole)) {
            return;
        }

        // Documents shared with everyone are visible to all authenticated users
        if (document.getAccessScope() == AccessScope.ALL) {
            return;
        }

        // Non-admins need department membership
        if (currentUser.getDepartment() == null || document.getDepartment() == null) {
            throw new ResourceNotFoundException("Document not found");
        }

        // Check department visibility: own department + ancestors
        List<Long> visibleDepartmentIds = getVisibleDepartmentIds(currentUser);
        if (!visibleDepartmentIds.contains(document.getDepartment().getId())) {
            throw new ResourceNotFoundException("Document not found");
        }
    }

    /**
     * Checks if a non-admin user can read a document (requires READY status).
     * Throws ResourceNotFoundException if not readable (404 semantics).
     */
    public void requireReadable(Document document, User currentUser) {
        String userRole = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        if (!"ADMIN".equals(userRole) 
                && document.getStatus() != DocumentStatus.READY) {
            throw new ResourceNotFoundException("Document not found");
        }
    }

    /**
     * Validates that the current user can upload to the given department with the given scope.
     * Throws AccessDeniedException if not allowed.
     */
    public void validateUploadTarget(User user, com.sakh.entity.Department department, com.sakh.enums.AccessScope scope) {
        String userRole = user.getRole() != null ? user.getRole().getName() : "";

        if ("ADMIN".equals(userRole)) {
            return;
        }

        if (!"MANAGER".equals(userRole)) {
            throw new AccessDeniedException("Only administrators and managers can upload documents");
        }

        if (scope == AccessScope.ALL) {
            throw new AccessDeniedException("Only administrators can share documents with everyone");
        }

        if (user.getDepartment() == null) {
            throw new AccessDeniedException("Your account is not assigned to a department");
        }

        List<Long> subtreeIds = departmentService.collectSubtreeIds(
                departmentService.getAllDepartmentsList(), user.getDepartment().getId());
        if (!subtreeIds.contains(department.getId())) {
            throw new AccessDeniedException(
                    "You can only upload documents to your own department or its sub-departments");
        }
    }

    /**
     * Returns the department IDs a user is allowed to see documents from.
     * Returns null for ADMIN (no restriction).
     */
    public List<Long> resolveVisibleDepartmentIds(User user, String userRole) {
        if ("ADMIN".equals(userRole)) {
            return null;
        }
        return getVisibleDepartmentIds(user);
    }

    /**
     * Returns the department IDs visible to a non-admin user:
     * own department + all ancestors (documents flow down the tree).
     */
    public List<Long> getVisibleDepartmentIds(User user) {
        if (user.getDepartment() == null) {
            return List.of(-1L); // sentinel for no access
        }
        return departmentService.collectAncestorIds(
                departmentService.getAllDepartmentsList(), user.getDepartment().getId());
    }

    /**
     * Returns the department IDs a user can upload to (MANAGER: own subtree).
     * Returns null for ADMIN (no restriction).
     */
    public List<Long> getUploadableDepartmentIds(User user) {
        String userRole = user.getRole() != null ? user.getRole().getName() : "";
        if ("ADMIN".equals(userRole)) {
            return null;
        }
        if (!"MANAGER".equals(userRole) || user.getDepartment() == null) {
            return List.of();
        }
        return departmentService.collectSubtreeIds(
                departmentService.getAllDepartmentsList(), user.getDepartment().getId());
    }

    /**
     * Checks if a document is accessible for RAG retrieval (LLM_CAN_PROCESS).
     * For Phase 2, LLM_CAN_PROCESS = USER_CAN_VIEW.
     * Returns true if accessible, false otherwise.
     */
    public boolean isAccessibleForRag(Document document, User user) {
        if (document == null) {
            return false;
        }

        String userRole = user.getRole() != null ? user.getRole().getName() : "";

        // ADMIN can access all
        if ("ADMIN".equals(userRole)) {
            return true;
        }

        // accessScope = ALL is visible to all authenticated users, but still requires READY status
        if (document.getAccessScope() == AccessScope.ALL) {
            if (document.getStatus() != DocumentStatus.READY) {
                return false;
            }
            return true;
        }

        // Non-admin users need department membership
        if (user.getDepartment() == null || document.getDepartment() == null) {
            return false;
        }

        // Non-admins may only access READY documents
        if (document.getStatus() != DocumentStatus.READY) {
            return false;
        }

        // Check department visibility: own department + ancestors
        List<Long> visibleDepartmentIds = getVisibleDepartmentIds(user);
        if (!visibleDepartmentIds.contains(document.getDepartment().getId())) {
            // Department visibility failed - but EMPLOYEE can still see their own uploads
            if ("EMPLOYEE".equals(userRole)
                    && document.getUploadedBy() != null
                    && user.getEmail() != null
                    && document.getUploadedBy().getEmail().equalsIgnoreCase(user.getEmail())) {
                return true;
            }
            return false;
        }

        // Department visibility passed - all non-admin roles (MANAGER, EMPLOYEE) can access
        return true;
    }

    /**
     * Builds a pgvector filter expression for the given user.
     * Returns null for ADMIN (no filter).
     */
    public String buildVectorFilterExpression(User user) {
        String role = user.getRole() != null ? user.getRole().getName() : "";
        Long departmentId = user.getDepartment() != null ? user.getDepartment().getId() : null;
        String email = user.getEmail();

        if ("ADMIN".equals(user.getRole() != null ? user.getRole().getName() : "")) {
            return null;
        }

        if (departmentId == null) {
            // User has no department - only own uploads
            return "uploadedBy == '" + email.replace("'", "''") + "'";
        }

        // Get visible department IDs (own + ancestors)
        List<Long> visibleDeptIds = getVisibleDepartmentIds(user);

        if (visibleDeptIds.isEmpty() || visibleDeptIds.contains(-1L)) {
            return "uploadedBy == '" + email.replace("'", "''") + "'";
        }

        // Build filter: departmentId IN (...) || uploadedBy == email
        String deptList = visibleDeptIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
        return "departmentId in (" + deptList + ") || uploadedBy == '" + email.replace("'", "''") + "'";
    }
}