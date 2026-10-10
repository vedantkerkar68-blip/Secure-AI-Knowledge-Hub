package com.sakh.rag;

import com.sakh.entity.Chunk;
import com.sakh.entity.Department;
import com.sakh.entity.Document;
import com.sakh.entity.Role;
import com.sakh.entity.User;
import com.sakh.enums.AccessScope;
import com.sakh.enums.DocumentStatus;
import com.sakh.processing.DocumentProcessingService;
import com.sakh.repository.DocumentRepository;
import com.sakh.service.ChunkService;
import com.sakh.service.MetadataExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.vectorstore.VectorStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Processing must be reliably idempotent: a document is only ever marked READY once
 * its previous vector entries have actually been removed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentProcessingServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private com.sakh.processing.parser.DocumentParser parser;

    @Mock
    private ChunkService chunkService;

    @Mock
    private MetadataExtractionService metadataService;

    @Mock
    private VectorStore vectorStore;

    private DocumentProcessingService processingService;
    private Path sourceFile;

    @BeforeEach
    void setUp() throws Exception {
        processingService = new DocumentProcessingService(
                documentRepository, List.of(parser), chunkService, metadataService, vectorStore);

        sourceFile = Files.createTempFile("sakh-processing-test", ".txt");
        Files.writeString(sourceFile, "Employees must give thirty days notice. HR handles the paperwork.");

        when(parser.supports(anyString())).thenReturn(true);
        when(parser.extractText(any())).thenReturn(
                "Employees must give thirty days notice. HR handles the paperwork.");

        when(documentRepository.findById(42L)).thenReturn(Optional.of(document(sourceFile.toString())));
    }

    @Test
    void processDocument_whenVectorDeletionFails_doesNotAddVectors_andMarksFailed() {
        doThrow(new RuntimeException("vector store unavailable"))
                .when(vectorStore).delete(anyString());

        when(chunkService.chunkDocument(any(), anyString())).thenReturn(List.of(chunk()));

        processingService.processDocument(42L);

        verify(vectorStore, never()).add(any());
        assertEquals(DocumentStatus.FAILED, currentStatus(),
                "A failed vector cleanup must surface through the FAILED status");
        verify(documentRepository, org.mockito.Mockito.atLeastOnce()).save(any(Document.class));
    }

    @Test
    void processDocument_whenVectorDeletionFails_doesNotRebuildChunks() {
        doThrow(new RuntimeException("vector store unavailable"))
                .when(vectorStore).delete(anyString());

        processingService.processDocument(42L);

        verify(chunkService, never()).chunkDocument(any(), anyString());
        verify(vectorStore, never()).add(any());
        assertEquals(DocumentStatus.FAILED, currentStatus());
    }

    @Test
    void processDocument_deletesVectorsBeforeChunkingAndAdding() {
        when(chunkService.chunkDocument(any(), anyString())).thenReturn(List.of(chunk()));

        processingService.processDocument(42L);

        InOrder order = inOrder(vectorStore, chunkService);
        order.verify(vectorStore).delete("documentId == 42");
        order.verify(chunkService).chunkDocument(any(), anyString());
        order.verify(vectorStore).add(any());
    }

    @Test
    void processDocument_onSuccess_addsVectorsAndMarksReady() {
        when(chunkService.chunkDocument(any(), anyString())).thenReturn(List.of(chunk()));

        processingService.processDocument(42L);

        verify(vectorStore).delete("documentId == 42");
        verify(vectorStore).add(any());
        assertEquals(DocumentStatus.READY, currentStatus());
    }

    @Test
    void processDocument_writesAccessScopeIntoVectorMetadata() {
        when(chunkService.chunkDocument(any(), anyString())).thenReturn(List.of(chunk()));

        processingService.processDocument(42L);

        org.mockito.ArgumentCaptor<List<org.springframework.ai.document.Document>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        var metadata = captor.getValue().get(0).getMetadata();
        assertEquals("ALL", metadata.get("accessScope"));
        assertEquals(42L, metadata.get("documentId"));
        assertEquals(7L, metadata.get("departmentId"));
        assertTrue(metadata.containsKey("chunkId"));
    }

    // --- helpers ---

    private DocumentStatus currentStatus() {
        org.mockito.ArgumentCaptor<Document> captor = org.mockito.ArgumentCaptor.forClass(Document.class);
        verify(documentRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Document last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        return last.getStatus();
    }

    private Document document(String storagePath) {
        Department dept = new Department();
        dept.setId(7L);

        Role role = new Role();
        role.setName("ADMIN");

        User uploader = new User();
        uploader.setEmail("owner@test.com");
        uploader.setRole(role);
        uploader.setDepartment(dept);

        Document doc = new Document();
        doc.setId(42L);
        doc.setTitle("Policy");
        doc.setStoragePath(storagePath);
        doc.setOriginalFilename("policy.txt");
        doc.setStoredFilename("policy.txt");
        doc.setFileType("text/plain");
        doc.setFileSize(10L);
        doc.setDepartment(dept);
        doc.setUploadedBy(uploader);
        doc.setAccessScope(AccessScope.ALL);
        doc.setStatus(DocumentStatus.PENDING);
        return doc;
    }

    private Chunk chunk() {
        Chunk c = new Chunk();
        c.setId(101L);
        c.setChunkIndex(0);
        c.setChunkText("Employees must give thirty days notice.");
        c.setTokenCount(8);
        return c;
    }
}
