# Secure AI Knowledge Hub (SAKH)

An enterprise-oriented secure AI knowledge management platform that enables organizations to store, process, search, and retrieve internal documents using Retrieval-Augmented Generation (RAG) with Role-Based Access Control (RBAC).

The backend API is deployed on Render, the React frontend is served by Vercel, and the database is hosted on Neon PostgreSQL with pgvector for vector similarity search. AI capabilities are powered by Google Gemini.

---

## Overview

SAKH solves a common enterprise problem: internal knowledge is scattered across documents, emails, and file shares, making it difficult for employees to find relevant information quickly. The platform ingests documents (PDF, DOCX), extracts text, generates embeddings, and indexes everything for semantic search. Users ask natural language questions and receive AI-generated answers with citations sourced exclusively from documents they are authorized to access.

Access control is enforced at every layer: authentication, API endpoints, document retrieval, and RAG answer generation. A user in one department cannot see documents from another department, and AI responses only include content from permitted sources.

---

## Key Features

- **JWT Authentication** — stateless token-based auth with configurable expiry
- **JWT Revocation & Rotation** — tokens carry a `jti` claim; logout revokes the token server-side, and refresh rotates the current token while revoking the old one
- **Account Status Enforcement** — non-active accounts are rejected at login and their tokens stop working immediately
- **Rate Limiting** — per-client limits on login, registration, and chat endpoints (429 on breach)
- **Role-Based Access Control (RBAC)** — ADMIN, MANAGER, EMPLOYEE, GUEST roles with hierarchical permissions
- **Department-Based Access** — documents are scoped to departments; users access only their department's documents (ADMIN sees all)
- **User Management** — admin-only user registration, status toggling, role assignment (with last-admin protection)
- **Department Management** — admin-only CRUD for organizational departments
- **Document Upload & Processing** — supports PDF, DOCX, TXT, MD; magic-byte content validation and size limits on upload; async text extraction, chunking, embedding generation
- **Versioning** — document re-upload creates new versions while preserving history
- **Vector Search** — pgvector-based cosine similarity search across embedded document chunks
- **Hybrid Search** — combines semantic (vector) and keyword (BM25-style) search with reciprocal rank fusion
- **Document-Scoped Chat** — attach a single document to a question so retrieval is confined to it, still enforcing access control
- **Retrieval-Augmented Generation** — multi-query expansion, query rewriting, context-aware prompting, answer grounding
- **Hallucination Mitigation** — sentence-level verification against source documents
- **Source Citations** — each answer cites specific document chunks with similarity scores; sources drawer with Document Name, Page, Section, chunk preview and Open PDF (page highlight)
- **Chat Sessions** — per-user isolated sessions (`sakh_chat_sessions_{userId}`), server-created IDs via `POST /chat/sessions`; Markdown rendering via `react-markdown` + `remark-gfm` (headings, bold, tables, code blocks, blockquotes); title auto-set from first question in a single `setSessions` updater (no race); sessions persist across logout/login for same user, different users never see each other's data; session list, detail, delete
- **Activity Logging** — audit trail (`createdAt`→Timestamp, `userEmail`→User, `action`→Action, `resource`→Details), filterable by action, search, date range with summary stats; `GET /api/admin/activity?page=0&size=20` verified
- **Dashboard Metrics** — role-based dashboard: ADMIN sees Users/Departments/Documents/Processed/Failed/Chat Sessions/Activity + Quick Actions (Add Employee, Add Department, Upload Document, New Chat) + Recent Activity/Uploads; MANAGER sees Department Documents/Users + Recent Uploads + My Chats (Upload Document, New Chat); EMPLOYEE sees My Documents/Uploads + My Chats (Upload Document, New Chat)
- **Backend Health UX** — cold-start aware splash screen with automatic reconnect, live status indicators, and login-time health banners
- **OpenAPI Documentation** — Swagger UI at `/api/swagger-ui.html`
- **Docker Support** — multi-stage Dockerfiles for backend and frontend, docker-compose for local development
- **Production Deployment** — configured for Render (backend) + Vercel (frontend) + Neon PostgreSQL

---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| **Frontend** | React | 19.1.0 |
| | Vite | 6.3.5 |
| | Material UI | 7.1.0 |
| | React Router | 7.6.1 |
| | Axios | 1.9.0 |
| | React Hook Form | 7.56.0 |
| | React Markdown + remark-gfm | 10.1.0 + 4.0.1 |
| | React Toastify | 11.1.0 |
| **Backend** | Java | 21 |
| | Spring Boot | 3.5 |
| | Spring Security | 6 |
| | Spring Data JPA | 3 |
| | Spring AI | 1.1 |
| | JWT (jjwt) | 0.12 |
| | Flyway | 10 |
| **Database** | PostgreSQL | 17 |
| | pgvector | 0.8 |
| **AI** | Google Gemini | 2.5 Flash (chat) / text-embedding-004 (embeddings) |
| **Deployment** | Docker | multi-stage builds |
| | Render | backend API |
| | Vercel | frontend SPA |
| | Neon | PostgreSQL + pgvector |

---

## System Architecture

```mermaid
graph TB
    User(["User"]) --> Browser["React SPA<br/>Vercel"]

    Browser --> API["Spring Boot REST API<br/>Render :8080"]

    subgraph Backend ["Spring Boot Backend"]
        API --> Security["JWT Filter<br/>SecurityContext"]
        Security --> Auth["Authentication<br/>Controller"]
        Security --> RBAC["Method-Level RBAC<br/>@PreAuthorize"]

        Auth --> Services["Application Services"]

        Services --> DocUpload["Document Upload"]
        Services --> Chat["Chat Service"]
        Services --> RAG["RAG Pipeline"]
        Services --> Activity["Activity Logging"]
        Services --> Dashboard["Dashboard Metrics"]

        DocUpload --> Processing["Async Processing<br/>Extract → Chunk → Embed"]
        Processing --> VectorStore[("pgvector<br/>Vector Store")]

        Chat --> Retriever["Hybrid Retriever<br/>Semantic + Keyword"]
        Retriever --> VectorStore
        Retriever --> ChunkDB[("Chunks<br/>PostgreSQL")]

        RAG --> QueryRewrite["Query Rewriter"]
        RAG --> MultiQuery["Multi-Query<br/>Expansion"]
        RAG --> AnswerVerify["Answer Verifier"]
        RAG --> Citation["Citation Builder"]
        RAG --> LLM["Gemini 2.5 Flash"]
    end

    VectorStore --> PG[("Neon PostgreSQL<br/>pgvector")]
    ChunkDB --> PG

    Services --> PG2[("Neon PostgreSQL<br/>Tables")]
    PG2 --> Flyway["Flyway Migrations<br/>V1-V7"]

    LLM --> GeminiAPI["Google Gemini API"]

    style User fill:#f9f9f9,stroke:#333
    style Browser fill:#e1f5fe,stroke:#0288d1
    style API fill:#fff3e0,stroke:#f57c00
    style PG fill:#e8f5e9,stroke:#388e3c
    style GeminiAPI fill:#fce4ec,stroke:#d32f2f
```

---

## RAG Pipeline

```mermaid
flowchart LR
    Q["User Question"] --> QR["Query Rewriter<br/>Gemini"]
    QR --> MQ["Multi-Query<br/>Expansion x3"]
    MQ --> VS[("Vector Search<br/>pgvector")]
    MQ --> KW[("Keyword Search<br/>PostgreSQL")]

    VS --> Merge["Merge and Rank<br/>Reciprocal Fusion"]
    KW --> Merge

    Merge --> Filter["Access Control<br/>Filter by Dept/Role"]
    Filter --> PB["Prompt Builder<br/>Context Assembly"]

    subgraph Documents ["Document Ingestion"]
        UP["Upload PDF/DOCX"] --> EX["Text Extraction<br/>PDFBox / POI"]
        EX --> CH["Chunking"]
        CH --> EM["Embedding<br/>text-embedding-004"]
        EM --> VS
        CH --> KW
    end

    PB --> Gen["Gemini 2.5 Flash"]
    Gen --> AV["Answer Verifier<br/>Sentence Grounding"]
    AV --> Resp["Answer and Citations"]
```

---

## Security Architecture

- **Authentication**: JWT tokens issued at login with a `jti` claim, validated on every request by `JwtAuthenticationFilter`; revocation checked against the `jwt_revocations` table
- **Token Lifecycle**: `POST /auth/logout` revokes the current token; `POST /auth/refresh` rotates it; deactivated/locked accounts are rejected immediately
- **Password Hashing**: BCrypt via Spring Security `PasswordEncoder`
- **Authorization**: Method-level `@PreAuthorize` annotations + request-matcher rules in `SecurityConfig`
- **Role Hierarchy**: ADMIN > MANAGER > EMPLOYEE > GUEST
- **Document Access**: Vector search and keyword search filter results by department ID and role — ADMIN sees all, MANAGER sees department, EMPLOYEE sees department + own uploads
- **Upload Validation**: extension allowlist plus magic-byte content verification and a 10MB size cap
- **Rate Limiting**: fixed-window limits on login, registration, and chat endpoints, keyed by client IP or user
- **CORS**: Configurable via `CORS_ALLOWED_ORIGINS` env var; supports multiple origins
- **Secrets**: All credentials are injected via environment variables — no hardcoded production secrets in source code
- **Frontend**: Route-level guards exist for UX but serve no security purpose; all authorization is enforced server-side

---

## Roles and Permissions

| Role | Description | Documents | Users | Departments | Activity Logs | Chat |
|---|---|---|---|---|---|---|
| ADMIN | Full system access | All documents | View, create, toggle status | CRUD | View all | All docs |
| MANAGER | Department management | Department documents | View department users | View only | Department scope | Department docs |
| EMPLOYEE | Document interaction | Own uploads + department docs | View own profile | View only | Own activity | Department docs + own uploads |
| GUEST | Read-only access | Only explicitly uploaded | View own profile | View only | Own activity | Own uploads only |

---

## Project Structure

```
Secure-AI-Knowledge-Hub/
├── backend/
│   ├── src/main/java/com/sakh/
│   │   ├── config/            # Swagger, web interceptor configuration
│   │   ├── controller/        # REST controllers (11)
│   │   ├── dto/               # Request/response DTOs
│   │   ├── entity/            # JPA entities (10)
│   │   ├── enums/             # ActivityType, DocumentStatus, UserStatus
│   │   ├── exception/         # Global exception handler
│   │   ├── llm/               # LLM service (Gemini wrapper)
│   │   ├── processing/        # Document processing pipeline
│   │   │   └── parser/        # PDF, DOCX parsers
│   │   ├── rag/               # RAG pipeline (7 components)
│   │   ├── repository/        # Spring Data JPA repositories (10)
│   │   ├── security/          # JWT, revocation, rate limiting, auth filter, security config
│   │   ├── service/           # Business services (11)
│   │   ├── storage/           # Local file storage
│   │   └── validation/        # Validation constants
│   ├── src/main/resources/
│   │   ├── db/migration/      # Flyway migrations (V1-V9)
│   │   ├── application.yml    # Main config (env var placeholders)
│   │   └── application-prod.yml
│   ├── Dockerfile
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── components/        # Layout (MainLayout, Navbar, Sidebar - role-based), common
│   │   ├── context/           # AuthContext (no chat-clear on login/logout; per-user key isolation)
│   │   ├── pages/             # 9 pages: Login, Dashboard (role-based), Users, Departments, Documents, Chat (Markdown+sources drawer), ActivityLogs (createdAt/userEmail/resource mapping), Profile, NotFound
│   │   ├── routes/            # React Router + PrivateRoute guard
│   │   ├── services/          # Axios services (8): auth, user, department, document, chat, dashboard, activityLog, api
│   │   └── theme.js           # MUI theme (Plus Jakarta Sans)
│   ├── Dockerfile
│   ├── nginx.conf
│   └── vercel.json
├── docker/
│   ├── docker-compose.yml     # Local development
│   ├── docker-compose.supabase.yml
│   └── docker-compose.neon.yml
├── docs/
│   ├── ARCHITECTURE.md
│   ├── DEPLOYMENT.md
│   ├── SECURITY.md
│   └── screenshots/           # Application screenshots
├── .gitignore
├── LICENSE
└── README.md
```

---

## Local Development

### Prerequisites

- Java 21
- Node.js 22
- Docker Desktop
- Gemini API key (free tier)

### 1. Clone and Configure

```sh
git clone https://github.com/vedantkerkar68-blip/Secure-AI-Knowledge-Hub.git
cd Secure-AI-Knowledge-Hub
```

Copy the environment template:

```sh
cp backend/.env.example backend/.env
```

Edit `backend/.env` and set `GEMINI_API_KEY`.

### 2. Start with Docker (recommended)

```sh
docker compose up -d
```

This starts PostgreSQL (pgvector), the backend, and the frontend. Access:
- Frontend: http://localhost:3000
- Backend API: http://localhost:8080/api
- Swagger UI: http://localhost:8080/api/swagger-ui.html
- Health: http://localhost:8080/api/health

### 3. Or Run Without Docker

Start PostgreSQL with pgvector (Docker):

```sh
docker run -d --name sakh-pg -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=root -e POSTGRES_DB=sakh_db -p 5432:5432 pgvector/pgvector:pg17
```

Start backend:

```sh
cd backend
mvn spring-boot:run
```

Start frontend (new terminal):

```sh
cd frontend
npm install
npm run dev
```

### Demo Credentials

| Email | Password | Role |
|---|---|---|
| admin@sakh.com | Admin@123 | ADMIN |

ADMIN users can register additional users through the Users page.

---

## Environment Variables

### Backend (`backend/.env` or Render env vars)

| Variable | Description | Local Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/sakh_db` |
| `SPRING_DATASOURCE_USERNAME` | Database user | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `root` |
| `JWT_SECRET` | 256+ bit secret for JWT signing | `9a8b7c6d...` (dev only) |
| `GEMINI_API_KEY` | Google Gemini API key | *(required)* |
| `APP_STORAGE_UPLOAD_DIR` | File upload directory | `./storage/uploads` |
| `CORS_ALLOWED_ORIGINS` | Allowed CORS origins | `http://localhost:3000,http://localhost:5173` |
| `RATE_LIMIT_LOGIN` | Max login attempts per window per IP | `10` |
| `RATE_LIMIT_REGISTER` | Max registration attempts per window per IP | `5` |
| `RATE_LIMIT_CHAT` | Max chat requests per window per user | `60` |
| `RATE_LIMIT_WINDOW_SECONDS` | Rate-limit window length in seconds | `60` |

### Frontend (`frontend/.env` or Vercel env vars)

| Variable | Description | Local Default |
|---|---|---|
| `VITE_API_BASE_URL` | Backend API base URL | `http://localhost:8080/api` |

> **Security**: Never expose database credentials, Gemini API keys, or JWT secrets in frontend environment variables. They are server-side only.

---

## Docker

The project includes multi-stage Dockerfiles for optimized builds. Pre-built images are available on Docker Hub:

- `vedantkerkar/sakh-backend:latest`
- `vedantkerkar/sakh-frontend:latest`

```sh
# Build and run locally
docker compose up -d

# Rebuild images
docker compose build

# View logs
docker compose logs -f backend

# Pull pre-built images
docker compose pull

# Stop
docker compose down
```

### Alternative Database Backends

```sh
# Use Neon PostgreSQL instead of local
docker compose -f docker/docker-compose.neon.yml up -d

# Use Supabase PostgreSQL instead of local
docker compose -f docker/docker-compose.supabase.yml up -d
```

---

## Production Deployment

| Component | Platform | Configuration |
|---|---|---|
| **Frontend** | Vercel | Set `VITE_API_BASE_URL` to `https://secure-ai-knowledge-hub.onrender.com/api` |
| **Backend** | Render | Set all backend env vars; health check at `/api/health` |
| **Database** | Neon PostgreSQL | pgvector extension enabled; SSL required |
| **AI** | Google Gemini API | Free tier API key |

---

## API Documentation

Swagger UI is available when the backend is running:

- **Local**: http://localhost:8080/api/swagger-ui.html
- **Production**: https://secure-ai-knowledge-hub.onrender.com/api/swagger-ui.html

OpenAPI spec at `/api/v3/api-docs`.

---

## Screenshots

| | | |
|---|---|---|
| [![Login](docs/screenshots/01-login.png)](docs/screenshots/01-login.png) | [![Dashboard](docs/screenshots/02-admin-dashboard.png)](docs/screenshots/02-admin-dashboard.png) | [![Users](docs/screenshots/03-users.png)](docs/screenshots/03-users.png) |
| **Login** | **Admin Dashboard** | **User Management** |
| [![Departments](docs/screenshots/04-departments.png)](docs/screenshots/04-departments.png) | [![Documents](docs/screenshots/05-documents.png)](docs/screenshots/05-documents.png) | [![AI Chat](docs/screenshots/06-ai-chat.png)](docs/screenshots/06-ai-chat.png) |
| **Departments** | **Document Management** | **AI Chat** |
| [![Sources](docs/screenshots/07-rag-sources.png)](docs/screenshots/07-rag-sources.png) | [![Activity Logs](docs/screenshots/08-activity-logs.png)](docs/screenshots/08-activity-logs.png) | [![Codebase](docs/screenshots/09-codebase.png)](docs/screenshots/09-codebase.png) |
| **RAG Sources** | **Activity Logs** | **Codebase Structure** |
| [![Database](docs/screenshots/10-database.png)](docs/screenshots/10-database.png) | | |
| **Database Schema** | | |

---

## Future Improvements

- Persistent object storage (AWS S3 / MinIO) for uploaded documents
- Redis caching for vector search results and session state
- Scheduled cleanup of expired token revocations
- Prometheus/Grafana observability
- CI/CD pipeline with automated integration tests
- Production-grade monitoring and alerting

---

## License

MIT License. See [LICENSE](LICENSE) for details.
