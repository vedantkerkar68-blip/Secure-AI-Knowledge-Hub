# AGENTS.md

# Secure AI Knowledge Hub (SAKH)

This file defines the development rules for all AI coding agents working on this repository.

Every AI agent must follow these instructions before modifying any source code.

---

# Project Overview

Secure AI Knowledge Hub (SAKH) is an enterprise knowledge management platform that enables organizations to securely store, process, search, and retrieve internal knowledge using Retrieval-Augmented Generation (RAG).

The system enforces Role-Based Access Control (RBAC) so that every AI-generated response respects the permissions of the requesting user.

---

# Technology Stack

## Backend

- Java 21
- Spring Boot 3.x
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- JWT
- Maven

## Frontend

- React
- TypeScript
- Material UI
- Axios
- React Router

## AI

- RAG
- pgvector
- Embedding API
- LLM API
- Hybrid Search

---

# Architecture

Layered Architecture

Controller
↓
Service
↓
Repository
↓
Database

Rules

- Controllers must not contain business logic.
- Services contain all business logic.
- Repositories only access the database.
- DTOs must be used for every REST API.
- Constructor injection only.
- Never expose JPA entities directly.

---

# Repository Structure

```
Secure-AI-Knowledge-Hub/

backend/
frontend/
docs/
storage/
docker/
```

---

Before implementing any feature:

Read

docs/MASTER_CONTEXT.md

If database work:

Read

docs/contracts/database_schema.md


# Documentation

Before implementing any feature, always read:

```
docs/vision/PROJECT_SPEC.md
docs/architecture/ARCHITECTURE.md
docs/contracts/database_schema.md

```

If working on database migrations:

```
backend/src/main/resources/db/migration/
```

If working on REST APIs:

```
docs/contracts/openapi.yaml
```

If any required document is missing:

STOP.

Do not guess.

Ask for clarification.

---

# Development Rules

- Never invent database tables.
- Never invent REST endpoints.
- Never rename existing database columns.
- Never modify unrelated files.
- Never generate placeholder implementations.
- Always generate production-ready code.
- Keep methods small and readable.
- Use JavaDoc for public classes.
- Follow the existing package structure.

---

# Security Rules

- Always verify JWT before protected endpoints.
- Never bypass authorization.
- Always hash passwords using BCrypt.
- Never store secrets in source code.
- Use environment variables for credentials.

---

# Database Rules

- Flyway is the single source of truth for database migrations.
- Every schema change requires a new Flyway migration.
- Never modify an existing migration after it has been committed.
- PostgreSQL is the only supported database.
- Use pgvector for embedding storage.

---

# Git Rules

- One feature per commit.
- Small commits.
- Do not mix unrelated changes.
- Ensure the project builds before committing.

---

# AI Agent Workflow

For every task:

1. Read the required specifications.
2. Explain the implementation plan.
3. Modify only the required files.
4. Show a summary of changes.
5. Wait for approval before continuing.

Never generate additional features that were not requested.

Never assume missing requirements.

Always ask if something is ambiguous.

---

End