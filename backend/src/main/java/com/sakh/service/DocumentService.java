package com.sakh.service;

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
import com.sakh.enums.ActivityType;
import com.sakh.enums.DocumentStatus;
import com.sakh.exception.ResourceNotFoundException;
import com.sakh.processing.DocumentProcessingService;
import com.sakh.repository.DepartmentRepository;
import com.sakh.repository.DocumentMetadataRepository;
import com.sakh.repository.DocumentRepository;
import com.sakh.repository.UserRepository;
import com.sakh.storage.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    public DocumentService(DocumentRepository documentRepository,
                           DocumentMetadataRepository documentMetadataRepository,
                           DepartmentRepository departmentRepository,
                           UserRepository userRepository,
                           StorageService storageService,
                           DocumentProcessingService processingService,
                           ActivityLogService activityLogService) {
        this.documentRepository = documentRepository;
        this.documentMetadataRepository = documentMetadataRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.processingService = processingService;
        this.activityLogService = activityLogService;
    }

    public UploadDocumentResponse uploadDocument(MultipartFile file, Long departmentId) {
        validateFile(file);

        User currentUser = getCurrentUser();

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + departmentId));

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
        
        // Filter by department for MANAGER and EMPLOYEE
        if (("MANAGER".equals(userRole) || "EMPLOYEE".equals(userRole)) && currentUser.getDepartment() != null) {
            department = currentUser.getDepartment().getName();
        }
        
        Page<Document> documents = documentRepository.findWithFilters(search, department, status, pageable);
        return documents.map(this::toListResponse);
    }

    public DocumentPreviewResponse getDocumentPreview(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));

        checkDocumentAccess(document);

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
        String department = null;

        if (("MANAGER".equals(userRole) || "EMPLOYEE".equals(userRole)) && currentUser.getDepartment() != null) {
            department = currentUser.getDepartment().getName();
        }

        Page<Document> documents = documentRepository.searchByKeyword(query, department, pageable);
        return documents.map(this::toListResponse);
    }

    public DocumentResponse getDocumentById(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));
        
        checkDocumentAccess(document);
        
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

    public List<DocumentVersionResponse> getDocumentVersions(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));
        
        checkDocumentAccess(document);
        
        List<Document> versions = documentRepository.findByGroupIdOrderByVersionDesc(document.getGroupId());
        return versions.stream()
                .map(this::toVersionResponse)
                .toList();
    }

    public Resource downloadDocument(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));
        
        checkDocumentAccess(document);

        activityLogService.log(getCurrentUser(), ActivityType.DOWNLOAD,
                "Document: " + document.getId() + " - " + document.getTitle());

        return storageService.load(document.getStoragePath());
    }

    private void checkDocumentAccess(Document document) {
        User currentUser = getCurrentUser();
        String userRole = getCurrentUserRole();
        
        // ADMIN can access all documents
        if ("ADMIN".equals(userRole)) {
            return;
        }
        
        // MANAGER and EMPLOYEE can only access documents in their department
        if (currentUser.getDepartment() == null || document.getDepartment() == null) {
            throw new ResourceNotFoundException("Document not found with id: " + document.getId());
        }
        
        if (!currentUser.getDepartment().getId().equals(document.getDepartment().getId())) {
            throw new ResourceNotFoundException("Document not found with id: " + document.getId());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        if (!isAllowedFileType(contentType, filename)) {
            throw new IllegalArgumentException("Unsupported file type. Allowed: pdf, docx, md, txt");
        }
    }

    private boolean isAllowedFileType(String contentType, String filename) {
        String extension = "";
        if (filename != null && filename.contains(".")) {
            extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        }

        return "pdf".equals(extension) || "docx".equals(extension) ||
                "md".equals(extension) || "txt".equals(extension);
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
        String filename = file.getOriginalFilename();
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        }
        return "unknown";
    }

    private DocumentListResponse toListResponse(Document document) {
        return DocumentListResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .originalFilename(document.getOriginalFilename())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .department(document.getDepartment() != null ? document.getDepartment().getName() : null)
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
}