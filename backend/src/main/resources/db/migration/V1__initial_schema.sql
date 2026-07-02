-- Secure AI Knowledge Hub (SAKH)
-- Initial schema migration
-- PostgreSQL 17 + pgvector

--CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------------------
-- roles
-- ---------------------------------------------------------------------------
CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(30)  NOT NULL,
    description TEXT,
    CONSTRAINT uq_roles_name UNIQUE (name)
);

INSERT INTO roles (name, description) VALUES
    ('ADMIN',    'System administrator with full access'),
    ('MANAGER',  'Department manager with elevated permissions'),
    ('EMPLOYEE', 'Standard employee user'),
    ('GUEST',    'Guest user with limited access');

-- ---------------------------------------------------------------------------
-- departments
-- ---------------------------------------------------------------------------
CREATE TABLE departments (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash TEXT         NOT NULL,
    role_id       BIGINT       NOT NULL,
    department_id BIGINT,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT fk_users_role
        FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_users_department
        FOREIGN KEY (department_id) REFERENCES departments (id)
);

-- email index is provided by uq_users_email
CREATE INDEX idx_users_department_id ON users (department_id);

-- ---------------------------------------------------------------------------
-- documents
-- ---------------------------------------------------------------------------
CREATE TABLE documents (
    id                BIGSERIAL PRIMARY KEY,
    title             VARCHAR(255) NOT NULL,
    original_filename TEXT         NOT NULL,
    stored_filename   TEXT         NOT NULL,
    storage_path      TEXT         NOT NULL,
    file_type         VARCHAR(30)  NOT NULL,
    file_size         BIGINT       NOT NULL,
    department_id     BIGINT       NOT NULL,
    uploaded_by       BIGINT       NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_documents_department
        FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_documents_uploaded_by
        FOREIGN KEY (uploaded_by) REFERENCES users (id)
);

CREATE INDEX idx_documents_department_id ON documents (department_id);

-- ---------------------------------------------------------------------------
-- document_metadata
-- ---------------------------------------------------------------------------
CREATE TABLE document_metadata (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT       NOT NULL,
    author      VARCHAR(255),
    language    VARCHAR(30),
    summary     TEXT,
    tags        JSONB,
    page_count  INTEGER,
    version     VARCHAR(20),
    CONSTRAINT fk_document_metadata_document
        FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE
);

CREATE INDEX idx_document_metadata_document_id ON document_metadata (document_id);

-- ---------------------------------------------------------------------------
-- chunks
-- ---------------------------------------------------------------------------
CREATE TABLE chunks (
    id            BIGSERIAL PRIMARY KEY,
    document_id   BIGINT       NOT NULL,
    chunk_index   INTEGER      NOT NULL,
    chunk_text    TEXT         NOT NULL,
    page_number   INTEGER,
    section_title VARCHAR(255),
    token_count   INTEGER,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chunks_document
        FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT uq_chunks_document_index UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_chunks_document_id ON chunks (document_id);

-- ---------------------------------------------------------------------------
-- chat_sessions
-- ---------------------------------------------------------------------------
CREATE TABLE chat_sessions (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(255),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

-- ---------------------------------------------------------------------------
-- chat_messages
-- ---------------------------------------------------------------------------
CREATE TABLE chat_messages (
    id           BIGSERIAL PRIMARY KEY,
    session_id   BIGINT        NOT NULL,
    message_role VARCHAR(20)   NOT NULL,
    message      TEXT          NOT NULL,
    citations    JSONB,
    confidence   DECIMAL(5, 2),
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_messages_session
        FOREIGN KEY (session_id) REFERENCES chat_sessions (id) ON DELETE CASCADE
);
