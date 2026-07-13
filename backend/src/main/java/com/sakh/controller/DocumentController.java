package com.sakh.controller;

import com.sakh.dto.document.DocumentListResponse;
import com.sakh.dto.document.DocumentResponse;
import com.sakh.dto.document.DocumentVersionResponse;
import com.sakh.dto.document.UploadDocumentResponse;
import com.sakh.dto.document.UpdateDocumentStatusRequest;
import com.sakh.dto.document.UpdateDocumentStatusResponse;
import com.sakh.enums.DocumentStatus;
import com.sakh.service.DocumentService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<UploadDocumentResponse> uploadDocument(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "departmentId", required = false) Long departmentId) {
        UploadDocumentResponse response = documentService.uploadDocument(file, departmentId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<Page<DocumentListResponse>> getAllDocuments(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) DocumentStatus status,
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(documentService.getAllDocuments(search, department, status, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<DocumentResponse> getDocumentById(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocumentById(id));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE')")
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
    public ResponseEntity<List<DocumentVersionResponse>> getDocumentVersions(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocumentVersions(id));
    }

    @GetMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<UpdateDocumentStatusResponse> getDocumentStatus(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocumentStatus(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UpdateDocumentStatusResponse> updateDocumentStatus(
            @PathVariable Long id,
            @RequestBody UpdateDocumentStatusRequest request) {
        return ResponseEntity.ok(documentService.updateDocumentStatus(id, request));
    }

    @PostMapping("/{id}/reprocess")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<UploadDocumentResponse> reprocessDocument(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(documentService.reprocessDocument(id));
    }

    private String determineContentType(Resource resource) {
        try {
            return java.nio.file.Files.probeContentType(java.nio.file.Paths.get(resource.getURI()));
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }
}