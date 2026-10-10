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

        // Non-admins need department membership for department-scoped documents
        if (document.getDepartment() == null) {
            throw new ResourceNotFoundException("Document not found");
        }

        // Check department visibility: own department + ancestors
        List<Long> visibleDepartmentIds = getVisibleDepartmentIds(currentUser);
        if (!visibleDepartmentIds.contains(document.getDepartment().getId())) {
            // Department visibility failed - but EMPLOYEE can still see their own uploads
            if ("EMPLOYEE".equals(userRole)
                    && document.getUploadedBy() != null
                    && currentUser.getEmail() != null
                    && document.getUploadedBy().getEmail().equalsIgnoreCase(currentUser.getEmail())) {
                return;
            }
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
     * For Phase 2, LLM_CAN_PROCESS = USER_CAN_VIEW, with one additional and
     * unconditional constraint: the document must be READY.
     *
     * <p>Readiness is checked before any role or scope decision, so it applies to
     * every role including ADMIN, and to every access scope including
     * {@link AccessScope#ALL}. Sharing a document with everyone makes it visible;
     * it never makes incomplete or failed content eligible for the LLM.
     *
     * <p>This deliberately does NOT affect {@link #requireReadable}, which continues to
     * exempt ADMIN so that document management (preview, details, versions, download,
     * status inspection) still works for documents that are not yet READY. Management
     * permission and LLM-processing eligibility are deliberately separate.
     *
     * @return {@code true} only for a READY document the user may access; {@code false}
     *         for a null document or any non-READY document
     */
    public boolean isAccessibleForRag(Document document, User user) {
        if (document == null) {
            return false;
        }

        // LLM processing eligibility: no role bypasses document readiness.
        if (document.getStatus() != DocumentStatus.READY) {
            return false;
        }

        String userRole = user.getRole() != null ? user.getRole().getName() : "";

        // ADMIN can access every READY document, regardless of department or scope
        if ("ADMIN".equals(userRole)) {
            return true;
        }

        // accessScope = ALL is visible to all authenticated users (readiness already enforced above)
        if (document.getAccessScope() == AccessScope.ALL) {
            return true;
        }

        // Document must have a department for department-scoped access
        if (document.getDepartment() == null) {
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
     *
     * <p>This is a recall filter for candidate generation, not the authorization
     * boundary. It is a permissive superset of {@link #isAccessibleForRag}: the
     * shared-scope clause and the own-upload clause may admit chunks that the
     * role or document status later rejects, which is safe because every merged
     * chunk is re-checked by {@code isAccessibleForRag} before it reaches the LLM.
     */
    public String buildVectorFilterExpression(User user) {
        String role = user.getRole() != null ? user.getRole().getName() : "";
        Long departmentId = user.getDepartment() != null ? user.getDepartment().getId() : null;
        String email = user.getEmail();

        if ("ADMIN".equals(role)) {
            return null;
        }

        String escapedEmail = escapeFilterLiteral(email);

        if (departmentId == null) {
            // User has no department - shared documents plus their own uploads
            return "accessScope == 'ALL' || uploadedBy == '" + escapedEmail + "'";
        }

        // Get visible department IDs (own + ancestors)
        List<Long> visibleDeptIds = getVisibleDepartmentIds(user);

        if (visibleDeptIds.isEmpty() || visibleDeptIds.contains(-1L)) {
            return "accessScope == 'ALL' || uploadedBy == '" + escapedEmail + "'";
        }

        // Build filter: shared documents OR departmentId == ... (OR-chained) OR uploadedBy == email.
        // NOTE: the Spring AI filter expression grammar used by SearchRequest does not support
        // the "in (...)" operator, so the department set is emitted as OR-joined equalities.
        String deptClauses = visibleDeptIds.stream()
                .distinct()
                .map(id -> "departmentId == " + id)
                .collect(Collectors.joining(" || "));

        return "accessScope == 'ALL' || " + deptClauses + " || uploadedBy == '" + escapedEmail + "'";
    }

    /**
     * Escapes a value for the Spring AI filter-expression text grammar.
     *
     * <p>Unlike SQL, this grammar escapes with a backslash rather than by doubling
     * the quote, so a naive {@code replace("'", "''")} produces an expression the
     * parser rejects. Backslashes are escaped first to keep the escaping total.
     */
    private static String escapeFilterLiteral(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}