-- Secure AI Knowledge Hub (SAKH)
-- V3: Remove embedding columns from chunks table
-- Embeddings are now stored in the vector_store table by Spring AI PgVectorStore

ALTER TABLE chunks
    DROP COLUMN IF EXISTS embedding,
    DROP COLUMN IF EXISTS embedding_model;

DROP INDEX IF EXISTS idx_chunks_embedding;
