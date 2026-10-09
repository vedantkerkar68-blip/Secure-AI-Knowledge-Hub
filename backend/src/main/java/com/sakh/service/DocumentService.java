package com.sakh.service;

import com.sakh.dto.department.DepartmentResponse;
import com.sakh.dto.document.DocumentListResponse;
import com.sakh.dto.document.DocumentPreviewResponse;
import com.sakh.dto.document.DocumentResponse;
import com.sakh.dto.document.DocumentVersionResponse;
import com.sakh.dto.document.UploadDocumentResponse;
import com.sakh.dto.document.UpdateDocumentStatusRequest;
import com.sakh.dto.document.UpdateDocumentStatusResponse;
import com.sakh.entity.Department;
import com.sakh.entity.Document;
import com.sakh.entity.DocumentMetadata;
import com.sakh.entity.User;
import com.sakh.enums.AccessScope;
import com.sakh.enums.ActivityType;
import com.sakh.enums.DocumentStatus;
import com.sakh.exception.ResourceNotFoundException;
import com.sakh.processing.DocumentProcessingService;
import com.sakh.repository.DepartmentRepository;
import com.sakh.repository.DocumentMetadataRepository;
import com.sakh.repository.DocumentRepository;
import com.sakh.repository.UserRepository;
import com.sakh.security.DocumentAuthorizationService;
import com.sakh.storage.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentMetadataRepository documentMetadataRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final DocumentProcessingService processingService;
    private final ActivityLogService activityLogService;
    private final DepartmentService departmentService;
    private final DocumentAuthorizationService authorizationService;

public DocumentService(DocumentRepository documentRepository,
                       DocumentMetadataRepository documentMetadataRepository,
                       DepartmentRepository departmentRepository,
                       UserRepository userRepository,
                       StorageService storageService,
                       DocumentProcessingService processingService,
                       ActivityLogService activityLogService,
                       DepartmentService departmentService,
                       DocumentAuthorizationService authorizationService) {
        this.documentRepository = documentRepository;
        this.documentMetadataRepository = documentMetadataRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.processingService = processingService;
        this.activityLogService = activityLogService;
        this.departmentService = departmentService;
        this.authorizationService = authorizationService;
    }

    public UploadDocumentResponse uploadDocument(MultipartFile file, Long departmentId, String accessScope) {
        validateFile(file);

        User currentUser = getCurrentUser();

        if (departmentId == null) {
            throw new IllegalArgumentException("Please select a department for this document.");
        }

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + departmentId));

        AccessScope scope = resolveAccessScope(accessScope);
        validateUploadTarget(currentUser, department, scope);

        String storagePath = storageService.store(file);

        // Check if document with same filename exists in same department
        Long groupId = null;
        Integer version = 1;

        // Find the latest version of this document in this department
        var latestVersionOpt = documentRepository.findByOriginalFilenameAndDepartmentIdAndIsLatestTrue(
                file.getOriginalFilename(), departmentId);

        if (latestVersionOpt.isPresent()) {
            Document latestVersion = latestVersionOpt.get();
            // This is a new version of existing document
            groupId = latestVersion.getGroupId();
            version = latestVersion.getVersion() + 1;

            // Mark previous version as not latest
            latestVersion.setIsLatest(false);
            latestVersion.setUpdatedAt(Instant.now());
            documentRepository.save(latestVersion);
        } else {
            // First version - generate new groupId
            groupId = generateGroupId();
        }

        Document document = new Document();
        document.setTitle(file.getOriginalFilename());
        document.setOriginalFilename(file.getOriginalFilename());
        document.setStoredFilename(new java.io.File(storagePath).getName());
        document.setStoragePath(storagePath);
        document.setFileType(getFileType(file));
        document.setFileSize(file.getSize());
        document.setDepartment(department);
        document.setUploadedBy(currentUser);
        document.setStatus(DocumentStatus.PENDING);
        document.setAccessScope(scope);
        document.setGroupId(groupId);
        document.setVersion(version);
        document.setIsLatest(true);

        Instant now = Instant.now();
        document.setCreatedAt(now);
        document.setUpdatedAt(now);

        Document saved = documentRepository.save(document);

        activityLogService.log(currentUser, ActivityType.UPLOAD,
                "Document: " + saved.getId() + " - " + saved.getTitle());

        // Trigger async processing
        processingService.processDocument(saved.getId());

        return UploadDocumentResponse.builder()
                .id(saved.getId())
                .title(saved.getTitle())
                .originalFilename(saved.getOriginalFilename())
                .fileType(saved.getFileType())
                .fileSize(saved.getFileSize())
                .status(saved.getStatus().name())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    public Page<DocumentListResponse> getAllDocuments(String search, String department, DocumentStatus status, Pageable pageable) {
        User currentUser = getCurrentUser();
        String userRole = getCurrentUserRole();

        List<Long> allowedDepartmentIds = authorizationService.resolveVisibleDepartmentIds(currentUser, userRole);

        // When visibility is scoped to the department tree, ignore the raw department filter
        // so users can only see documents from their own branch of the tree.
        if (allowedDepartmentIds != null) {
            department = null;
        }

        // Non-admins may only see documents that have finished processing.
        if (!"ADMIN".equals(userRole)) {
            status = DocumentStatus.READY;
        }

        Page<Document> documents = documentRepository.findWithFilters(search, department, status, allowedDepartmentIds, pageable);
        return documents.map(this::toListResponse);
    }

    public DocumentPreviewResponse getDocumentPreview(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));

        checkDocumentAccess(document);
        requireReadable(document);

        DocumentMetadata metadata = documentMetadataRepository.findByDocumentId(id);

        return DocumentPreviewResponse.builder()
                .title(document.getTitle())
                .summary(metadata != null ? metadata.getSummary() : null)
                .language(metadata != null ? metadata.getLanguage() : null)
                .pageCount(metadata != null ? metadata.getPageCount() : null)
                .author(metadata != null ? metadata.getAuthor() : null)
                .tags(metadata != null ? metadata.getTags() : null)
                .version(metadata != null ? metadata.getVersion() : null)
                .department(document.getDepartment() != null ? document.getDepartment().getName() : null)
                .uploadedBy(document.getUploadedBy() != null ? document.getUploadedBy().getEmail() : null)
                .createdAt(document.getCreatedAt())
                .build();
    }

    public Page<DocumentListResponse> searchDocuments(String query, Pageable pageable) {
        User currentUser = getCurrentUser();
        String userRole = getCurrentUserRole();

        List<Long> allowedDepartmentIds = authorizationService.resolveVisibleDepartmentIds(currentUser, userRole);

        DocumentStatus status = "ADMIN".equals(userRole) ? null : DocumentStatus.READY;

        Page<Document> documents = documentRepository.searchByKeyword(query, status != null ? status.name() : null, allowedDepartmentIds, pageable);
        return documents.map(this::toListResponse);
    }

    public DocumentResponse getDocumentById(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));
        
        checkDocumentAccess(document);
        requireReadable(document);
        
        return toResponse(document);
    }

    public UpdateDocumentStatusResponse getDocumentStatus(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));
        
        checkDocumentAccess(document);
        
        return UpdateDocumentStatusResponse.builder()
                .documentId(document.getId())
                .status(document.getStatus())
                .build();
    }

    public UpdateDocumentStatusResponse updateDocumentStatus(Long id, UpdateDocumentStatusRequest request) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));
        
        checkDocumentAccess(document);
        
        // Only ADMIN can update status (MANAGER can upload but only ADMIN can change status)
        User currentUser = getCurrentUser();
        String userRole = getCurrentUserRole();
        if (!"ADMIN".equals(userRole)) {
            throw new SecurityException("Only ADMIN can update document status");
        }
        
        DocumentStatus newStatus = request.getStatus();
        document.setStatus(newStatus);
        document.setUpdatedAt(Instant.now());
        documentRepository.save(document);
        
        return UpdateDocumentStatusResponse.builder()
                .documentId(document.getId())
                .status(document.getStatus())
                .build();
    }

    public UploadDocumentResponse reprocessDocument(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));
        
        checkDocumentAccess(document);
        
        // Reset status to PENDING for reprocessing
        document.setStatus(DocumentStatus.PENDING);
        document.setUpdatedAt(Instant.now());
        documentRepository.save(document);
        
        activityLogService.log(getCurrentUser(), ActivityType.REPROCESS,
                "Document: " + document.getId() + " - " + document.getTitle());

        // Trigger async processing
        processingService.processDocument(document.getId());

        return UploadDocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .originalFilename(document.getOriginalFilename())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .status(document.getStatus().name())
                .createdAt(document.getCreatedAt())
                .build();
    }

    public void deleteDocument(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));

        checkDocumentAccess(document);

        String userRole = getCurrentUserRole();
        if (!"ADMIN".equals(userRole)) {
            throw new SecurityException("Only ADMIN can delete documents");
        }

        activityLogService.log(getCurrentUser(), ActivityType.DELETE,
                "Document: " + document.getId() + " - " + document.getTitle());
        documentRepository.delete(document);
    }

    public List<DocumentVersionResponse> getDocumentVersions(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));
        
        checkDocumentAccess(document);
        requireReadable(document);
        
        List<Document> versions = documentRepository.findByGroupIdOrderByVersionDesc(document.getGroupId());
        return versions.stream()
                .map(this::toVersionResponse)
                .toList();
    }

    public Resource downloadDocument(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));
        
        checkDocumentAccess(document);
        requireReadable(document);

        activityLogService.log(getCurrentUser(), ActivityType.DOWNLOAD,
                "Document: " + document.getId() + " - " + document.getTitle());

        return storageService.load(document.getStoragePath());
    }

    private void checkDocumentAccess(Document document) {
        User currentUser = getCurrentUser();
        authorizationService.checkDocumentViewAccess(document, currentUser);
    }

    /**
     * Non-admins may only view documents that have finished processing.
     */
    private void requireReadable(Document document) {
        User currentUser = getCurrentUser();
        authorizationService.requireReadable(document, currentUser);
    }

private AccessScope resolveAccessScope(String value) {
        if (value == null || value.isBlank()) {
            return AccessScope.DEPARTMENT;
        }
        try {
            return AccessScope.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid access scope: " + value);
        }
    }

    /**
     * Returns the departments the current user may upload documents to.
     * Admin: every department. Manager: own department and its sub-departments.
     */
    public List<DepartmentResponse> getUploadOptions() {
        User currentUser = getCurrentUser();
        String userRole = getCurrentUserRole();
        List<Department> all = departmentService.getAllDepartmentsList();

        if ("ADMIN".equals(userRole)) {
            return all.stream().map(departmentService::toResponse).toList();
        }

        if ("MANAGER".equals(userRole) && currentUser.getDepartment() != null) {
            List<Long> subtreeIds = authorizationService.getUploadableDepartmentIds(getCurrentUser());
            return all.stream()
                    .filter(d -> subtreeIds.contains(d.getId()))
                    .map(departmentService::toResponse)
                    .toList();
        }

        return List.of();
    }

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File exceeds the maximum allowed size of 10MB");
        }

        String filename = file.getOriginalFilename();
        String extension = getExtension(filename);

        if (!isAllowedExtension(extension)) {
            throw new IllegalArgumentException("Unsupported file type. Allowed: pdf, docx, md, txt");
        }

        if (!matchesContentSignature(file, extension)) {
            throw new IllegalArgumentException("File content does not match the declared file type");
        }
    }

    private boolean isAllowedExtension(String extension) {
        return "pdf".equals(extension) || "docx".equals(extension)
                || "md".equals(extension) || "txt".equals(extension);
    }

    private String getExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        }
        return "";
    }

    /**
     * Verifies the declared extension against the actual file content using
     * magic bytes. Text files (txt, md) are rejected if they contain NUL bytes,
     * which would indicate a non-text payload disguised with a text extension.
     */
    private boolean matchesContentSignature(MultipartFile file, String extension) {
        try (var input = file.getInputStream()) {
            byte[] header = input.readNBytes(8);

            if ("pdf".equals(extension)) {
                return header.length >= 5
                        && header[0] == '%' && header[1] == 'P' && header[2] == 'D'
                        && header[3] == 'F' && header[4] == '-';
            }

            if ("docx".equals(extension)) {
                return header.length >= 4
                        && header[0] == 'P' && header[1] == 'K'
                        && (header[2] == 3 || header[2] == 5)
                        && (header[3] == 4 || header[3] == 6);
            }

            if ("txt".equals(extension) || "md".equals(extension)) {
                return containsNoNullBytes(header);
            }

            return true;
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Could not read uploaded file");
        }
    }

    private boolean containsNoNullBytes(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return false;
            }
        }
        return true;
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String email;
        if (principal instanceof UserDetails userDetails) {
            email = userDetails.getUsername();
        } else {
            email = principal.toString();
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
    
    private String getCurrentUserRole() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            String authority = userDetails.getAuthorities().iterator().next().getAuthority();
            if (authority.startsWith("ROLE_")) {
                return authority.substring(5);
            }
            return authority;
        }
        return "";
    }

    private Long generateGroupId() {
        // Use timestamp + random for unique groupId
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    private String getFileType(MultipartFile file) {
        String type = getExtension(file.getOriginalFilename());
        return type.isEmpty() ? "unknown" : type;
    }

    private DocumentListResponse toListResponse(Document document) {
        return DocumentListResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .originalFilename(document.getOriginalFilename())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .department(document.getDepartment() != null ? document.getDepartment().getName() : null)
                .departmentId(document.getDepartment() != null ? document.getDepartment().getId() : null)
                .accessScope(document.getAccessScope() != null ? document.getAccessScope().name() : AccessScope.DEPARTMENT.name())
                .uploadedBy(document.getUploadedBy() != null ? document.getUploadedBy().getEmail() : null)
                .uploadedAt(document.getCreatedAt())
                .status(document.getStatus() != null ? document.getStatus().name() : null)
                .build();
    }

    private DocumentResponse toResponse(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .originalFilename(document.getOriginalFilename())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .department(document.getDepartment() != null ? document.getDepartment().getName() : null)
                .departmentId(document.getDepartment() != null ? document.getDepartment().getId() : null)
                .accessScope(document.getAccessScope() != null ? document.getAccessScope().name() : AccessScope.DEPARTMENT.name())
                .uploadedBy(document.getUploadedBy() != null ? document.getUploadedBy().getEmail() : null)
                .uploadedAt(document.getCreatedAt())
                .status(document.getStatus() != null ? document.getStatus().name() : null)
                .build();
    }

    private DocumentVersionResponse toVersionResponse(Document document) {
        return DocumentVersionResponse.builder()
                .id(document.getId())
                .groupId(document.getGroupId())
                .originalFilename(document.getOriginalFilename())
                .version(document.getVersion())
                .isLatest(document.getIsLatest())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    /**
     * Validates that the current user can upload to the given department with the given scope.
     * Throws AccessDeniedException if not allowed.
     * This mirrors the logic from DocumentAuthorizationService.validateUploadTarget.
     */
    private void validateUploadTarget(User user, Department department, AccessScope scope) {
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
}