package com.sakh.controller;

import com.sakh.dto.department.DepartmentResponse;
import com.sakh.dto.document.DocumentListResponse;
import com.sakh.dto.document.DocumentPreviewResponse;
import com.sakh.dto.document.DocumentResponse;
import com.sakh.dto.document.DocumentVersionResponse;
import com.sakh.dto.document.UploadDocumentResponse;
import com.sakh.dto.document.UpdateDocumentStatusRequest;
import com.sakh.dto.document.UpdateDocumentStatusResponse;
import com.sakh.enums.DocumentStatus;
import com.sakh.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/documents")
@Tag(name = "Documents", description = "Document upload, search, preview, download, and version management")
@SecurityRequirement(name = "JWT")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Upload document", description = "Uploads a new document file for processing (admin or manager)")
    public ResponseEntity<UploadDocumentResponse> uploadDocument(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "accessScope", required = false) String accessScope) {
        UploadDocumentResponse response = documentService.uploadDocument(file, departmentId, accessScope);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/upload-options")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Upload department options", description = "Returns the departments the current user may upload documents to (admin: all, manager: own subtree)")
    public ResponseEntity<List<DepartmentResponse>> getUploadOptions() {
        return ResponseEntity.ok(documentService.getUploadOptions());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE', 'GUEST')")
    @Operation(summary = "List documents", description = "Returns a paginated list of documents with optional filters")
    public ResponseEntity<Page<DocumentListResponse>> getAllDocuments(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) DocumentStatus status,
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(documentService.getAllDocuments(search, department, status, pageable));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE', 'GUEST')")
    @Operation(summary = "Keyword search", description = "Full-text keyword search across document titles, filenames, and metadata")
    public ResponseEntity<Page<DocumentListResponse>> searchDocuments(
            @RequestParam(required = false) String query,
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(documentService.searchDocuments(query, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE', 'GUEST')")
    @Operation(summary = "Get document by ID", description = "Returns document details by ID")
    public ResponseEntity<DocumentResponse> getDocumentById(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocumentById(id));
    }

    @GetMapping("/{id}/preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE', 'GUEST')")
    @Operation(summary = "Preview document metadata", description = "Returns document preview with title, summary, author, tags, and other metadata")
    public ResponseEntity<DocumentPreviewResponse> previewDocument(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocumentPreview(id));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE', 'GUEST')")
    @Operation(summary = "Download document", description = "Downloads the original document file")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id) {
        Resource resource = documentService.downloadDocument(id);
        String contentType = determineContentType(resource);
        
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Get document versions", description = "Returns version history for a document (admin or manager)")
    public ResponseEntity<List<DocumentVersionResponse>> getDocumentVersions(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocumentVersions(id));
    }

    @GetMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE', 'GUEST')")
    @Operation(summary = "Get document status", description = "Returns the processing status of a document")
    public ResponseEntity<UpdateDocumentStatusResponse> getDocumentStatus(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocumentStatus(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update document status", description = "Updates document processing status (admin only)")
    public ResponseEntity<UpdateDocumentStatusResponse> updateDocumentStatus(
            @PathVariable Long id,
            @RequestBody UpdateDocumentStatusRequest request) {
        return ResponseEntity.ok(documentService.updateDocumentStatus(id, request));
    }

    @PostMapping("/{id}/reprocess")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Reprocess document", description = "Triggers reprocessing of a document (admin or manager)")
    public ResponseEntity<UploadDocumentResponse> reprocessDocument(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(documentService.reprocessDocument(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete document", description = "Deletes a document by its ID (admin only)")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
    
    private String determineContentType(Resource resource) {
        try {
            return java.nio.file.Files.probeContentType(java.nio.file.Paths.get(resource.getURI()));
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }
}
