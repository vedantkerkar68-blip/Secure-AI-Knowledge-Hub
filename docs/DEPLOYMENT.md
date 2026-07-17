# Deployment

## Architecture

```
User's Browser
    │
    ▼
Vercel (CDN)
  └── React SPA served as static files
      └── API calls via Axios to Render
    │
    ▼
Render (Web Service)
  └── Spring Boot REST API (Java 21)
      ├── Health check: GET /api/health
      └── Environment variables injected at deploy time
    │
    ▼
Neon PostgreSQL (Serverless)
  ├── PostgreSQL 17 + pgvector 0.8
  ├── SSL required (sslmode=require)
  └── Connection via pooled endpoint (-pooler)
    │
    ▼
Google Gemini API
  └── External API calls from Render
      ├── Chat: gemini-2.5-flash
      └── Embeddings: text-embedding-004
```

---

## Frontend — Vercel

### Setup

1. Connect your GitHub repository to Vercel
2. Set the **Root Directory** to `frontend`
3. Vercel auto-detects Vite and uses the build command from `package.json`

### Environment Variables (Vercel Dashboard)

| Variable | Value |
|---|---|
| `VITE_API_BASE_URL` | `https://secure-ai-knowledge-hub.onrender.com/api` |

### SPA Routing

A `vercel.json` file is included in the frontend root:

```json
{
  "rewrites": [
    { "source": "/(.*)", "destination": "/index.html" }
  ]
}
```

This ensures all routes (`/dashboard`, `/chat`, etc.) serve `index.html` so React Router handles them client-side. API calls are unaffected since they target the Render backend domain.

---

## Backend — Render

### Setup

1. Create a new **Web Service** on Render
2. Connect your GitHub repository
3. Configure:
   - **Name**: `secure-ai-knowledge-hub`
   - **Region**: Choose closest to your users
   - **Branch**: `main`
   - **Runtime**: `Docker`
   - **Docker Build Context Directory**: `backend`
   - **Dockerfile Path**: `backend/Dockerfile`
   - **Health Check Path**: `/api/health`

### Environment Variables (Render Dashboard)

| Variable | Required | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | Yes | Neon PostgreSQL JDBC URL with `?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | Yes | Database user |
| `SPRING_DATASOURCE_PASSWORD` | Yes | Database password |
| `JWT_SECRET` | Yes | 256+ bit random secret for JWT signing |
| `GEMINI_API_KEY` | Yes | Google Gemini API key |
| `CORS_ALLOWED_ORIGINS` | Yes | `https://secure-ai-knowledge-hub.vercel.app` |
| `APP_STORAGE_UPLOAD_DIR` | No | Defaults to `./storage/uploads` |
| `SPRING_PROFILES_ACTIVE` | No | Set to `prod` for production-optimized settings |

> Render automatically injects the `PORT` environment variable. The application reads it via `server.port=${PORT:8080}`.

### Cold Starts

Render free-tier services spin down after 15 minutes of inactivity. The first request after idle may take 30-60 seconds while the service starts. A health check ping can mitigate this for monitoring purposes.

---

## Database — Neon PostgreSQL

### Setup

1. Create a Neon project at [neon.tech](https://neon.tech)
2. Enable the **pgvector** extension (enabled by default on Neon)
3. Obtain the connection string from the Neon dashboard

### Connection String Format

```
jdbc:postgresql://<host>:5432/<database>?sslmode=require
```

Use the **pooled connection** endpoint (contains `-pooler` in the hostname) for better connection management with Spring Boot's HikariCP.

### Schema Management

Flyway runs automatically on backend startup. Migrations are applied in order (V1 through V7). The first deployment creates all tables and seeds the admin user.

---

## Docker (Local Development)

### Prerequisites

- Docker Desktop
- Gemini API key

### Quick Start

```sh
# Clone
git clone https://github.com/vedantkerkar68-blip/Secure-AI-Knowledge-Hub.git
cd Secure-AI-Knowledge-Hub

# Set Gemini API key as environment variable
export GEMINI_API_KEY=your_key_here

# Start all services
docker compose up -d

# Access
# Frontend: http://localhost:3000
# API:      http://localhost:8080/api
# Swagger:  http://localhost:8080/api/swagger-ui.html
```

### Database Override Files

For production database testing without a local PostgreSQL container:

```sh
# Neon
docker compose -f docker/docker-compose.neon.yml up -d

# Supabase
docker compose -f docker/docker-compose.supabase.yml up -d
```

---

## CI/CD Notes

The project does not include a CI/CD pipeline configuration. Deployments are manual through the Vercel and Render dashboards, triggered by pushes to the `main` branch.

### Recommended Additions

- GitHub Actions workflow for running backend tests on PR
- Automated deployment to Render on merge to main
- Vercel auto-deploys on push to main (enabled by default)
- Database migration verification step before deployment
