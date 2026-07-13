package com.sakh.repository;

import com.sakh.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, Long> {

    DocumentMetadata findByDocumentId(Long documentId);
}