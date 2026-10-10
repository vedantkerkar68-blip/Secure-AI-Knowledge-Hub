-- Secure AI Knowledge Hub (SAKH)
-- V11: Backfill accessScope into existing vector_store metadata.
--
-- Context:
--   Commit 8112f1f added an "accessScope == 'ALL'" clause to the RAG semantic
--   candidate filter and began writing accessScope into newly ingested vector
--   metadata. Vectors created before that commit have no accessScope key, so a
--   READY AccessScope.ALL document in an unrelated department, uploaded by
--   someone else, matched none of the semantic filter clauses
--   (shared / department / own-upload) and became unreachable.
--
-- Why a metadata backfill is sufficient:
--   accessScope is a filter field, not part of the embedding vector. Populating
--   it does not change the embedding, so there is no need to re-embed or to
--   re-run the LLM/embedding APIs. This is a pure metadata repair.
--
-- Notes:
--   * Idempotent - only touches rows where the key is absent.
--   * Also repairs orphaned/duplicate vectors left by reprocessing, because it
--     keys off metadata->>'documentId' rather than a specific vector id.
--   * Runs AFTER documents.access_scope is guaranteed to exist (V10).
--   * Rows whose documentId does not resolve to a document are left untouched;
--     RetrieverService already fails closed for unknown document ids.

UPDATE vector_store vs
SET metadata = vs.metadata || jsonb_build_object('accessScope', d.access_scope)
FROM documents d
WHERE vs.metadata ? 'documentId'
  AND NOT (vs.metadata ? 'accessScope')
  AND (vs.metadata ->> 'documentId') ~ '^[0-9]+$'
  AND d.id = (vs.metadata ->> 'documentId')::bigint;

-- Index the documentId metadata key so the join above stays cheap on large stores
-- and so per-document vector cleanup during reprocessing can use an index scan.
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_document_id
    ON vector_store ((metadata ->> 'documentId'));
