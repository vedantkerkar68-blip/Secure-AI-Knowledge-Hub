# REST API Specification

Version

1.0

---

# Authentication

POST

/api/auth/login

POST

/api/auth/logout

POST

/api/auth/refresh

---

# Users

GET

/api/users

GET

/api/users/{id}

POST

/api/users

PUT

/api/users/{id}

DELETE

/api/users/{id}

---

# Departments

GET

/api/departments

POST

/api/departments

PUT

/api/departments/{id}

DELETE

/api/departments/{id}

---

# Documents

POST

/api/documents/upload

GET

/api/documents

GET

/api/documents/{id}

DELETE

/api/documents/{id}

---

# AI Chat

POST

/api/chat

Request

Question

Response

Answer

Sources

Confidence

---

# Search

GET

/api/search

Parameters

keyword

department

---

# Chat History

GET

/api/chat/history

DELETE

/api/chat/history/{id}

---

# Health

GET

/api/health

---

HTTP Codes

200

201

400

401

403

404

500

---

End of Document