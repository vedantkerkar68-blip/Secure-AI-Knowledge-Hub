# Access Control Policy

- **Project:** Secure AI Knowledge Hub (SAKH)
- **Version:** 1.1
- **Status:** Living document — reflects the implemented authorization policy
- **Phase:** 2 — Access-Control Consistency

---

## 1. Purpose

This document defines the authoritative authorization policy for document access and LLM processing in SAKH. It resolves the divergences identified in Phase 1 between `DocumentService` (document API) and `RetrieverService` (RAG retrieval), and records the behavior that is actually implemented today.

---

## 2. Core Principles

### 2.1 Separation of Concerns

| Decision | Question | Enforced By | Status |
|----------|----------|-------------|--------|
| **USER_CAN_VIEW** | May this user open/download this document? | `DocumentAuthorizationService.checkDocumentViewAccess()` / `requireReadable()` | ✅ CURRENT |
| **LLM_CAN_PROCESS (Phase 2 baseline)** | May this content enter the LLM context under the current rule — authorized **and** `READY`? | `DocumentAuthorizationService.isAccessibleForRag()`, applied by `RetrieverService` | ✅ CURRENT |
| **LLM_PROCESS_PERMISSION** (separate tier) | May this content be processed independently of view access? | *new component* | 🧭 PLANNED (Phase 12) |

**Rule:** `USER_CAN_VIEW` does **not** automatically imply `LLM_CAN_PROCESS`. A document may be viewable by a user but still forbidden from LLM processing (e.g. sensitive HR data a manager can view but that must not be sent to an external LLM).

> **Current vs. planned.** The Phase 2 baseline (`LLM_CAN_PROCESS = USER_CAN_VIEW`, plus the mandatory `READY` requirement in §5.4) **is implemented and enforced**. What remains *planned* is a separate, finer-grained processing-permission tier — a separate concept from the current rule, described in §8.

---

## 3. Roles and Their Permissions

| Role | Description |
|------|-------------|
| `ADMIN` | Full document management access, all users, departments, activity logs. RAG retrieval still requires `READY` (§5.4). |
| `MANAGER` | Department-scoped access — own department plus ancestor departments; may upload to their own subtree |
| `EMPLOYEE` | Department documents plus their own uploads |
| `GUEST` | Public/shared knowledge only — `AccessScope.ALL` documents that are `READY` (§3.1) |

### 3.1 GUEST Policy (implemented)

`GUEST` is seeded in the database (`V1__initial_schema.sql`) and is assigned by an
administrator. Its access is **explicitly restricted**, not merely inherited from the
absence of a branch.

| Concern | GUEST rule |
|---------|-----------|
| Document API — list, search, details, status | `AccessScope.ALL` **and** `READY` only |
| Document API — preview, download | `AccessScope.ALL` **and** `READY` only |
| Document API — versions, upload-options | ❌ Denied (administration surface) |
| Global hybrid RAG | `AccessScope.ALL` **and** `READY` only |
| Document-scoped RAG | `AccessScope.ALL` **and** `READY` only |
| Upload, status change, reprocess, delete | ❌ Denied |
| User / department administration | ❌ Denied |
| Citation metadata | Owning department **redacted** (§3.2) |
| Chat | ✅ Allowed over public/shared knowledge only |

**GUEST never receives department-scoped access**, regardless of:

- department membership (including the guest's own department);
- position in the department hierarchy (ancestor or descendant);
- uploader identity — the EMPLOYEE own-upload exception does not apply to `GUEST`.

### 3.2 Citation Metadata

Citations returned in a chat answer may expose the owning document's department
alongside its title. The access-control policy establishes that an `AccessScope.ALL`
document is *visible* to all authenticated users; it does **not** establish that the
owning department is itself public information. GUEST citations therefore omit
`department`. All other roles keep the existing behaviour unchanged.

Redaction is applied in `CitationService` after the documents have already passed
authorization, so it narrows disclosure only and never grants access.

### 3.3 Enforcement Layers

GUEST access is enforced independently at three layers. The controller rule is not a
substitute for the layers beneath it.

1. **HTTP routing (`DocumentController`)** — the read-only document endpoints are
   `hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE', 'GUEST')`. Every mutation, the version
   history, and upload-options remain `ADMIN`/`MANAGER`/`ADMIN`-only.
2. **Service (`DocumentAuthorizationService`)** — `checkDocumentViewAccess()` denies
   GUEST any document that is not `AccessScope.ALL`; `requireReadable()` additionally
   enforces `READY` because GUEST is not ADMIN; `isAccessibleForRag()` applies the same
   rule to both retrieval paths; `validateUploadTarget()` denies upload.
3. **Query (`DocumentRepository`)** — `getAllDocuments()` and `searchDocuments()` pass an
   `allScopeOnly` flag that restricts the **query itself**, so pagination counts only
   permitted rows. Filtering after the query would corrupt page boundaries.

---

## 4. Document Access Policy (USER_CAN_VIEW)

### 4.1 Visibility Rules (Document API)

A user may view/download a document if **any** of the following is true:

| Condition | ADMIN | MANAGER | EMPLOYEE |
|-----------|-------|---------|----------|
| `accessScope = ALL` | ✅ | ✅ | ✅ |
| Document in user's own department | ✅ | ✅ | ✅ |
| Document in an ancestor department | ✅ | ✅ | ✅ |
| Document in a descendant department | ✅ (any) | ❌ | ❌ |
| Document in an unrelated department | ✅ (any) | ❌ | ❌ |
| Own upload (`EMPLOYEE`) | N/A | N/A | ✅ (see note) |
| Document status = READY | ✅ | ✅ | ✅ |
| Document status ≠ READY | ✅ (management only) | ❌ (404) | ❌ (404) |

> **Own upload note.** The own-upload exception applies to `EMPLOYEE` only, and only on the department-scoped path (`accessScope = DEPARTMENT`); `AccessScope = ALL` documents are already visible to everyone. Critically, it applies **identically in the document API and in RAG** — `checkDocumentViewAccess()` and `isAccessibleForRag()` contain the same own-upload fallback. The product question of whether this exception *should* exist outside the employee's department remains open (§10, #4).

> **Descendant note.** `ADMIN` reaches every document regardless of department. For `MANAGER` and `EMPLOYEE`, read visibility is the user's own department **plus its ancestors only** — the descendant subtree is *not* included. Descendant/subtree scoping applies only to *upload targets* (§6). Whether `MANAGER` *should* see descendants is unresolved (§10, #3).

### 4.2 Department Hierarchy Semantics

- **Ancestor chain:** User's department → parent → grandparent → ... → root
- **Descendant subtree:** User's department → all children → grandchildren → ...
- **Read visibility (implemented):** own department + ancestor chain, applied identically to every non-`ADMIN` role
- **Upload scope (implemented):** `MANAGER` may upload to their own department plus the entire descendant subtree
- **ADMIN:** No department restrictions (null = no filter)

**Cycle prevention:** `DepartmentService` validates no cycles on create/update.

### 4.3 Access Scope Semantics

| Scope | Semantics | Who Can Set |
|-------|-----------|-------------|
| `ALL` | Visible to all authenticated users | ADMIN only |
| `DEPARTMENT` (default) | Visible to the owning department and its ancestor departments | ADMIN, MANAGER (forced to DEPARTMENT) |

**Enforcement:**
- Only ADMIN may set `accessScope = ALL`
- MANAGER uploads are forced to `DEPARTMENT` scope
- EMPLOYEE cannot upload

### 4.4 Document Status Filtering (document management)

| Role | Document Status Filter |
|------|---------------------------|
| ADMIN | All statuses (PENDING, PROCESSING, READY, FAILED, ARCHIVED) |
| MANAGER / EMPLOYEE | READY only (others return 404) |

> **This table governs document management only** — preview, details, versions,
> download, status inspection, and other document-API operations. It does **not**
> grant LLM-processing eligibility. See §5.4, which is stricter and applies to every role.

---

## 5. RAG Retrieval Policy (LLM_CAN_PROCESS)

### 5.1 Core Principle

> **LLM_CAN_PROCESS ⊆ USER_CAN_VIEW**, and additionally **LLM_CAN_PROCESS ⇒ document status is READY**.

A document chunk may enter the LLM context only if the user is authorized for that document **AND** the document is `READY`.

### 5.2 LLM Processing Authorization (Phase 2 — implemented)

For Phase 2 the rule is:

> **LLM_CAN_PROCESS = USER_CAN_VIEW AND document.status = READY**

Both conditions must hold. The same visibility rules apply to RAG retrieval as to document API access, with the additional unconditional readiness requirement in §5.4. No independent processing-permission tier exists yet; that remains planned for Phase 12 (§8).

Enforcement is centralized in `DocumentAuthorizationService.isAccessibleForRag()`, which is applied by `RetrieverService` on **both** the global hybrid path and the document-scoped path. The method **fails closed**: a null document, a null user, or any non-`READY` document returns `false` rather than throwing.

### 5.3 Retrieval Filtering Rules (Unified)

Applied after the readiness gate in §5.4, i.e. to `READY` documents only:

| Condition | ADMIN | MANAGER | EMPLOYEE |
|-----------|-------|---------|----------|
| `accessScope = ALL` | ✅ | ✅ | ✅ |
| Document in user's own department | ✅ | ✅ | ✅ |
| Document in an ancestor department | ✅ | ✅ | ✅ |
| Document in a descendant department | ✅ (any) | ❌ | ❌ |
| Document in an unrelated department | ✅ (any) | ❌ | ❌ |
| Own upload | N/A | N/A | ✅ (if `DEPARTMENT` scope) |

**Notes on candidate generation:** the vector filter and the keyword query are deliberately *permissive* for recall (shared scope, department chain, own upload), and are followed by the authoritative `isAccessibleForRag()` check on every merged chunk. Candidate filtering is never the authorization boundary.

### 5.4 Document Readiness for RAG (Applies to ALL Roles)

> **A document may enter an LLM prompt only when its status is READY. This applies
> to every role, including `ADMIN`, and to every access scope, including
> `AccessScope = ALL`.**

| Role | READY required for RAG? |
|------|--------------------------|
| ADMIN | ✅ Yes |
| MANAGER | ✅ Yes |
| EMPLOYEE | ✅ Yes |

**Rationale.** A non-READY document is by definition not finished: `PENDING` and
`PROCESSING` content may be incomplete, `FAILED` content may be partially written,
and `ARCHIVED` content has been withdrawn. None of it is a trustworthy basis for a
generated answer.

**Two rules that are easy to confuse — keep them separate:**

| Concern | Governed by | ADMIN rule |
|---------|--------------|------------|
| **Document management** (can this user open/download/inspect this document?) | §4.1, §4.4 | Any status permitted |
| **LLM processing** (may this content enter the prompt?) | §5.2, §5.3, **§5.4** | READY required, no bypass |

`ADMIN` is therefore still able to preview, download, re-inspect and troubleshoot a
`FAILED` or `PROCESSING` document through the document API, while none of that
content can be retrieved into a chat prompt by anyone, administrators included.

**Enforcement points:**
- `DocumentAuthorizationService.isAccessibleForRag()` checks readiness **before** any
  role or access-scope decision, so no branch can bypass it.
- Applied on **both** retrieval paths: global hybrid retrieval and document-scoped
  retrieval. The document-scoped path additionally performs
  `checkDocumentViewAccess()` + `requireReadable()` before retrieval, but the final
  `isAccessibleForRag()` check remains authoritative for RAG.
- `requireReadable()` deliberately still exempts `ADMIN`, because it backs document
  management. It must not be treated as the RAG readiness gate.
- In global retrieval, authorization is applied **before** the final `topK` cut, so
  permissive candidates cannot displace authorized chunks from the result set.

---

## 6. Document Upload Policy

| Role | Can Upload | Allowed Departments | Allowed accessScope |
|------|------------|---------------------|---------------------|
| ADMIN | ✅ | Any | ALL, DEPARTMENT |
| MANAGER | ✅ | Own subtree (self + descendants) | DEPARTMENT only |
| EMPLOYEE | ❌ | N/A | N/A |

---

## 7. Enforcement Points

| Layer | Component | Responsibility |
|-------|-----------|----------------|
| HTTP | `SecurityConfig` | JWT validation, route-level RBAC |
| Controller | `@PreAuthorize` | Role-based endpoint access |
| Service (Doc) | `DocumentAuthorizationService` | `checkDocumentViewAccess()`, `requireReadable()`, `getVisibleDepartmentIds()`, `getUploadableDepartmentIds()` |
| Service (RAG) | `DocumentAuthorizationService` → `RetrieverService` | `isAccessibleForRag()` (authoritative), `buildVectorFilterExpression()` (candidate recall only) |
| Ingestion | `DocumentAuthorizationService.validateUploadTarget()` | Upload target validation |
| Test isolation | `TestDatabaseSafetyGuard` | Prevents the test suite from targeting a production database |

---

## 8. Planned Future Processing Boundary (Phase 12)

> **The additional, independent processing-permission boundary described in this
> section is planned and is not implemented.** The Phase 2 baseline in §5.2 and §5.4
> — authorization plus `READY` status — **is** implemented and enforced today. The rows
> marked ✅ CURRENT below describe that implemented baseline; the processing-permission
> tier is the planned enhancement, and is distinct from the current rule.

| Decision | Question | Enforced By | Status |
|----------|----------|-------------|--------|
| User view access | May this user open/download this document? | `DocumentAuthorizationService` | ✅ CURRENT |
| LLM eligibility (Phase 2 rule) | May this content enter the prompt under the current rule? | `DocumentAuthorizationService.isAccessibleForRag()` | ✅ CURRENT |
| Independent processing permission | May this content be processed regardless of view access? | *new component* | 🧭 PLANNED (Phase 12) |

**Future requirements (Phase 12):**
1. Must be a **backend security boundary**, not prompt wording.
2. Must be evaluated **per retrieved chunk**, on both the semantic and keyword paths, and for document-scoped retrieval.
3. Must be auditable — decisions recorded in `activity_logs`.
4. Must be covered by security regression tests (referred to as `SEC-09` in the local-only `docs/RAG_EVALUATION.md`).
5. **Fail closed**: on error, deny. It must never default to allow.
6. Enforced during ingestion as well, so content that must not be processed is excluded from embedding in the first place where that is possible.

---

## 9. Error Handling

- Unauthorized access → 404 (not 403) to avoid document existence disclosure
- Invalid access scope → 400 with clear message
- Missing department → 404 (not 403)
- Failed authorization → no stack traces, no sensitive data in response

---

## 10. Unresolved Ambiguities

These are **open product decisions**, deliberately preserved. The "Currently" column
records implemented behaviour; it is not a recommendation.

| # | Ambiguity | Currently implemented | Decision Needed |
|---|-----------|------------------------|-----------------|
| 1 | GUEST role breadth | **Resolved:** `GUEST` is implemented as public/shared knowledge only — `AccessScope.ALL` + `READY`; no upload, no administration, no department-scoped access (§3.1) | Should `GUEST` ever gain department-scoped read access, or upload? |
| 2 | LLM_PROCESSING separate tier | Not implemented; Phase 2 rule is authorized + READY (§5.2) | Should a third access-scope value such as `LLM_ONLY` exist? |
| 3 | MANAGER descendant visibility | **Not** implemented: MANAGER sees own department + ancestors only (§4.1) | Should MANAGER also see descendant departments? |
| 4 | EMPLOYEE own-upload outside department | **Implemented in both** the document API and RAG: own uploads are reachable from any department (§4.1) | Should EMPLOYEE see their own uploads even in other departments? |

**Resolved (previously contradictory):**

| Question | Decision |
|----------|----------|
| Does `ADMIN`'s all-status document privilege extend to RAG? | **No.** `ADMIN` may retrieve content into a prompt only when the document is READY (§5.4). `ADMIN` retains all-status access for document management (§4.4). |
| Does `AccessScope = ALL` bypass readiness? | **No.** Shared visibility never bypasses the readiness gate (§5.4). |

---

## 11. Related Documents

This repository intentionally keeps most project documentation **local-only**
(`.gitignore` excludes `docs/*` except screenshots and this policy). Only the
documents below are available on GitHub; the remainder are referenced for local
readers and will not resolve as links for anyone cloning the repository.

### Tracked in GitHub

| Document | Relevance |
|----------|-----------|
| `README.md` | Security architecture summary and the readiness invariant |
| `CHANGELOG.md` | Record of the Phase 2 authorization changes |
| `docs/06_BASELINE_AND_DIAGNOSTICS.md` | Phase 1 baseline findings this policy resolves |
| `docs/07_ACCESS_CONTROL_POLICY.md` | This document |

### Local-only (not tracked in GitHub)

| Document | Relevance |
|----------|-----------|
| `docs/SECURITY.md` | Phases 2 and 12 |
| `docs/RAG_EVALUATION.md` | Phase 1 methodology, gates, `SEC-09` |
| `docs/RAG_ARCHITECTURE.md` | Current RAG pipeline vs. planned improvements |
| `docs/ARCHITECTURE.md` | Implementation gaps |
| `docs/INGESTION_PIPELINE.md` | Phase 5A |
| `docs/14_DEVELOPMENT_ROADMAP.md` | Phase definitions |
| `docs/vision/PROJECT_SPEC.md` | Source of the `USER ACCESS != LLM PROCESSING ACCESS` principle |

---

## 12. Approval

This policy is derived from the implemented behavior in `DocumentAuthorizationService` and `RetrieverService`, from documented requirements in the local-only `docs/14_DEVELOPMENT_ROADMAP.md`, and from the security principle `USER ACCESS != LLM PROCESSING ACCESS` established in the local-only `docs/vision/PROJECT_SPEC.md`.

**Status:** Reflects implemented Phase 2 behavior. Open product decisions are retained in §10.

---

**End of Document**