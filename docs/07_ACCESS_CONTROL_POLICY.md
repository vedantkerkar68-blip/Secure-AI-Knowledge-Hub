# Access Control Policy

**Project:** Secure AI Knowledge Hub (SAKH)  
**Version:** 1.0  
**Status:** Living document — reflects the agreed authorization policy  
**Phase:** 2 — Access-Control Consistency  

---

## 1. Purpose

This document defines the authoritative authorization policy for document access and LLM processing in SAKH. It resolves the divergences identified in Phase 1 between `DocumentService` (document API) and `RetrieverService` (RAG retrieval).

---

## 2. Core Principles

### 2.1 Separation of Concerns

| Decision | Question | Enforced By | Status |
|----------|----------|-------------|--------|
| **USER_CAN_VIEW** | May this user open/download this document? | `DocumentService` | ✅ CURRENT |
| **LLM_CAN_PROCESS** | May this content enter the LLM context? | `RetrieverService` (Phase 2) | 🧭 PLANNED |

**Rule:** `USER_CAN_VIEW` does **not** automatically imply `LLM_CAN_PROCESS`. A document may be viewable by a user but still forbidden from LLM processing (e.g., sensitive HR data that a manager can view but must not be sent to an external LLM).

---

## 3. Roles and Their Permissions

| Role | Description |
|------|-------------|
| `ADMIN` | Full system access — all documents, all users, departments, activity logs |
| `MANAGER` | Department-scoped access — documents in their department and sub-departments |
| `EMPLOYEE` | Document upload and chat — department documents + own uploads |
| `GUEST` | Seeded role; **no enforcement branch exists** — treat as unprovisioned |

> ⚠️ `GUEST` is seeded in the database (`V1__initial_schema.sql`) and documented in early specs, but the current authorization code has no `GUEST` branch in `RetrieverService` or `DocumentService`. Treat `GUEST` as unprovisioned until explicitly implemented.

---

## 4. Document Access Policy (USER_CAN_VIEW)

### 4.1 Visibility Rules (Document API)

A user may view/download a document if **any** of the following is true:

| Condition | ADMIN | MANAGER | EMPLOYEE |
|-----------|-------|---------|----------|
| `accessScope = ALL` | ✅ | ✅ | ✅ |
| Document in user's department | ✅ | ✅ | ✅ |
| Document in ancestor department | ✅ | ✅ | ✅ |
| Document in descendant department | ❌ | ✅ (subtree) | ❌ |
| Document in unrelated department | ❌ | ❌ | ❌ |
| Own upload (EMPLOYEE) | N/A | N/A | ✅ (only if `accessScope = DEPARTMENT`) |
| Document status = READY | ✅ | ✅ | ✅ |
| Document status ≠ READY | ✅ | ❌ (404) | ❌ (404) |

### 4.2 Department Hierarchy Semantics

- **Ancestor chain:** User's department → parent → grandparent → ... → root
- **Descendant subtree:** User's department → all children → grandchildren → ...
- **Manager scope:** Own department + entire descendant subtree
- **Employee scope:** Own department + ancestor chain only
- **ADMIN:** No department restrictions (null = no filter)

### 4.3 Access Scope Semantics

| Scope | Semantics | Who Can Set |
|-------|-----------|-------------|
| `ALL` | Visible to all authenticated users | ADMIN only |
| `DEPARTMENT` (default) | Visible to the owning department and its sub-departments | ADMIN, MANAGER (forced to DEPARTMENT) |

**Rules:**
- Only ADMIN may set `accessScope = ALL`
- MANAGER uploads are forced to `DEPARTMENT` scope
- EMPLOYEE cannot upload

### 4.3 Document Status Filtering

| Role | Document Status Filter (document management) |
|------|---------------------------|
| ADMIN | All statuses (PENDING, PROCESSING, READY, FAILED, ARCHIVED) |
| MANAGER / EMPLOYEE | READY only (others return 404) |

> **This table governs document management only** — preview, details, versions,
> download, status inspection, and other document-API operations. It does **not**
> grant LLM-processing eligibility. See §5.4 for that, which is stricter and applies
> to every role.

---

## 5. RAG Retrieval Policy (LLM_CAN_PROCESS)

### 5.1 Core Principle

> **LLM_CAN_PROCESS ⊆ USER_CAN_VIEW**

A document chunk may enter the LLM context **only if** the user has `USER_CAN_VIEW` for that document **AND** the document is approved for LLM processing.

### 5.2 LLM Processing Authorization (Phase 2 — Minimum)

For Phase 2, we enforce: **LLM_CAN_PROCESS = USER_CAN_VIEW**

This means: the same visibility rules apply to RAG retrieval as to document API access. No separate LLM-processing tier yet (that's Phase 12).

### 5.3 Retrieval Filtering Rules (Unified)

| Condition | ADMIN | MANAGER | EMPLOYEE |
|-----------|-------|---------|----------|
| `accessScope = ALL` | ✅ | ✅ | ✅ |
| Document in user's department | ✅ | ✅ | ✅ |
| Document in ancestor department | ✅ | ✅ | ✅ |
| Document in descendant department | ❌ | ✅ (subtree) | ❌ |
| Document in unrelated department | ❌ | ❌ | ❌ |
| Own upload (EMPLOYEE) | N/A | N/A | ✅ (if DEPT scope) |
| Document status = READY | ✅ | ✅ | ✅ |

**Key changes from current RetrieverService:**
1. `accessScope = ALL` is now honored in retrieval
2. Ancestor departments are now visible (was exact match only)
3. EMPLOYEE own-upload visibility retained (but only if document is in allowed department tree or accessScope=ALL)

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
| **Document management** (can this user open/download/inspect this document?) | §4.1, §4.3 | Any status permitted |
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

---

## 6. Document Upload Policy

| Role | Can Upload | Allowed Departments | Allowed accessScope |
|------|------------|---------------------|---------------------|
| ADMIN | ✅ | Any | ALL, DEPARTMENT |
| MANAGER | ✅ | Own subtree (self + descendants) | DEPARTMENT only |
| EMPLOYEE | ❌ | N/A | N/A |

---

## 7. Department Hierarchy Semantics

| Concept | Definition |
|---------|------------|
| **Ancestor chain** | Department → parent → parent's parent ... up to root |
| **Descendant subtree** | Department + all children + grandchildren + ... |
| **Manager scope** | Own department + entire descendant subtree |
| **Employee scope** | Own department + ancestor chain only |
| **ADMIN** | No restrictions (sees all) |

**Cycle prevention:** `DepartmentService` validates no cycles on create/update.

---

## 7. Access Scope Semantics (Detailed)

| Scope | Semantics | Who Can Set |
|-------|-----------|-------------|
| `ALL` | Visible to all authenticated users | ADMIN only |
| `DEPARTMENT` (default) | Visible to the owning department and its sub-departments | ADMIN, MANAGER (forced to DEPARTMENT) |

**Enforcement:**
- Only ADMIN may set `accessScope = ALL`
- MANAGER uploads are forced to `DEPARTMENT` scope
- EMPLOYEE cannot upload

---

## 8. LLM Processing Boundary (Future — Phase 12)

> **This section documents the planned boundary. Not implemented in Phase 2.**

| Decision | Question | Enforced By | Status |
|----------|----------|-------------|--------|
| User view access | May this user open/download this document? | `DocumentService` | ✅ CURRENT |
| LLM processing access | May this content enter the LLM context? | *new component* | 🧭 PLANNED (Phase 12) |

**Future requirements (Phase 12):**
1. Must be a **backend security boundary**, not prompt wording.
2. Must be evaluated **per retrieved chunk**, on both the semantic and keyword paths, and for document-scoped retrieval.
3. Must be auditable — decisions recorded in `activity_logs`.
4. Must be covered by security regression tests (`SEC-09` in `docs/RAG_EVALUATION.md`).
5. **Fail closed**: on error, deny. It must never default to allow.
6. Enforced during ingestion as well, so content that must not be processed is excluded from embedding in the first place where that is possible.

---

## 8. Enforcement Points

| Layer | Component | Responsibility |
|-------|-----------|----------------|
| HTTP | `SecurityConfig` | JWT validation, route-level RBAC |
| Controller | `@PreAuthorize` | Role-based endpoint access |
| Service (Doc) | `DocumentAuthorizationService` | `checkDocumentViewAccess()`, `requireReadable()`, `resolveVisibleDepartmentIds()` |
| Service (RAG) | `RetrieverService` | `isAccessibleForRag()`, `buildVectorFilterExpression()` |
| Ingestion | `DocumentService.validateUploadTarget()` | Upload target validation |
| Ingestion (future) | *new component* | LLM-processing eligibility at ingestion |

---

## 9. Error Handling

- Unauthorized access → 404 (not 403) to avoid document existence disclosure
- Invalid access scope → 400 with clear message
- Missing department → 404 (not 403)
- Failed authorization → no stack traces, no sensitive data in response

---

## 10. Unresolved Ambiguities

| # | Ambiguity | Decision Needed |
|---|-----------|-----------------|
| 1 | GUEST role semantics | Is GUEST a supported role? If so, what are its permissions? |
| 2 | LLM_PROCESSING separate tier | Should `accessScope` have a third value `LLM_ONLY` or similar? |
| 3 | Manager ancestor visibility | Should MANAGER see ancestor departments? (Currently: no) |
| 4 | Employee own-upload outside dept | Should EMPLOYEE see their own uploads even in other departments? (Currently: yes in RAG, no in API) |

**Resolved (previously contradictory):**

| Question | Decision |
|----------|----------|
| Does `ADMIN`'s all-status document privilege extend to RAG? | **No.** `ADMIN` may retrieve content into a prompt only when the document is READY (§5.4). `ADMIN` retains all-status access for document management (§4.3). |

> **Decision:** For Phase 2, we implement the policy as documented above. Ambiguities 1, 2, 3, 4 are documented for future resolution. Current implementation follows the tables in Section 4.1 and 5.3, and the readiness rule in 5.4.

---

## 11. Related Documents

| Document | Relevance |
|----------|-----------|
| `docs/06_BASELINE_AND_DIAGNOSTICS.md` | Baseline findings |
| `docs/architecture/ARCHITECTURE.md` | Current implementation gaps |
| `docs/RAG_ARCHITECTURE.md` | Current RAG pipeline vs. planned improvements |
| `docs/RAG_EVALUATION.md` | Phase 1 methodology and gates |
| `docs/INGESTION_PIPELINE.md` | Phase 5A |
| `docs/SECURITY.md` | Phases 2 and 12 |
| `docs/14_DEVELOPMENT_ROADMAP.md` | Phases 2 and 12 |

---

## 12. Approval

This policy is derived from existing code behavior, documented requirements in `docs/14_DEVELOPMENT_ROADMAP.md`, and the security principle `USER ACCESS != LLM PROCESSING ACCESS` established in `docs/vision/PROJECT_SPEC.md`.

**Status:** Ready for implementation review.

---

**End of Document**