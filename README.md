# Secure AI Knowledge Hub (SAKH)

A full-stack enterprise knowledge management platform that enables organizations to securely store, process, search, and retrieve internal documents using Retrieval-Augmented Generation (RAG) with Role-Based Access Control (RBAC).

## Overview

SAKH allows organizations to upload internal documents (PDF, DOCX, TXT) into a centralized knowledge repository. The system automatically extracts text, generates embeddings, and indexes content for semantic search. Users can ask natural language questions and receive AI-generated answers with citations sourced exclusively from documents they are authorized to access.

The platform enforces permissions at every layer: authentication, API access, document retrieval, and answer generation. No information from a restricted document is ever included in an AI response.

## Architecture

```
                              +-----------------+
                              |   React    |
                              |   Frontend      |
                              +--------+--------+
                                       |
                                  JWT Auth
                                       |
                              +--------v--------+
                              |  Spring Boot    |
                              |  REST API       |
                              +--------+--------+
                                       |
                    +------------------+------------------+
                    |                                     |
            +-------v-------+                    +--------v--------+
            | Spring         |                    |  RAG Pipeline   |
            | Security (JWT) |                    |  - Query Rewrite|
            +---------------+                    |  - Multi-Query  |
                                                  |  - Retrieval    |
            +-------v-------+                    |  - Verification |
            | Business       |                    |  - LLM Response |
            | Services       |                    +--------+--------+
            +-------+-------+                             |
                    |                                      |
            +-------v-------+                    +--------v--------+
            | Spring Data   |                    |  Spring AI      |
            | JPA / Flyway  |                    |  (Gemini)       |
            +-------+-------+                    +--------+--------+
                    |                                      |
                    +------------------+------------------+
                                       |
                            +----------v----------+
                            |   PostgreSQL 17     |
                            |   + pgvector 0.8    |
                            +---------------------+
```

### Request Flow

```
User Request
     |
     v
JWT Authentication Filter
     |
     v
Controller (validates input)
     |
     v
Service Layer (business logic, RBAC check)
     |
     v
Repository Layer (data access)
     |
     v
Database
```

### RAG Pipeline Flow

```
User Question
     |
     v
Prompt Security Filter (injection detection)
     |
     v
Query Rewriter (ChatModel - Gemini)
     |
     v
Multi-Query Retriever (3 paraphrased queries)
     |
     v
Permission Filter (RBAC on metadata)
     |
     v
Vector Search (pgvector - cosine similarity)
     |
     v
Result Merge / Dedup / Rank
     |
     v
Context Assembly + Prompt Building
     |
     v
LLM Answer Generation (StreamingChatModel)
     |
     v
Answer Verifier (hallucination detection)
     |
     v
Response with Citations + Confidence
```

## Technology Stack

### Backend

| Technology | Version |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.5.16 |
| Spring AI | 1.1.8 |
| Spring Security | 6.x |
| Spring Data JPA | 3.x |
| Hibernate | 6.6 |
| PostgreSQL | 17 |
| Flyway | 10.x |
| JJWT | 0.12.6 |
| Apache PDFBox | 3.0.4 |
| Apache POI | 5.3.0 |
| SpringDoc OpenAPI | 2.8.5 |

### Frontend

| Technology | Version |
| --- | --- |
| React | 19.x |
| JavaScript (ES2022) | — |
| Material UI | 6.x |
| Axios | 1.x |
| React Router | 7.x |
| Vite | 6.x |
| React Hook Form | 7.x |
| React Markdown | 10.x |
| React Toastify | 11.x |

### AI

| Technology | Purpose |
| --- | --- |
| Google Gemini (via Spring AI) | Chat completions, streaming, query rewriting, title/summary generation |
| Google Gemini Embedding | Document embedding generation |
| Spring AI PgVectorStore | Vector storage and similarity search |
| pgvector 0.8 | PostgreSQL vector extension |

## Features

### Authentication and Authorization
- JWT-based authentication with access and refresh token flow
- Four role levels: ADMIN, MANAGER, EMPLOYEE, GUEST
- Role-Based Access Control at endpoint and document level
- BCrypt password hashing

### Document Management
- Upload PDF, DOCX, and TXT files
- Automatic text extraction via Apache PDFBox and Apache POI
- Metadata extraction (author, language, summary, page count, tags)
- Document reprocessing pipeline
- Chunking with configurable overlap
- Search documents by keyword with pagination
- Document preview with metadata summary

### RAG Pipeline
- **Prompt Security Service:** 11 regex patterns blocking prompt injection (ignore instructions, reveal prompt, jailbreak, DAN mode)
- **Query Rewriter:** Expands user questions for better retrieval using ChatModel (Gemini)
- **Multi-Query Retriever:** Generates 3 paraphrased queries, retrieves chunks for each, merges by chunk ID keeping highest score, returns top K
- **Vector Search:** Cosine similarity search via pgvector with RBAC metadata filtering
- **Hybrid Context:** Full conversation history with automatic summarization every 20 messages
- **Answer Generation:** Streaming support via SSE (Server-Sent Events)
- **Hallucination Verification:** Sentence-level word overlap check against source chunks (30% threshold)
- **Title Generation:** Auto-generates chat session titles from first user question

### Frontend UI
- Role-based sidebar and dashboard (ADMIN sees all; MANAGER/EMPLOYEE see filtered views)
- User management with CRUD, role/department/status filters, Enable/Disable toggle
- Department management with full CRUD and pagination
- Document management with drag-and-drop upload, progress bar, status polling, download, preview metadata, version history, reprocess
- AI Chat with Markdown rendering, sources drawer, confidence labels (High/Medium/Low), typing indicator
- Paginated activity log with search and action-type color coding
- Profile page with account info, member-since date, and inline editing
- Responsive MUI design with consistent blue theme

### API
- All endpoints documented via OpenAPI 3.0 / Swagger UI
- Standardized API error responses with field-level validation errors
- Pagination, sorting, and filtering on list endpoints

### Admin
- Dashboard with aggregate counts (users, departments, documents, chat sessions, messages, vector store entries)
- Activity log with paginated audit trail (LOGIN, UPLOAD, DOWNLOAD, DELETE, REPROCESS, CHAT)
- RAG evaluation metrics (recall, precision, average similarity, average response time, hallucination rate)

### Security
- All secrets and credentials via environment variables
- X-Forwarded-For header support for client IP resolution in audit logs
- Password hashing with BCrypt

## Project Structure

```
Secure-AI-Knowledge-Hub/
|
+-- backend/
|   +-- src/
|   |   +-- main/java/com/sakh/
|   |   |   +-- ai/                  # AI service abstractions
|   |   |   +-- config/              # Swagger, app configuration
|   |   |   +-- controller/          # REST controllers
|   |   |   +-- dto/                 # Data transfer objects
|   |   |   +-- entity/              # JPA entities
|   |   |   +-- enums/               # Role, activity type enums
|   |   |   +-- exception/           # Global exception handler, custom exceptions
|   |   |   +-- knowledge/           # Knowledge processing utilities
|   |   |   +-- llm/                 # LLM service abstraction
|   |   |   +-- processing/          # Document processing pipeline
|   |   |   +-- rag/                 # RAG pipeline components
|   |   |   |   +-- AnswerVerifier.java
|   |   |   |   +-- ConversationSummarizer.java
|   |   |   |   +-- MultiQueryRetriever.java
|   |   |   |   +-- PromptBuilder.java
|   |   |   |   +-- QueryRewriter.java
|   |   |   |   +-- RetrieverService.java
|   |   |   |   +-- TitleGenerator.java
|   |   |   +-- repository/          # JPA repositories
|   |   |   +-- security/            # JWT filter, security config, user details
|   |   |   +-- service/             # Business logic services
|   |   |   +-- storage/             # File storage abstraction
|   |   |   +-- util/                # Utility classes
|   |   |   +-- validation/          # Custom validators
|   |   +-- main/resources/
|   |   |   +-- db/migration/        # Flyway migrations (V1-V5)
|   |   |   +-- application.yml
|   |   |   +-- application-dev.yml
|   |   +-- test/
|   +-- pom.xml
|
+-- frontend/                        # React + JavaScript application
|   +-- src/
|       +-- assets/
|       +-- common/
|       +-- components/
|       |   +-- layout/              # MainLayout, Navbar, Sidebar
|       +-- context/                 # AuthContext
|       +-- hooks/
|       +-- pages/                   # Dashboard, Users, Departments, Documents, Chat, ActivityLogs, Profile, Login, NotFound
|       +-- routes/                  # AppRoutes with PrivateRoute guard
|       +-- services/                # Axios-based API services
|       +-- utils/
|       +-- App.jsx
|       +-- theme.js                 # MUI theme configuration
|
+-- docker/                          # Docker Compose configuration
+-- docs/                            # Project documentation
|   +-- architecture/
|   +-- contracts/
|   +-- vision/
+-- knowledge/                       # Sample knowledge documents
+-- scripts/                         # Utility scripts
+-- prompts/                         # LLM prompt templates
+-- storage/                         # Uploaded file storage
|
+-- AGENTS.md                        # AI agent development rules
+-- LICENSE                          # MIT License
```

## Database

The system uses PostgreSQL 17 with pgvector for vector similarity search. Schema migrations are managed by Flyway.

### Tables

| Table | Purpose |
| --- | --- |
| `roles` | System roles (ADMIN, MANAGER, EMPLOYEE, GUEST) |
| `departments` | Organization departments |
| `users` | Application users with BCrypt-hashed passwords |
| `documents` | Uploaded document records with status tracking |
| `document_metadata` | Extracted metadata (summary, author, tags, language, page count) |
| `chunks` | Document text chunks with vector embeddings |
| `chat_sessions` | User conversation sessions |
| `chat_messages` | Individual messages with citations and confidence scores |
| `activity_logs` | Audit trail for security events |
| `vector_store` | PgVectorStore-managed embedding table |

### Migrations

| Migration | Description |
| --- | --- |
| V1 | Initial schema: roles, departments, users, documents, document_metadata, chunks, chat_sessions, chat_messages |
| V2 | Add versioning columns and pgvector extension |
| V3 | Remove embedding columns (migrated to vector_store) |
| V4 | Add summary field to chat_sessions |
| V5 | Create activity_logs table |

## Spring AI Integration

Spring AI provides the abstraction layer for AI model interactions, eliminating direct API dependencies.

### Models Used

- **ChatModel (Gemini):** Query rewriting, title generation, conversation summarization
- **StreamingChatModel (Gemini):** Token-by-token streaming answer generation
- **EmbeddingModel (Gemini):** Document embedding generation for vector search
- **PgVectorStore:** Vector storage with metadata filtering for RBAC

### Vector Store Filter Syntax

Metadata filters for RBAC use Spring AI vector store filter expressions:

- Equality: `departmentId == 1`
- Inequality: `uploadedBy != 'user@email.com'`
- Email strings require single quotes: `uploadedBy == 'admin@sakh.com'`
- Logical AND: `departmentId == 1 AND uploadedBy == 'admin@sakh.com'`
- Logical OR: `departmentId == 1 OR uploadedBy == 'admin@sakh.com'`

## PgVector

The vector store uses the `pgvector` PostgreSQL extension for efficient approximate nearest neighbor search. Key details:

- Vector dimension: 768 (Google Gemini Embedding)
- Index: IVFFlat with cosine distance
- Query: cosine similarity search with metadata filtering
- Table: `vector_store` (managed by Spring AI PgVectorStore)

## RAG Pipeline Detail

### 1. Prompt Security
All user input is scanned against 11 regex patterns before any processing. Detected injection attempts are logged and blocked with a 400 response.

### 2. Query Rewriting
The raw user question is sent to Gemini ChatModel for expansion into a more specific search query. On failure, the original query is used.

### 3. Multi-Query Retrieval
The rewritten query is expanded into 3 paraphrased variants. Each variant is independently searched against the vector store. Results are merged by `chunkId`, keeping the highest similarity score, then sorted descending and truncated to top K.

### 4. Permission Filtering
Every vector search includes metadata filters (`departmentId`, `uploadedBy`) derived from the authenticated user's role and department. ADMIN users bypass document-level filtering.

### 5. Context Assembly
Retrieved chunks are assembled with conversation history. If the session has more than 20 messages, a summary replaces earlier messages to fit context windows.

### 6. Answer Generation
The assembled prompt is sent to Gemini via StreamingChatModel for real-time streaming or via ChatModel for standard responses.

### 7. Answer Verification
The generated answer is split into sentences. Each sentence is checked against source chunks using word overlap. Sentences with less than 30% word overlap with any source chunk are removed. The response includes the verified text and metadata about removed content.

### 8. Activity Logging
Every chat interaction is recorded in the activity log with user identity, timestamp, and client IP.

## Installation

### Prerequisites

- JDK 21
- Maven 3.9+
- PostgreSQL 17 with pgvector 0.8
- Node.js 18+ (for frontend)
- Google Gemini API key

### Environment Variables

Copy `backend/.env.example` to `backend/.env` and fill in the values:

```
cp backend/.env.example backend/.env
```

| Variable | Required | Default | Description |
| --- | --- | --- | --- |
| `GEMINI_API_KEY` | Yes | -- | Google Gemini API key. Obtain from https://aistudio.google.com/app/apikey |
| `JWT_SECRET` | No | auto-generated | Base64-encoded JWT signing secret. Generate with `openssl rand -base64 64`. Minimum 256-bit. |
| `DB_HOST` | No | `localhost` | PostgreSQL host address |
| `DB_PORT` | No | `5432` | PostgreSQL port |
| `DB_NAME` | No | `sakh_db` | PostgreSQL database name |
| `DB_USERNAME` | No | `postgres` | PostgreSQL database user |
| `DB_PASSWORD` | No | `postgres` | PostgreSQL database user password |

### Run Locally

1. Clone the repository:
   ```
   git clone https://github.com/your-org/Secure-AI-Knowledge-Hub.git
   cd Secure-AI-Knowledge-Hub
   ```

2. Set up PostgreSQL database:
   ```sql
   CREATE DATABASE sakh_db;
   CREATE EXTENSION IF NOT EXISTS vector;
   ```

3. Configure environment variables:
   ```
   cp backend/.env.example backend/.env
   ```
   Then edit `backend/.env` with your Gemini API key and database credentials.

4. Build and run the backend:
   ```
   cd backend
   mvn clean install -DskipTests
   mvn spring-boot:run
   ```

5. Run the frontend:
   ```
   cd frontend
   npm install
   npm run dev
   ```

   The frontend runs at `http://localhost:5173` and proxies API requests to `http://localhost:8080/api`.

### Swagger UI

Once the backend is running, access the API documentation at:

```
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON specification:

```
http://localhost:8080/v3/api-docs
```

### Docker Deployment

#### Prerequisites

- Docker Desktop (or Docker Engine + Docker Compose)
- Google Gemini API key

#### Quick Start

1. Set your Gemini API key as an environment variable:

   **Windows (PowerShell):**
   ```powershell
   $env:GEMINI_API_KEY="your_gemini_key"
   ```

   **Linux / macOS:**
   ```sh
   export GEMINI_API_KEY=your_gemini_key
   ```

2. Build and start all services:

   ```sh
   docker compose build
   docker compose up -d
   ```

   This starts:
   - **PostgreSQL 17** with pgvector on port `5432`
   - **Backend API** on port `8080`
   - **Frontend** on port `3000`

3. Access the application:

   | Service | URL |
   | --- | --- |
   | Frontend | http://localhost:3000 |
   | Swagger UI | http://localhost:8080/api/swagger-ui/index.html |
   | OpenAPI Spec | http://localhost:8080/api/v3/api-docs |

#### Configuration

Create a `.env` file in the project root to customize settings:

```sh
# Required
GEMINI_API_KEY=your_gemini_key

# Optional (defaults shown)
JWT_SECRET=9a8b7c6d5e4f3g2h1i0j9k8l7m6n5o4p3q2r1s0t9u8v7w6x5y4z321098765432
```

#### Commands

| Command | Description |
| --- | --- |
| `docker compose build` | Build all images |
| `docker compose up -d` | Start all services in background |
| `docker compose down` | Stop and remove all containers |
| `docker compose logs -f` | Follow all service logs |
| `docker compose logs -f backend` | Follow backend logs only |
| `docker compose ps` | List running services |

#### Supabase Deployment

To deploy using a Supabase PostgreSQL database instead of the local PostgreSQL container:

```sh
docker compose -f docker/docker-compose.supabase.yml build
docker compose -f docker/docker-compose.supabase.yml up -d
```

This starts only the **Backend** and **Frontend** services, connecting directly to the Supabase PostgreSQL instance.

#### Volumes

Persistent data is stored in Docker volumes:

- `postgres-data` — database files (local deployment only)
- `uploads` — uploaded documents
- `logs` — application logs

## Authentication

This is an internal company application. There is no public registration.

### Fresh Deployment Flow

1. Flyway migrations execute on first startup
2. Default roles and department are created
3. Default administrator account is created
4. Admin logs in with the demo credentials
5. Admin creates employee accounts through the User Management UI
6. Employees log in with their created credentials

No public registration is available. Users must be created by an administrator.

### Demo Credentials

| Role | Email | Password |
| --- | --- | --- |
| Administrator | `admin@sakh.com` | `Admin@123` |

This account is automatically created on first startup by Flyway migration V7.

## Screenshots

![Login Page](docs/screenshots/login.png)
![Dashboard](docs/screenshots/dashboard.png)
![Chat Interface](docs/screenshots/chat.png)
![Swagger UI](docs/screenshots/swagger.png)

## API Error Response Format

All API errors follow a consistent structure:

```json
{
  "timestamp": "2026-07-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed.",
  "path": "/api/auth/register",
  "fields": [
    { "field": "email", "message": "must be a well-formed email address" },
    { "field": "password", "message": "must be at least 8 characters" }
  ]
}
```

Standard status codes:

| Code | Usage |
| --- | --- |
| 400 | Validation failure or illegal argument |
| 401 | Authentication failure |
| 403 | Access denied |
| 404 | Resource not found |
| 409 | Duplicate resource |
| 502 | AI service unavailable |
| 500 | Internal server error |

## Testing

The project includes 43+ unit and integration tests covering:

- RAG pipeline components (QueryRewriter, MultiQueryRetriever, AnswerVerifier, ConversationSummarizer, TitleGenerator)
- Prompt security injection detection (20 parameterized test cases)
- Metrics collection
- Chat integration with full pipeline

Run tests:

```
cd backend
mvn test
```

## Future Improvements

- Local LLM support (Ollama, Llama)
- OCR for scanned documents
- SharePoint and Confluence integration
- Multi-language document support
- Knowledge graph integration
- Advanced analytics dashboard with charts
- WebSocket-based real-time notifications
- Document version diff viewer
- Batch document import
- Scheduled document re-indexing

## License

MIT License. See [LICENSE](LICENSE) for details.
