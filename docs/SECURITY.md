# Security

## Overview

SAKH implements defense-in-depth security. Access control is enforced at every layer — authentication, API endpoints, service methods, database queries, and RAG retrieval. Frontend route guards exist for user experience only and do not substitute for server-side authorization.

---

## Authentication

### JWT (JSON Web Token)

- Tokens are issued on successful login via `POST /auth/login`
- Format: stateless JWT signed with HMAC-SHA512 (`HS512`)
- Claims: `sub` (user email), `iat`, `exp`
- Default expiration: 24 hours (configurable via `security.jwt.expiration`)
- The signing key is derived from `JWT_SECRET` environment variable (256+ bits recommended)
- Every protected request validates the token via `JwtAuthenticationFilter`
- Token is extracted from the `Authorization: Bearer <token>` header

### Password Hashing

- BCrypt via Spring Security `PasswordEncoder` (default strength: 10 rounds)
- Passwords are never stored in plaintext
- The admin seed user (`V7__seed_initial_admin.sql`) uses PostgreSQL `pgcrypto`'s `crypt()` with `gen_salt('bf', 10)` — BCrypt-compatible hash

### Registration

- User registration requires ADMIN role (`/auth/register` is restricted to `hasRole('ADMIN')`)
- No public signup is available
- New users are registered with ACTIVE status by default

---

## Authorization

### Role-Based Access Control (RBAC)

Four roles with hierarchical permissions:

| Role | Description |
|---|---|
| `ADMIN` | Full system access — all documents, all users, departments, activity logs |
| `MANAGER` | Department-scoped access — documents in their department, department users |
| `EMPLOYEE` | Document upload and chat — department documents + own uploads |
| `GUEST` | Read-only — only documents explicitly uploaded by/for them |

### Enforcement Points

1. **Request Matchers** (`SecurityConfig.java`)
   - `/auth/login`, `/health`, `/swagger-ui/**`, `/v3/api-docs/**` — permit all
   - `/auth/register` — ADMIN only
   - Everything else — authenticated

2. **Method-Level Annotations** (`@PreAuthorize`)
   - `UserController.getAllUsers()` — ADMIN
   - `UserController.updateUserStatus()` — ADMIN
   - `DepartmentController` — ADMIN for create/update/delete
   - `ActivityLogController.getAll()` — ADMIN

3. **Service-Level Checks**
   - ChatService verifies session ownership before returning messages
   - DocumentService filters by department for non-admin users

### Department-Based Access

Documents are associated with a department at upload time. Access is enforced in the RAG retrieval layer:

- **ADMIN**: all documents across all departments
- **MANAGER**: documents in their own department
- **EMPLOYEE**: documents in their own department + documents they uploaded
- **GUEST**: only documents they uploaded

This filtering happens in `RetrieverService.isAccessible()` and applies to both semantic and keyword search results. The filter expression is passed to pgvector at query time.

---

## CORS

- Configurable via `CORS_ALLOWED_ORIGINS` environment variable
- Supports multiple origins as a comma-separated list
- Default (local development): `http://localhost:3000,http://localhost:5173`
- Methods: GET, POST, PUT, DELETE, PATCH, OPTIONS
- Headers: all headers allowed
- Credentials: enabled (cookies, auth headers)
- Does not use wildcard `*` with credentials

---

## Secret Management

All secrets are injected via environment variables at deploy time:

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL` | Database connection URL |
| `SPRING_DATASOURCE_USERNAME` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `JWT_SECRET` | JWT signing key |
| `GEMINI_API_KEY` | Google Gemini API key |

### Local Development

- Backend `application.yml` uses `${VAR:default}` syntax with safe local defaults
- Frontend `.env.example` contains development values only
- No `.env` files are tracked in version control

### Production

- Render dashboard provides a secure environment variable editor
- Neon connection strings are set as environment variables, not hardcoded
- Frontend environment variables contain only `VITE_API_BASE_URL` — no database credentials or API keys

---

## Database Security

- PostgreSQL with SSL enforced (`?sslmode=require`)
- Connection pooling via HikariCP with configurable pool size
- Flyway migrations are the single source of truth for schema changes
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate validates entity mappings against the actual schema on startup
- The `pgcrypto` extension is used for secure password hashing in seed data

---

## RAG Security

- User prompts are validated against injection patterns (`PromptSecurityService`)
- Document retrieval filters by the authenticated user's role and department
- Answer generation only uses context from permitted documents
- The `AnswerVerifier` removes sentences that cannot be grounded in the source documents
- No information from restricted documents is included in AI responses

---

## Responsible Security Practices

### Do Not Commit

- `.env` files
- API keys
- JWT secrets
- Database passwords
- Connection strings containing credentials
- Private keys or certificates

### If a Secret Is Committed

Simply deleting the file from the repository is not sufficient. The secret must be rotated immediately:
- Regenerate the Gemini API key
- Change the database password
- Generate a new JWT secret
- Check for unauthorized access to affected services

### Recommended Practices

- Use environment variables or a secrets manager (AWS Secrets Manager, HashiCorp Vault) for all credentials
- Rotate secrets regularly
- Use a unique JWT secret per environment (dev, staging, production)
- Enable branch protection on the main branch
- Run automated secret scanning in CI/CD
