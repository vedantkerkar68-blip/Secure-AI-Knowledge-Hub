## v0.2.1 — 2026-08-28

### Fixed
- Chat session isolation: per-user `localStorage` key `sakh_chat_sessions_{userId}`; removed `clearChatStorage()` on `login`/`logout` in `AuthContext.jsx` — sessions persist for same user across logout/login, different users isolated (no leak)
- Chat title race: replaced separate `persistMessages` + title `setSessions` calls with single functional updater that appends user message then atomically sets title + assistant message — title now updates from first question reliably
- Dashboard: role-based rendering with 403 handling for `GET /admin/dashboard` (ADMIN full cards/actions, MANAGER/EMPLOYEE limited cards + Upload/New Chat)
- Activity Logs: corrected frontend mapping `createdAt`→Timestamp, `userEmail`→User, `resource`→Details; 403 + empty states handled
- Profile: inline edit for first/last name + password via `PUT /users/{id}`
- README/DEPLOYMENT: synced tech stack (React 19.1, Vite 6.3.5, MUI 7.1, Router 7.6.1) and deployment details (Vercel/Render/Neon)

## v0.2.0

### Added
- Server-persisted chat sessions with list, detail, and delete endpoints (V8 migration)
- Document-scoped chat: attach a document to a question to confine retrieval to it
- Token revocation and rotation: `jti` claims, `POST /auth/logout`, `POST /auth/refresh` (V9 migration)
- Account status enforcement at login and per request; revoked/invalid tokens return `401`
- Upload hardening: extension allowlist plus magic-byte content validation and 10MB size cap
- Rate limiting on login, registration, and chat endpoints (429 on breach)
- Activity log filtering (action, search, date range) and summary endpoint
- Admin user management: role assignment with last-admin and self-change guards
- Backend health UX: cold-start aware splash screen, login banners, mid-session outage toasts
- Material UI design system: theme, splash screen, responsive sidebar/navbar, profile dialog

## v0.1.0

### Added
- Project setup
- PostgreSQL configuration
- Flyway migrations
- Role entity
- Department entity
- User entity
- Repository layer
- Global exception handling