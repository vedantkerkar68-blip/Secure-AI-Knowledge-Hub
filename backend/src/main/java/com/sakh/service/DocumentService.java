package com.sakh.service;

import com.sakh.dto.document.UploadDocumentResponse;
import com.sakh.entity.Department;
import com.sakh.entity.Document;
import com.sakh.entity.User;
import com.sakh.enums.DocumentStatus;
import com.sakh.exception.ResourceNotFoundException;
import com.sakh.repository.DepartmentRepository;
import com.sakh.repository.DocumentRepository;
import com.sakh.repository.UserRepository;
import com.sakh.storage.StorageService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public DocumentService(DocumentRepository documentRepository,
                           DepartmentRepository departmentRepository,
                           UserRepository userRepository,
                           StorageService storageService) {
        this.documentRepository = documentRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    public UploadDocumentResponse uploadDocument(MultipartFile file, Long departmentId) {
        validateFile(file);

        User currentUser = getCurrentUser();

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + departmentId));

        String storagePath = storageService.store(file);

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

        Instant now = Instant.now();
        document.setCreatedAt(now);
        document.setUpdatedAt(now);

        Document saved = documentRepository.save(document);

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

    private String getFileType(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        }
        return "unknown";
    }
}