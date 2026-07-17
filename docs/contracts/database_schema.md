# Database Schema

Version: 1.0

Database: PostgreSQL 17

Extension:

- pgvector

---

# Table: roles

Purpose

Stores system roles.

Columns

id BIGSERIAL PRIMARY KEY

name VARCHAR(30) UNIQUE NOT NULL

description TEXT

Seed Data

ADMIN

MANAGER

EMPLOYEE

GUEST

---

# Table: departments

Purpose

Stores organization departments.

Columns

id BIGSERIAL PRIMARY KEY

name VARCHAR(100)

description TEXT

created_at TIMESTAMP

---

# Table: users

Purpose

Stores application users.

Columns

id BIGSERIAL PRIMARY KEY

first_name VARCHAR(100)

last_name VARCHAR(100)

email VARCHAR(255) UNIQUE

password_hash TEXT

role_id BIGINT

department_id BIGINT

status VARCHAR(20)

created_at TIMESTAMP

updated_at TIMESTAMP

Foreign Keys

role_id → roles.id

department_id → departments.id

---

# Table: documents

Purpose

Stores uploaded documents.

Columns

id BIGSERIAL PRIMARY KEY

title VARCHAR(255)

original_filename TEXT

stored_filename TEXT

storage_path TEXT

file_type VARCHAR(30)

file_size BIGINT

department_id BIGINT

uploaded_by BIGINT

status VARCHAR(20)

created_at TIMESTAMP

updated_at TIMESTAMP

---

# Table: document_metadata

Purpose

Stores extracted metadata.

Columns

id BIGSERIAL PRIMARY KEY

document_id BIGINT

author VARCHAR(255)

language VARCHAR(30)

summary TEXT

tags JSONB

page_count INTEGER

version VARCHAR(20)

---

# Table: chunks

Purpose

Stores document chunks.

Columns

id BIGSERIAL PRIMARY KEY

document_id BIGINT

chunk_index INTEGER

chunk_text TEXT

page_number INTEGER

section_title VARCHAR(255)

token_count INTEGER

embedding VECTOR(768)

created_at TIMESTAMP

---

# Table: chat_sessions

Purpose

Stores user conversations.

Columns

id BIGSERIAL PRIMARY KEY

user_id BIGINT

title VARCHAR(255)

created_at TIMESTAMP

---

# Table: chat_messages

Purpose

Stores questions and answers.

Columns

id BIGSERIAL PRIMARY KEY

session_id BIGINT

message_role VARCHAR(20)

message TEXT

citations JSONB

confidence DECIMAL(5,2)

created_at TIMESTAMP