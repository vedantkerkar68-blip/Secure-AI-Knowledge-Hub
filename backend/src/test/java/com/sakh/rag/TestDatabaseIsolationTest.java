package com.sakh.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Guards the test database isolation described in application-test.yml.
 *
 * <p>Without these assertions a regression in the test datasource configuration
 * would silently point the whole suite - including Flyway migrations - at the
 * shared development schema.
 */
@SpringBootTest
@ActiveProfiles("test")
class TestDatabaseIsolationTest {

    private static final String PROBE_DOCUMENT_ID = "987654321";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VectorStore vectorStore;

    @MockBean
    private org.springframework.ai.embedding.EmbeddingModel embeddingModel;

    @Test
    void testsRunAgainstTheDedicatedSchema() {
        assertEquals("sakh_test", jdbcTemplate.queryForObject("SELECT current_schema()", String.class));
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'sakh_test' AND table_name = 'vector_store'",
                Long.class) == 1L, "vector_store must exist in the sakh_test schema");
    }

    /**
     * The vector store writes to the test schema, and the shared development
     * schema is left byte-for-byte untouched.
     */
    @Test
    void vectorWritesLandInTheTestSchemaAndNeverInTheDevelopmentSchema() {
        float[] embedding = new float[3072];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = 0.01f;
        }
        // PgVectorStore.add() embeds documents via the bulk
        // embed(List<Document>, EmbeddingOptions, BatchingStrategy) overload.
        when(embeddingModel.embed(
                org.mockito.ArgumentMatchers.<List<Document>>any(),
                org.mockito.ArgumentMatchers.nullable(org.springframework.ai.embedding.EmbeddingOptions.class),
                org.mockito.ArgumentMatchers.nullable(org.springframework.ai.embedding.BatchingStrategy.class)))
                .thenReturn(List.of(embedding));
        when(embeddingModel.embed(any(Document.class))).thenReturn(embedding);
        when(embeddingModel.embed(anyString())).thenReturn(embedding);

        Long publicBefore = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.vector_store", Long.class);

        vectorStore.add(List.of(Document.builder()
                .text("isolation probe")
                .metadata(Map.of(
                        "documentId", 987654321L,
                        "chunkId", 987654321L,
                        "departmentId", 1L,
                        "uploadedBy", "probe@test.com",
                        "accessScope", "ALL"))
                .build()));

        try {
            Long inTestSchema = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM sakh_test.vector_store WHERE metadata ->> 'documentId' = ?",
                    Long.class, PROBE_DOCUMENT_ID);
            assertEquals(1L, inTestSchema, "The vector write must land in the sakh_test schema");

            Long publicAfter = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM public.vector_store", Long.class);
            assertEquals(publicBefore, publicAfter,
                    "The shared development vector_store must not be modified by tests");
        } finally {
            jdbcTemplate.update("DELETE FROM sakh_test.vector_store WHERE metadata ->> 'documentId' = ?",
                    PROBE_DOCUMENT_ID);
        }
    }
}
