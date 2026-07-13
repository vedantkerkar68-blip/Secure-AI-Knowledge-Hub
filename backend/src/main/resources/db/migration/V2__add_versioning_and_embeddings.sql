-- Secure AI Knowledge Hub (SAKH)
-- V2: Add missing columns for document versioning and vector embeddings
-- PostgreSQL 17 + pgvector

-- ---------------------------------------------------------------------------
-- documents: add versioning columns
-- ---------------------------------------------------------------------------
ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS group_id BIGINT,
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS is_latest BOOLEAN NOT NULL DEFAULT TRUE;

-- Add unique constraint for versioning (one version per group)
-- This allows multiple versions of the same logical document
ALTER TABLE documents
    ADD CONSTRAINT uq_documents_group_version UNIQUE (group_id, version);

-- Index for group lookups
CREATE INDEX IF NOT EXISTS idx_documents_group_id ON documents (group_id);

-- Index for finding latest versions
CREATE INDEX IF NOT EXISTS idx_documents_is_latest ON documents (is_latest);

COMMENT ON COLUMN documents.group_id IS 'Logical document group ID for versioning';
COMMENT ON COLUMN documents.version IS 'Document version number within group';
COMMENT ON COLUMN documents.is_latest IS 'Flag indicating the latest version';