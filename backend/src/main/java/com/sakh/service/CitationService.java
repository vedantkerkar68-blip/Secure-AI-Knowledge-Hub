package com.sakh.service;

import com.sakh.dto.chat.CitationDTO;
import com.sakh.repository.DocumentRepository;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CitationService {

    private final DocumentRepository documentRepository;

    public CitationService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

/**
     * Builds citations for the documents supplied to a prompt.
     *
     * <p>{@code viewerRole} decides whether owning-department metadata may be disclosed.
     * GUEST may only ever retrieve public/shared knowledge, and the access-control policy
     * does not establish that a document's owning department is itself public information,
     * so the department is redacted for that role. Every other role keeps the existing
     * behaviour unchanged.
     *
     * <p>The caller must already have authorized the documents; this method does not
     * perform access control.
     */
    public List<CitationDTO> createCitations(List<Document> documents, String viewerRole) {
        boolean redactDepartment = "GUEST".equals(viewerRole);
        return documents.stream()
                .map(doc -> toCitation(doc, redactDepartment))
                .toList();
    }

    private CitationDTO toCitation(Document doc, boolean redactDepartment) {
        Object docIdObj = doc.getMetadata().get("documentId");
        Long documentId = docIdObj instanceof Number num ? num.longValue() : null;

        com.sakh.entity.Document entity = null;
        if (documentId != null) {
            Optional<com.sakh.entity.Document> opt = documentRepository.findById(documentId);
            if (opt.isPresent()) {
                entity = opt.get();
            }
        }

        String title = entity != null && entity.getTitle() != null ? entity.getTitle() : "Unknown";
        Integer version = entity != null ? entity.getVersion() : null;
        String department = (!redactDepartment && entity != null && entity.getDepartment() != null)
                ? entity.getDepartment().getName() : null;

        return CitationDTO.builder()
                .documentId(documentId)
                .documentTitle(title)
                .version(version)
                .department(department)
                .pageNumber(toInteger(doc.getMetadata().get("pageNumber")))
                .sectionTitle(toString(doc.getMetadata().get("sectionTitle")))
                .chunkIndex(toInteger(doc.getMetadata().get("chunkIndex")))
                .similarityScore(doc.getScore())
                .build();
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Number num) return num.intValue();
        return null;
    }

    private static String toString(Object value) {
        return value != null ? value.toString() : null;
    }
}
