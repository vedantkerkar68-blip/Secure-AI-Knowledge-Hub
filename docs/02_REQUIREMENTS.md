# Software Requirements Specification (SRS)

Document Version: 1.0

Project: Secure AI Knowledge Hub (SAKH)

Status: Approved

---

# 1. Introduction

## 1.1 Purpose

The Secure AI Knowledge Hub (SAKH) is a web-based enterprise application that enables organizations to securely manage documents and interact with them using AI.

The system ensures that users receive answers only from documents they are authorized to access.

---

# 2. Functional Requirements

## FR-01 User Authentication

- User can login using email and password.
- JWT token is generated after successful login.
- Invalid credentials are rejected.

---

## FR-02 User Management

Administrator can

- Create users
- Update users
- Delete users
- Assign roles
- Assign departments

---

## FR-03 Department Management

Administrator can

- Create department
- Update department
- Delete department
- View departments

Example:

HR

Finance

Engineering

Legal

---

## FR-04 Document Upload

Authorized users can upload

- PDF
- DOCX
- TXT
- Markdown

---

## FR-05 Document Processing

System automatically

- Extracts text
- Converts content into Markdown
- Generates metadata
- Creates chunks
- Generates embeddings
- Stores vectors

No manual processing required.

---

## FR-06 Document Search

Users can search

- By title
- By keyword
- By department
- By tags

---

## FR-07 AI Chat

User asks a question.

System

- Retrieves relevant chunks
- Checks permissions
- Sends context to AI
- Returns answer
- Includes citations

---

## FR-08 Role-Based Access

Administrator

Full access.

Manager

Department documents.

Employee

Allowed documents.

Guest

Public documents only.

---

## FR-09 Chat History

Users can

- View previous conversations
- Delete conversations

---

## FR-10 Audit Logs

System records

- Login
- Upload
- Chat request
- Download
- Delete

---

# 3. Non-Functional Requirements

## Security

- JWT Authentication
- Password hashing
- Role-based authorization
- Input validation

---

## Performance

Document upload should complete within acceptable time.

Chat response should typically return within a few seconds, depending on document size and AI API latency.

---

## Scalability

The architecture should allow adding new document types and AI providers without major redesign.

---

## Maintainability

Backend follows layered architecture.

Frontend follows component-based architecture.

---

## Availability

System should remain available during normal usage.

---

## Usability

Simple dashboard.

Simple navigation.

Minimal learning curve.

---

# 4. Constraints

- Java backend only
- PostgreSQL database
- API-based LLM
- Docker deployment
- Internet required for AI APIs

---

# 5. Assumptions

Users have valid accounts.

Uploaded documents are readable.

AI provider is available.