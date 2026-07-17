# Use Case Specification

Project

Secure AI Knowledge Hub (SAKH)

Version

1.0

---

# Actors

Administrator

Manager

Employee

Guest

---

# UC-01 Login

Actor

All Users

Flow

Open Login Page

↓

Enter Credentials

↓

Validate

↓

Generate JWT

↓

Dashboard

---

# UC-02 Upload Document

Actor

Administrator

Manager

Flow

Upload File

↓

Validate File

↓

Extract Text

↓

Convert Markdown

↓

Generate Metadata

↓

Chunk

↓

Embedding

↓

Save

↓

Success

---

# UC-03 Search Documents

Actor

All Users

Flow

Enter Keyword

↓

Search

↓

Display Results

---

# UC-04 Ask AI

Actor

All Users

Flow

Ask Question

↓

Verify JWT

↓

Check Permissions

↓

Retrieve Chunks

↓

Build Prompt

↓

Call AI API

↓

Return Answer

↓

Display Citations

---

# UC-05 Manage Users

Actor

Administrator

Flow

Create User

↓

Assign Role

↓

Assign Department

↓

Save

---

# UC-06 View Chat History

Actor

All Users

Flow

Open Chat History

↓

View Previous Questions

↓

Open Conversation

---

# UC-07 Delete Document

Actor

Administrator

Flow

Select Document

↓

Delete

↓

Remove Embeddings

↓

Update Database

↓

Success

---

# UC-08 Logout

Actor

All Users

Flow

Click Logout

↓

Invalidate Token

↓

Redirect Login