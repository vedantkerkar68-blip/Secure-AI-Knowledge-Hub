## Unreleased

### Security
- **RAG readiness enforced for every role**: `DocumentAuthorizationService.isAccessibleForRag()` now checks document status *before* any role or access-scope decision, so `READY` is required for content to enter an LLM prompt for **all** roles including `ADMIN`, and for every access scope including `AccessScope = ALL`. Previously `ADMIN` returned `true` before any status check, allowing `PENDING` / `PROCESSING` / `FAILED` content into a prompt.
- **`isAccessibleForRag()` fails closed on null input**: a null document or a null user now returns `false` instead of throwing `NullPointerException`.
- **Document management is unchanged**: `requireReadable()` still exempts `ADMIN`, so preview, details, versions, download and status inspection continue to work for documents that are not `READY`. Management permission and LLM-processing eligibility are now explicitly separate concerns.

### Fixed
- **Authorized chunks no longer lost in global retrieval**: candidate generation is permissive for recall and the vector filter has no status clause, so non-`READY` candidates were occupying the `topK` window and then being discarded, permanently dropping authorized chunks that ranked just below the cut. The window is now ranked at its full `fetchSize`, authorized, and only then capped to `topK`.

### Changed
- **README access-control wording corrected**: the previous "ADMIN sees all" description of vector/keyword search contradicted the readiness rule. The README now states the readiness invariant and distinguishes document-management permissions from LLM-processing eligibility, without duplicating the full policy.

### Notes
- `docs/07_ACCESS_CONTROL_POLICY.md` carries the detailed access-control policy and was updated with the same rule (new section 5.4, plus the resolved-conflict entry). That file is intentionally **local-only** - `.gitignore` excludes `docs/*` except screenshots - so it is not part of any commit. The tracked README carries the invariant and points to it. Whether to track the full policy is an open repository-convention question.

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