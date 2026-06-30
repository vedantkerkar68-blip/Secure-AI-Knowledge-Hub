# 04_ARCHITECTURE.md

# System Architecture

Project

Secure AI Knowledge Hub (SAKH)

Version

1.0

Status

Approved

---

# 1. Architecture Style

The project follows a Layered Architecture.

Presentation Layer

↓

Business Layer

↓

Data Access Layer

↓

Database

↓

AI Services

---

# 2. High-Level Components

Frontend

- React
- TypeScript
- Material UI

Backend

- Spring Boot
- Spring Security
- Spring Data JPA

Database

- PostgreSQL
- pgvector

AI Services

- Embedding API
- LLM API

Knowledge

- Original Documents
- Structured Knowledge
- Metadata
- Embeddings

---

# 3. Request Flow

User

↓

Login

↓

JWT Authentication

↓

Dashboard

↓

Upload Document

↓

Backend

↓

Document Processing

↓

Knowledge Processing

↓

Chunk Generation

↓

Embedding Generation

↓

Database

---

# 4. Chat Flow

User

↓

Enter Question

↓

JWT Validation

↓

Role Verification

↓

Retrieve Relevant Chunks

↓

Build Prompt

↓

Call LLM API

↓

Receive Response

↓

Return Answer + Citations

---

# 5. Backend Architecture

Controller

↓

Service

↓

Repository

↓

Database

---

# 6. AI Processing Pipeline

Document Upload

↓

Extract Text

↓

Generate Metadata

↓

Chunk Text

↓

Generate Embeddings

↓

Store Embeddings

↓

Ready for Retrieval

---

# 7. Security Layer

Authentication

↓

Authorization

↓

Document Permission Check

↓

Retrieval

↓

AI

---

# 8. Folder Structure

backend/

controller/

service/

repository/

entity/

dto/

security/

config/

rag/

knowledge/

frontend/

pages/

components/

services/

hooks/

context/

knowledge/

documents/

metadata/

---

# 9. Design Principles

- Separation of Concerns
- Clean Code
- Layered Architecture
- Secure by Design
- Modular Components

---

End of Document