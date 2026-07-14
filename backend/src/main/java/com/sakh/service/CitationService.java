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

    public List<CitationDTO> createCitations(List<Document> documents) {
        return documents.stream()
                .map(this::toCitation)
                .toList();
    }

    private CitationDTO toCitation(Document doc) {
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
        String department = entity != null && entity.getDepartment() != null
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
