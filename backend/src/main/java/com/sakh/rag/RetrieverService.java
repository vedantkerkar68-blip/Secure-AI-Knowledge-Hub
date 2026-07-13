package com.sakh.rag;

import com.sakh.entity.Chunk;
import com.sakh.entity.Document;
import com.sakh.entity.User;
import com.sakh.enums.DocumentStatus;
import com.sakh.repository.DocumentRepository;
import com.sakh.repository.ChunkRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RetrieverService {

    private final ChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;

    public RetrieverService(ChunkRepository chunkRepository, DocumentRepository documentRepository) {
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
    }

    public List<Chunk> retrieve(String question, User user, int topK) {
        // Get user's department
        Long departmentId = user.getDepartment() != null ? user.getDepartment().getId() : null;

        // Find accessible documents
        List<Document> accessibleDocuments = findAccessibleDocuments(departmentId);

        if (accessibleDocuments.isEmpty()) {
            return Collections.emptyList();
        }

        // Perform keyword search on each document and merge results
        List<Chunk> allResults = new ArrayList<>();

        for (Document doc : accessibleDocuments) {
            if (doc.getStatus() == DocumentStatus.READY) {
                if (doc.getStatus() == DocumentStatus.READY) {
                List<Chunk> docResults = chunkRepository.findByKeywordSearch(doc.getId(), question, topK);
                allResults.addAll(docResults);
            }
            }
        }

        // Deduplicate and limit
        return allResults.stream()
                .distinct()
                .limit(topK)
                .collect(Collectors.toList());
    }

    private List<Document> findAccessibleDocuments(Long departmentId) {
        if (departmentId != null) {
            return documentRepository.findByDepartmentIdAndStatus(departmentId, DocumentStatus.READY);
        }
        return Collections.emptyList();
    }
}