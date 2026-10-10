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
     * The vector store writes to the test schema, and - when the shared development
     * schema has a {@code vector_store} table - the number of rows in it is unchanged
     * by the write.
     *
     * <p>This is a net row-count comparison, not a content comparison. Equal counts
     * before and after show only that the total number of rows is unchanged. They do
     * not show that nothing was inserted or deleted, because an insertion paired with
     * a deletion would leave the count identical, and they do not show that existing
     * rows are unmodified, because an in-place update leaves the count untouched too.
     */
    @Test
    void vectorWritesLandInTheTestSchemaAndDoNotChangeTheDevelopmentSchemaRowCount() {
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

        Long publicBefore = developmentVectorStoreRowCount();

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

            if (publicBefore != null) {
                assertEquals(publicBefore, developmentVectorStoreRowCount(),
                        "The shared development vector_store must not be modified by tests");
            }
        } finally {
            jdbcTemplate.update("DELETE FROM sakh_test.vector_store WHERE metadata ->> 'documentId' = ?",
                    PROBE_DOCUMENT_ID);
        }
    }

    /**
     * Row count of the shared development schema's vector_store, or {@code null} when
     * that schema has no vector_store table.
     *
     * <p>Flyway only creates migration objects in {@code sakh_test}, so on an isolated
     * CI database - the configuration application-test.yml documents and the guard
     * allow-lists - {@code public.vector_store} legitimately does not exist. Querying
     * it unconditionally would make this test error there rather than assert isolation.
     * When the table is absent there is nothing to protect, which is why the caller
     * treats {@code null} as "no development data present" instead of a failure.
     */
    private Long developmentVectorStoreRowCount() {
        Long tables = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = 'vector_store'",
                Long.class);
        if (tables == null || tables == 0L) {
            return null;
        }
        return jdbcTemplate.queryForObject("SELECT count(*) FROM public.vector_store", Long.class);
    }
}
