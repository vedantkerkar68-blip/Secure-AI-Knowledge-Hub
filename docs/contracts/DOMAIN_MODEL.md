# Domain Model

Project: Secure AI Knowledge Hub (SAKH)

Version: 1.0

---

# Purpose

This document defines every business object used in the application.

These objects become

- Java Entities
- Database Tables
- DTOs
- REST APIs

AI agents must never invent additional entities without updating this document.

---

# Core Entities

## User

Represents a person using the application.

Attributes

- id
- firstName
- lastName
- email
- password
- status
- role
- department
- createdAt
- updatedAt

Relationships

Belongs to one Department

Has one Role

Can upload many Documents

Can ask many Chat Questions

---

## Role

Represents user permissions.

Roles

ADMIN

MANAGER

EMPLOYEE

GUEST

---

## Department

Represents a business department.

Examples

HR

Engineering

Finance

Legal

Relationships

Has many Users

Has many Documents

---

## Workspace

Represents a logical knowledge space.

Examples

HR Workspace

Engineering Workspace

Finance Workspace

A Workspace contains

Documents

Chats

Knowledge

Users

---

## Document

Represents an uploaded file.

Attributes

id

title

originalFileName

fileType

fileSize

uploadedBy

workspace

department

status

createdAt

Relationships

Contains many Chunks

Has Metadata

---

## DocumentMetadata

Contains metadata.

Attributes

language

version

summary

tags

author

---

## Chunk

Represents a small section of a document.

Attributes

chunkId

chunkIndex

chunkText

embeddingVector

pageNumber

Relationships

Belongs to one Document

---

## ChatSession

Represents one AI conversation.

Attributes

id

user

workspace

createdAt

---

## ChatMessage

Represents one question or answer.

Attributes

id

role

message

timestamp

citation

confidence

---

## Citation

Represents document references.

Attributes

documentName

page

section

chunkId

---

# Entity Relationships

Workspace

↓

Documents

↓

Chunks

↓

Embeddings

↓

AI

Department

↓

Users

↓

Chat Sessions

↓

Messages

---

End