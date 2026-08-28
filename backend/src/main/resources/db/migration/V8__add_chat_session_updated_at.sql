-- Secure AI Knowledge Hub (SAKH)
-- V8: Add last-activity tracking to chat sessions
-- Enables listing sessions ordered by most recent activity.

ALTER TABLE chat_sessions ADD COLUMN updated_at TIMESTAMP;

UPDATE chat_sessions SET updated_at = created_at WHERE updated_at IS NULL;

ALTER TABLE chat_sessions ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE chat_sessions ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_chat_sessions_updated_at ON chat_sessions (updated_at DESC);
