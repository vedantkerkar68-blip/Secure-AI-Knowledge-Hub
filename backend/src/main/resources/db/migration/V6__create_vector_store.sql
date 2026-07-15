-- Secure AI Knowledge Hub (SAKH)
-- V6: Create vector_store table for Spring AI PgVectorStore
-- Required by spring.ai.vectorstore.pgvector (initialize-schema is false)

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS vector_store (
    id        UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    content   TEXT,
    metadata  JSONB,
    embedding VECTOR(3072)
);
