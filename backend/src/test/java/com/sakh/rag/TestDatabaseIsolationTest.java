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

    /** Marker contents used for the controlled development-schema fixture. */
    private static final String SENTINEL_A = "ci-sentinel-a";
    private static final String SENTINEL_B = "ci-sentinel-b";

    /** Field separator that cannot occur in the values being compared. */
    private static final String SEP = "\u0000";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VectorStore vectorStore;

    @MockBean
    private org.springframework.ai.embedding.EmbeddingModel embeddingModel;

    /**
     * {@code true} only between a successful {@code CREATE TABLE public.vector_store}
     * performed by this test and its matching cleanup. Gates every destructive statement.
     */
    private boolean developmentFixtureOwnedByTest;

    @Test
    void testsRunAgainstTheDedicatedSchema() {
        assertEquals("sakh_test", jdbcTemplate.queryForObject("SELECT current_schema()", String.class));
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'sakh_test' AND table_name = 'vector_store'",
                Long.class) == 1L, "vector_store must exist in the sakh_test schema");
    }

    /**
     * Writes a probe vector and proves it landed in the test schema only, and that the
     * development schema is byte-for-byte unchanged by the write.
     *
     * <p>The development-schema check compares a fingerprint of <em>every</em> row -
     * id, content, metadata and the stored embedding - not merely the row count. A count
     * comparison would be satisfied by an update in place, or by one insert paired with
     * one delete. The embedding is rendered through pgvector's own text cast, so an
     * in-place rewrite of an existing vector is detected too.
     *
     * <p>Fixture handling is safe for a developer's existing data:
     * <ul>
     *   <li>If {@code public.vector_store} already exists, it is read-only for this test.
     *       No row is inserted, updated or deleted, and the pre-existing rows are
     *       captured and compared.</li>
     *   <li>If it does not exist, the test creates it using the migration-compatible
     *       shape from V6 and seeds the two sentinel rows, then drops it again. That
     *       table is owned by this test and is the only thing it removes.</li>
     * </ul>
     *
     * <p>There is no branch in which the isolation assertion is skipped: either a
     * pre-existing table is fingerprinted or the test supplies one itself.
     */
    @Test
    void vectorWritesLandInTheTestSchemaAndLeaveTheDevelopmentSchemaUnchanged() {
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

        // Reset before anything can fail, so cleanup can never act on a stale value.
        developmentFixtureOwnedByTest = false;

        try {
            ensureDevelopmentFixture();

            List<String> developmentBefore = developmentFingerprint();
            List<String> sentinelsBefore = sentinelFingerprint();

            if (developmentFixtureOwnedByTest) {
                assertEquals(2, sentinelsBefore.size(),
                        "A fixture created by this test must contain exactly the two controlled sentinels");
            }

            vectorStore.add(List.of(Document.builder()
                    .text("isolation probe")
                    .metadata(Map.of(
                            "documentId", 987654321L,
                            "chunkId", 987654321L,
                            "departmentId", 1L,
                            "uploadedBy", "probe@test.com",
                            "accessScope", "ALL"))
                    .build()));

            Long inTestSchema = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM sakh_test.vector_store WHERE metadata ->> 'documentId' = ?",
                    Long.class, PROBE_DOCUMENT_ID);
            assertEquals(1L, inTestSchema, "The vector write must land in the sakh_test schema");

            Long probeInDevelopmentSchema = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM public.vector_store WHERE metadata ->> 'documentId' = ?",
                    Long.class, PROBE_DOCUMENT_ID);
            assertEquals(0L, probeInDevelopmentSchema,
                    "Vector writes must never reach the development schema");

            assertEquals(sentinelsBefore, sentinelFingerprint(),
                    "The controlled sentinel rows must be unchanged: none deleted, none added, "
                            + "no content or metadata rewritten");

            assertEquals(developmentBefore, developmentFingerprint(),
                    "Every existing public.vector_store row must be unchanged - same ids, same content, "
                            + "same metadata, same embeddings - and no row added or removed");
        } finally {
            jdbcTemplate.update("DELETE FROM sakh_test.vector_store WHERE metadata ->> 'documentId' = ?",
                    PROBE_DOCUMENT_ID);
            if (developmentFixtureOwnedByTest) {
                // Only ever drops the table this test created itself. Ownership is only
                // set immediately after a successful CREATE, and is cleared afterwards
                // so a second invocation cannot drop the same table twice.
                developmentFixtureOwnedByTest = false;
                jdbcTemplate.execute("DROP TABLE IF EXISTS public.vector_store");
            }
        }
    }

    /**
     * Ensures {@code public.vector_store} exists so the isolation check has something
     * to protect, without ever modifying a table this test does not own.
     *
     * <p>Ownership is recorded in {@link #developmentFixtureOwnedByTest} the instant
     * {@code CREATE TABLE} returns, and <em>before</em> the sentinel rows are inserted.
     * The caller runs this inside its {@code try}, so a failure part-way through
     * initialisation still reaches the {@code finally} block and the half-built table is
     * removed.
     *
     * <p>If {@code CREATE TABLE} itself fails - including the case where another
     * process created the table between the existence check and this statement -
     * ownership stays {@code false}, the exception propagates, and cleanup declines to
     * drop a table this test never created.
     */
    private void ensureDevelopmentFixture() {
        Long tables = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = 'vector_store'",
                Long.class);
        if (tables != null && tables == 1L) {
            // A developer's database (or the CI seed step) already provides it.
            // Do not insert, update, or delete anything here.
            return;
        }

        // Shape copied verbatim from V6__create_vector_store.sql.
        jdbcTemplate.execute("CREATE TABLE public.vector_store ("
                + "id        UUID DEFAULT gen_random_uuid() PRIMARY KEY, "
                + "content   TEXT, "
                + "metadata  JSONB, "
                + "embedding VECTOR(3072))");

        // Ownership established here, before any row is written.
        developmentFixtureOwnedByTest = true;

        for (String sentinel : List.of(SENTINEL_A, SENTINEL_B)) {
            jdbcTemplate.update(
                    "INSERT INTO public.vector_store (content, metadata, embedding) "
                            + "VALUES (?, ?::jsonb, NULL)",
                    sentinel,
                    "{\"documentId\":\"0\",\"accessScope\":\"ALL\"}");
        }
    }

     /** Ordered identity of every row in the development vector_store. */
    private List<String> developmentFingerprint() {
        // pgvector exposes the standard cast to text, which renders the stored values
        // deterministically for a given stored vector, so a before/after comparison in
        // the same run detects an in-place embedding rewrite. A NULL embedding renders
        // as the literal "null" and compares consistently. COUNT(*) is redundant given
        // ORDER BY id plus the primary key, and md5() would still depend on this text.
        return jdbcTemplate.query(
                "SELECT id::text AS id, content, metadata::text AS metadata, embedding::text AS embedding "
                        + "FROM public.vector_store ORDER BY id",
                (rs, rowNum) -> rs.getString("id") + SEP + rs.getString("content") + SEP
                        + rs.getString("metadata") + SEP + rs.getString("embedding"));
    }

    /** Identity of the controlled sentinel rows only, so a lost or rewritten sentinel is detectable. */
    private List<String> sentinelFingerprint() {
        return jdbcTemplate.query(
                "SELECT content, metadata::text AS metadata FROM public.vector_store "
                        + "WHERE content IN (?, ?) ORDER BY content",
                (rs, rowNum) -> rs.getString("content") + SEP + rs.getString("metadata"),
                SENTINEL_A, SENTINEL_B);
    }
}
