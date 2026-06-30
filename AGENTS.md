# AGENTS.md

# Secure AI Knowledge Hub (SAKH)

This file defines how AI coding agents must work on this repository.

It is the primary instruction document for AI-assisted development.

---

# Project Summary

Secure AI Knowledge Hub (SAKH) is an enterprise knowledge management platform.

Users upload organizational documents.

The backend extracts knowledge, generates embeddings and uses Retrieval-Augmented Generation (RAG) to answer questions.

Every answer must respect Role-Based Access Control (RBAC).

The system is built using

- Java 21
- Spring Boot
- PostgreSQL
- React
- TypeScript
- Docker
- API-based LLM
- pgvector

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

Controllers never contain business logic.

---

# Repository Structure

backend/

frontend/

docs/

knowledge/

storage/

docker/

---

# Development Rules

Always read

docs/01_PROJECT_SPEC.md

docs/04_ARCHITECTURE.md

before implementing any feature.

If implementing database features

also read

docs/contracts/database.sql

If implementing APIs

also read

docs/contracts/openapi.yaml

---

# Coding Rules

Never invent database tables.

Never invent REST APIs.

Never rename existing fields.

Never create duplicate classes.

Never expose entities directly in REST APIs.

Always use DTOs.

Always use constructor injection.

Always follow package structure.

Always create JavaDoc for public classes.

---

# Security Rules

Always verify JWT before accessing protected endpoints.

Never bypass authorization.

Passwords must always use BCrypt.

Never store secrets in source code.

---

# AI Rules

If a required file is missing

STOP

Do not guess.

Ask for clarification.

Never generate incomplete code pretending it is complete.

If modifying existing code

Read the existing files first.

Never overwrite unrelated code.

---

# Git Rules

One feature per commit.

Small commits.

Never modify multiple unrelated modules in one change.

---

# Backend Stack

Java 21

Spring Boot

Spring Security

Spring Data JPA

PostgreSQL

JWT

Maven

---

# Frontend Stack

React

TypeScript

Material UI

Axios

React Router

---

# AI Stack

Embedding API

LLM API

RAG

Hybrid Search

pgvector

---

End