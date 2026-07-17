# Database Specification

Project

Secure AI Knowledge Hub

Version

1.0

---

# Database

PostgreSQL

---

# Tables

## users

id

name

email

password

department_id

role_id

created_at

---

## roles

id

role_name

---

Roles

ADMIN

MANAGER

EMPLOYEE

GUEST

---

## departments

id

department_name

---

Examples

HR

Engineering

Finance

Legal

---

## documents

id

title

file_name

file_path

department_id

uploaded_by

upload_date

status

---

## metadata

id

document_id

version

language

tags

summary

---

## chunks

id

document_id

chunk_index

chunk_text

---

## embeddings

id

chunk_id

vector

embedding_model

created_at

---

## chat_history

id

user_id

question

answer

created_at

---

# Relationships

Department

↓

Users

Department

↓

Documents

Documents

↓

Metadata

Documents

↓

Chunks

Chunks

↓

Embeddings

Users

↓

Chat History

---

# Indexes

email

department_id

document_id

chunk_id

---

End of Document