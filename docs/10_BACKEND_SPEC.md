# Backend Specification

Project

Secure AI Knowledge Hub (SAKH)

Version

1.0

---

# Technology

Java 21

Spring Boot 3.x

Spring Security

Spring Data JPA

Maven

---

# Architecture

Controller

↓

Service

↓

Repository

↓

Database

---

# Package Structure

com.sakh

config

controller

dto

entity

repository

service

security

exception

knowledge

rag

llm

util

---

# Controllers

AuthController

UserController

DepartmentController

DocumentController

ChatController

---

# Services

AuthService

UserService

DepartmentService

DocumentService

KnowledgeService

EmbeddingService

ChatService

LLMService

---

# Repositories

UserRepository

DepartmentRepository

DocumentRepository

ChunkRepository

ChatRepository

---

# Entity Rules

Every entity uses

- UUID Primary Key
- createdAt
- updatedAt

---

# Exception Handling

Global Exception Handler

Custom Exceptions

ResourceNotFoundException

UnauthorizedException

ValidationException

---

# Logging

Use SLF4J

No System.out.println()

---

End