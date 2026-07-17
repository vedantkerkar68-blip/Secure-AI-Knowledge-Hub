# Architecture

## High-Level Architecture

The application follows a layered architecture with a decoupled frontend and backend, communicating over HTTPS REST.

```
┌─────────────────────────────────────────────────────────────┐
│                     Vercel (CDN)                            │
│  React SPA ──── Axios ──── HTTPS ──── JWT Bearer Token     │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    Render (Cloud VM)                        │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Spring Boot REST API                                │   │
│  │                                                      │   │
│  │  Security Layer                                      │   │
│  │  ┌──────────────┐  ┌──────────────────────────┐     │   │
│  │  │ JWT Filter   │  │ CORS Filter              │     │   │
│  │  └──────┬───────┘  └──────────────────────────┘     │   │
│  │         │                                            │   │
│  │  ┌──────▼───────────────────────────────────────┐   │   │
│  │  │  Controllers (9)                             │   │   │
│  │  │  Auth | Users | Departments | Documents      │   │   │
│  │  │  Chat | Activity | Dashboard | Health        │   │   │
│  │  └──────┬───────────────────────────────────────┘   │   │
│  │         │                                            │   │
│  │  ┌──────▼───────────────────────────────────────┐   │   │
│  │  │  Services (10)                               │   │   │
│  │  │  Auth | User | Department | Document         │   │   │
│  │  │  Chat | Chunk | Citation | Activity          │   │   │
│  │  │  Dashboard | Metrics                         │   │   │
│  │  └──────┬───────────────────────────────────────┘   │   │
│  │         │                                            │   │
│  │  ┌──────▼───────────────────────────────────────┐   │   │
│  │  │  RAG Pipeline (7 components)                 │   │   │
│  │  │  QueryRewriter → MultiQueryRetriever         │   │   │
│  │  │  → RetrieverService → PromptBuilder          │   │   │
│  │  │  → LLMService → AnswerVerifier               │   │   │
│  │  │  → CitationService                           │   │   │
│  │  └──────┬───────────────────────────────────────┘   │   │
│  │         │                                            │   │
│  │  ┌──────▼───────────────────────────────────────┐   │   │
│  │  │  Data Layer                                  │   │   │
│  │  │  Spring Data JPA (9 repos)                   │   │   │
│  │  │  Flyway Migrations (V1-V7)                   │   │   │
│  │  │  Spring AI VectorStore (pgvector)            │   │   │
│  │  └──────┬───────────────────────────────────────┘   │   │
│  └─────────┼────────────────────────────────────────────┘   │
└────────────┼────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│              Neon PostgreSQL + pgvector                     │
│                                                             │
│  Tables:                                                    │
│  users | roles | departments | documents                    │
│  document_metadata | chunks | chat_sessions                 │
│  chat_messages | activity_logs                              │
│                                                             │
│  Extensions: pgvector                                       │
│  Vector Store: public.vector_store (3072d, cosine)          │
└─────────────────────────────────────────────────────────────┘

             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│              Google Gemini API                              │
│  Chat: gemini-2.5-flash                                    │
│  Embedding: text-embedding-004 (3072 dimensions)            │
│  Used for: query rewrite, multi-query expansion,           │
│            answer generation, summarization,                │
│            title generation, embedding                      │
└─────────────────────────────────────────────────────────────┘
```

---

## Backend Layers

### Controller Layer (`com.sakh.controller`)

9 controllers exposing REST endpoints under `/api`. Each controller delegates to a service and never contains business logic.

| Controller | Path | Purpose |
|---|---|---|
| `AuthenticationController` | `/auth` | Login, register |
| `UserController` | `/users` | Profile, user management (admin) |
| `DepartmentController` | `/departments` | Department CRUD (admin) |
| `DocumentController` | `/documents` | Upload, search, download, versions |
| `ChatController` | `/chat` | Sessions, messages, streaming |
| `ActivityLogController` | `/activity` | Activity audit log (admin) |
| `DashboardController` | `/dashboard` | Summary metrics |
| `HealthController` | `/health` | Health check (unauthenticated) |
| `MetricsController` | `/metrics` | RAG pipeline metrics |

### Service Layer (`com.sakh.service`)

10 services containing all business logic.

- **AuthenticationService** — register, login, JWT generation
- **UserService** — profile, user list, status toggling
- **DepartmentService** — CRUD operations
- **DocumentService** — upload, search, download, versioning, status management
- **DocumentProcessingService** — async text extraction, chunking, embedding
- **ChunkService** — text chunking strategy
- **ChatService** — session management, message orchestration, RAG pipeline
- **CitationService** — builds citation metadata from retrieved documents
- **ActivityLogService** — persists audit events with IP resolution
- **DashboardService** — aggregates document/query/user counts
- **MetricsCollector** — in-memory RAG performance metrics

### RAG Pipeline (`com.sakh.rag`)

7 components forming the RAG pipeline:

1. **QueryRewriter** — uses Gemini to reformulate the user question for better retrieval
2. **MultiQueryRetriever** — generates 3 query variations, retrieves for each, merges results
3. **RetrieverService** — hybrid search (semantic vector + keyword) with access-control filtering
4. **PromptBuilder** — assembles context, conversation history, and question into a structured prompt
5. **AnswerVerifier** — sentence-level grounding check against source documents (30% word-overlap threshold)
6. **ConversationSummarizer** — auto-summarizes long conversations at 20-message intervals
7. **TitleGenerator** — generates chat session titles from the first user message

### Data Layer

- **9 JPA entities** mapped to PostgreSQL tables
- **9 Spring Data JPA repositories**
- **7 Flyway migrations** for schema and seed data
- **Spring AI PgVectorStore** for vector operations (3072 dimensions, cosine distance)

### Security Layer (`com.sakh.security`)

- **JwtService** — token creation (HMAC-SHA512), validation, claim extraction
- **JwtAuthenticationFilter** — extracts JWT from `Authorization: Bearer` header, validates, sets SecurityContext
- **CustomUserDetailsService** — loads user from database by email
- **SecurityConfig** — CORS, CSRF disable, stateless sessions, route permissions, filter chain
- **PasswordConfig** — BCryptPasswordEncoder bean
- **PromptSecurityService** — validates user prompts against injection patterns

---

## Frontend Structure

### Pages (`frontend/src/pages`)

9 route pages:

- **Login** — email/password form, redirects to dashboard
- **Dashboard** — admin: role-aware summary cards (documents, queries, users, recent activity)
- **Users** — admin-only: user table, create user dialog, status toggle
- **Departments** — admin-only: department CRUD
- **Documents** — file upload, document table with status/search, download
- **Chat** — AI chat interface with streaming, source citations displayed in drawer
- **ActivityLogs** — admin-only: paginated audit log
- **Profile** — current user profile display
- **NotFound** — 404 page

### Services (`frontend/src/services`)

7 Axios-based API service modules, all using a shared `api.js` instance that:
- Reads `VITE_API_BASE_URL` from environment
- Attaches `Authorization: Bearer <token>` from localStorage to every request
- Handles 401 responses by clearing auth state and redirecting to `/login`
- Routes are protected client-side for UX; real authorization is on the backend

---

## Database

### Tables (7 Flyway migrations)

| Migration | Purpose |
|---|---|
| V1 | Core schema: users, roles, departments, documents, chunks, document_metadata |
| V2 | Document versioning fields (groupId, version, isLatest) + embedding columns |
| V3 | Remove embedding columns from chunks (moved to vector_store) |
| V4 | Chat sessions and messages |
| V5 | Activity log table |
| V6 | pgvector extension + vector_store table |
| V7 | Seed initial admin user + IT department |

### Entity Relationships

- **User** → Role (Many-to-One)
- **User** → Department (Many-to-One)
- **Document** → Department (Many-to-One)
- **Document** → User (uploadedBy, Many-to-One)
- **Chunk** → Document (Many-to-One)
- **ChatSession** → User (Many-to-One)
- **ChatMessage** → ChatSession (Many-to-One)

---

## Authentication Flow

```
Login Request
    │
    ▼
POST /auth/login { email, password }
    │
    ▼
AuthenticationService.login()
    ├── userRepository.findByEmail()
    ├── passwordEncoder.matches(password, hash)
    ├── jwtService.generateToken(userDetails) → JWT with subject=email
    ├── activityLogService.log(LOGIN)
    └── Return { token, type: "Bearer", expiresIn: 86400000 }

Subsequent Requests
    │
    ▼
Authorization: Bearer <token>
    │
    ▼
JwtAuthenticationFilter.doFilterInternal()
    ├── Extract token from header
    ├── jwtService.extractUsername(token) → email
    ├── CustomUserDetailsService.loadUserByUsername(email)
    ├── jwtService.isTokenValid(token, userDetails)
    └── Set SecurityContext with authenticated principal
```

---

## Document Ingestion Flow

```
Upload Request
    │
    ▼
POST /documents/upload (multipart file + departmentId)
    │
    ▼
DocumentService.uploadDocument()
    ├── Validate file type (PDF, DOCX)
    ├── storageService.store(file) → storage path
    ├── Check for existing version → increment version or create group
    ├── Save Document entity (status: UPLOADED)
    └── Trigger async DocumentProcessingService.processDocument()

Async Processing
    │
    ▼
DocumentProcessingService.processDocument()
    ├── Set status → PROCESSING
    ├── Extract text via parser (PDFBox/POI)
    ├── metadataService.extractAndSave() → PDF metadata (title, author, pages)
    ├── chunkService.chunkDocument()
    │   ├── Split by sections (## headers)
    │   ├── Fallback: sentence-based splitting with 500-char target chunks
    │   └── 100-char overlap between chunks
    ├── Build Spring AI Document objects with metadata
    ├── vectorStore.add(documents) → embedding generation + pgvector insert
    └── Set status → READY (or FAILED on error)
```

---

## RAG Query Flow

```
User Question
    │
    ▼
POST /chat/{sessionId}/message { message }
    │
    ▼
ChatService.sendMessage()
    ├── promptSecurityService.validate(message) — injection check
    ├── Save user message to DB
    ├── Generate chat title if first message (TitleGenerator)
    │
    ├── Query Rewriting
    │   └── QueryRewriter.rewrite() → Gemini reformulates question
    │
    ├── Multi-Query Expansion
    │   └── MultiQueryRetriever → 3 Gemini-generated query variants
    │
    ├── Hybrid Retrieval (RetrieverService)
    │   ├── Semantic search: vectorStore.similaritySearch() → pgvector cosine similarity
    │   ├── Keyword search: chunkRepository.findKeywordSearchGlobal() → PostgreSQL text search
    │   ├── Access control filter: role/department-based filtering
    │   └── Merge & rank: weighted fusion (0.7 semantic + 0.3 keyword)
    │
    ├── Context Assembly (PromptBuilder)
    │   ├── Load conversation history or summary
    │   ├── Build structured prompt with context + history + question
    │   └── Cap context at 12,000 characters
    │
    ├── Answer Generation (LLMService)
    │   └── Gemini 2.5 Flash → generated answer
    │
    ├── Hallucination Check (AnswerVerifier)
    │   └── Sentence-level grounding: each sentence must share ≥30% word overlap with source chunks
    │
    ├── Citation Building (CitationService)
    │   └── Map retrieved documents to CitationDTOs with title, page, chunk, similarity score
    │
    ├── Save assistant message + citations to DB
    ├── Summarize conversation if at 20-message boundary
    ├── Log activity
    └── Return { answer, confidence, citations }
```
