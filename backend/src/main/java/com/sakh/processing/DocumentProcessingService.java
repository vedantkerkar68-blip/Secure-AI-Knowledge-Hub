package com.sakh.processing;

import com.sakh.entity.Chunk;
import com.sakh.enums.DocumentStatus;
import com.sakh.exception.ResourceNotFoundException;
import com.sakh.processing.parser.DocumentParser;
import com.sakh.repository.DocumentRepository;
import com.sakh.service.ChunkService;
import com.sakh.service.MetadataExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final DocumentRepository documentRepository;
    private final List<DocumentParser> parsers;
    private final ChunkService chunkService;
    private final MetadataExtractionService metadataService;
    private final VectorStore vectorStore;

    public DocumentProcessingService(DocumentRepository documentRepository, List<DocumentParser> parsers, ChunkService chunkService, MetadataExtractionService metadataService, VectorStore vectorStore) {
        this.documentRepository = documentRepository;
        this.parsers = parsers;
        this.chunkService = chunkService;
        this.metadataService = metadataService;
        this.vectorStore = vectorStore;
    }

    @Async
    @Transactional
    public void processDocument(Long documentId) {
        logger.info("Document processing started for document ID: {}", documentId);

        com.sakh.entity.Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);
        logger.info("Document {} status changed to PROCESSING", documentId);

        try {
            String extractedText = extractText(document);

            metadataService.extractAndSave(document);

            List<Chunk> chunks = chunkService.chunkDocument(document, extractedText);
            logger.info("Document {}: created {} chunks", documentId, chunks.size());

            List<org.springframework.ai.document.Document> springAiDocs = chunks.stream()
                    .map(chunk -> {
                        Map<String,Object> metadata = new HashMap<>();

                        metadata.put("documentId", document.getId());
                        metadata.put("chunkId", chunk.getId());
                        metadata.put("departmentId", document.getDepartment().getId());
                        metadata.put("chunkIndex", chunk.getChunkIndex());

                        if(chunk.getSectionTitle()!=null)
                            metadata.put("sectionTitle", chunk.getSectionTitle());

                        if(chunk.getPageNumber()!=null)
                            metadata.put("pageNumber", chunk.getPageNumber());

                        metadata.put("uploadedBy", document.getUploadedBy().getEmail());
                        return org.springframework.ai.document.Document.builder()
        .text(chunk.getChunkText())
        .metadata(metadata)
        .build();
                    })
                    .toList();

            logger.info("Generating embeddings for {} chunks", springAiDocs.size());
            vectorStore.add(springAiDocs);
            logger.info("Embedding generation and vector storage completed for document {}", documentId);

            document.setStatus(DocumentStatus.READY);
            documentRepository.save(document);
            logger.info("Document {} processing completed, status changed to READY", documentId);

        } catch (Exception e) {
            logger.error("Document processing failed for document ID: {}", documentId, e);
            document.setStatus(DocumentStatus.FAILED);
            documentRepository.save(document);
        }
    }

    private String extractText(com.sakh.entity.Document document) {
        java.nio.file.Path filePath = java.nio.file.Paths.get(document.getStoragePath());
        String fileType = document.getFileType();

        DocumentParser parser = parsers.stream()
                .filter(p -> p.supports(fileType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No parser found for file type: " + fileType));

        return parser.extractText(filePath);
    }
}