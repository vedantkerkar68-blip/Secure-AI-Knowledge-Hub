# Secure AI Knowledge Hub (SAKH)

## Purpose
Enterprise knowledge management platform with secure AI-powered document search using Retrieval-Augmented Generation (RAG).

## Tech Stack

Backend
- Java 21
- Spring Boot 3.x
- Spring Security
- Spring Data JPA
- Flyway
- PostgreSQL
- JWT

Frontend
- React
- TypeScript
- Material UI

AI
- Embedding API
- LLM API
- pgvector
- Hybrid Search

## Architecture

Controller
↓

Service
↓

Repository
↓

Database

Rules

- No business logic in controllers.
- DTOs only.
- Constructor injection.
- Flyway manages database schema.
- RBAC everywhere.
- Never expose entities directly.

## Package

com.sakh

## Database

Authoritative document:

docs/contracts/database_schema.md

## Coding Standards

docs/guides/CODING_STANDARDS.md

## AI Rules

docs/guides/AI_AGENT_RULES.md

Always follow AGENTS.md.