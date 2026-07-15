package com.sakh.repository;

import com.sakh.entity.Document;
import com.sakh.enums.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query("SELECT d FROM Document d WHERE " +
            "(?1 IS NULL OR d.originalFilename ILIKE %?1%) AND " +
            "(?2 IS NULL OR d.department.name = ?2) AND " +
            "(?3 IS NULL OR d.status = ?3) AND " +
            "d.isLatest = true")
    Page<Document> findWithFilters(String search, String department, DocumentStatus status, Pageable pageable);

    List<Document> findByGroupIdOrderByVersionDesc(Long groupId);

    Optional<Document> findByOriginalFilenameAndDepartmentIdAndIsLatestTrue(String originalFilename, Long departmentId);

    long countByStatus(DocumentStatus status);

    @Query("SELECT d FROM Document d LEFT JOIN DocumentMetadata dm ON dm.document = d WHERE " +
            "d.isLatest = true AND " +
            "(?1 IS NULL OR " +
            "  LOWER(d.title) LIKE LOWER(CONCAT('%', ?1, '%')) OR " +
            "  LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', ?1, '%')) OR " +
            "  LOWER(dm.summary) LIKE LOWER(CONCAT('%', ?1, '%')) OR " +
            "  LOWER(dm.author) LIKE LOWER(CONCAT('%', ?1, '%')) OR " +
            "  LOWER(dm.language) LIKE LOWER(CONCAT('%', ?1, '%')) OR " +
            "  EXISTS (SELECT 1 FROM Chunk c WHERE c.document.id = d.id AND LOWER(c.chunkText) LIKE LOWER(CONCAT('%', ?1, '%')))) AND " +
            "(?2 IS NULL OR d.department.name = ?2)")
    Page<Document> searchByKeyword(String query, String department, Pageable pageable);
}
