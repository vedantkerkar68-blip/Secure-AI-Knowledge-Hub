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

    Document findByGroupIdAndVersion(Long groupId, Integer version);

    Document findByGroupIdAndIsLatestTrue(Long groupId);

    boolean existsByOriginalFilenameAndGroupId(String originalFilename, Long groupId);

    Optional<Document> findByOriginalFilenameAndDepartmentIdAndIsLatestTrue(String originalFilename, Long departmentId);

    List<Document> findByDepartmentIdAndStatus(Long departmentId, DocumentStatus status);
}