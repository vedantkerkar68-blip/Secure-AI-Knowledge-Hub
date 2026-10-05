# SAKH Baseline & Diagnostics

**Project:** Secure AI Knowledge Hub (SAKH)  
**Phase:** 1 — Baseline, Diagnostics & Regression Protection  
**Branch:** `develop`  
**Date:** Phase 1 kickoff  

---

## 1. Current Architecture ✅ IMPLEMENTED

```
Presentation Layer (React SPA, JavaScript/JSX)
         │  HTTPS + JSON + JWT Bearer
         ▼
Business Layer (Spring Boot 3.5.x, Java 21)
         ▼
Data Access Layer (Spring Data JPA + Spring AI PgVectorStore)
         ▼
PostgreSQL 17 + pgvector (Neon)
```

**External AI:** Google Gemini (chat + embeddings) via `com.sakh.llm.LLMService`

**Key Packages:**
- `com.sakh.controller` — REST endpoints (9 controllers)
- `com.sakh.service` — Business logic (12 services)
- `com.sakh.rag` — RAG pipeline (7 components)
- `com.sakh.processing` — Document ingestion (parsers, chunking, metadata)
- `com.sakh.security` — JWT, RBAC, prompt security, rate limiting
- `com.sakh.processing.parser` — PDF/DOCX/TXT parsers

---

## 2. Document Ingestion Flow ✅ IMPLEMENTED (except DOCX)

```
POST /api/documents (multipart: file, departmentId, accessScope)
    │
    ▼
DocumentController.uploadDocument()
    │
    ▼
DocumentService.uploadDocument()
    ├── Validate extension allow-list (pdf, docx, md, txt), size (10MB), content/type match
    ├── Resolve access scope (ALL / DEPARTMENT)
    ├── validateUploadTarget(): ADMIN any dept + either scope; MANAGER own subtree + DEPARTMENT only
    ├── StorageService.store() → storage path
    ├── Versioning: reuse group_id, increment version, mark latest
    ├── Save Document (status PENDING, accessScope)
    ├── Log UPLOAD activity
    └── Trigger @Async processDocument(id)
          │
          ▼
DocumentProcessingService.processDocument()  [@Async @Transactional]
  ├── status → PROCESSING
  ├── extractText(): resolve parser via parser.supports(fileType)  ⚠️ GAP for DOCX
  ├── MetadataExtractionService.extractAndSave()
  ├── ChunkService.chunkDocument()
  │     ├── TARGET_TOKENS = 700, OVERLAP_TOKENS = 100
  │     └── sentence accumulation with overlap carry-over
  ├── Build Spring AI Documents with metadata
  │     (documentId, chunkId, departmentId, chunkIndex,
  │      sectionTitle, pageNumber, uploadedBy)
  ├── vectorStore.add()  → embeddings + pgvector insert
  └── status → READY,  or FAILED on exception
```

---

## 3. PDF Processing ✅ IMPLEMENTED

| Aspect | Details |
|--------|---------|
| **Parser** | `PdfDocumentParser` (Apache PDFBox 3.0.4) |
| **MIME handling** | `supports("application/pdf")` **and** `supports("pdf")` ✅ |
| **Text extraction** | `PDFTextStripper.getText()` — extracts all text |
| **Page handling** | No explicit page boundary preservation in chunks |
| **Metadata extraction** | `MetadataExtractionService` — author, language, summary, tags, page_count, version |
| **Chunking** | Sentence-aware, TARGET_TOKENS=700, OVERLAP_TOKENS=100 |
| **Chunk size** | ~700 tokens target, overlap 100 tokens |
| **Embedding model** | Google Gemini `text-embedding-004` |
| **Embedding dimensions** | 3072 |
| **Vector storage** | `vector_store` table (pgvector), 3072-dim, cosine |
| **Status transition** | PENDING → PROCESSING → READY (or FAILED) |

---

## 4. DOCX Processing ❌ KNOWN BUG

| Format | Upload Validation | Processing Result |
|--------|-------------------|-------------------|
| PDF | ✅ | READY |
| DOCX | ✅ accepted | ❌ FAILED |
| TXT | ✅ accepted | ❌ FAILED |
| MD | ✅ accepted | ❌ FAILED |

### 4.1 DOCX Failure Root Cause ⚠️ KNOWN BUG

**Exact failure location:** `DocumentProcessingService.extractText()` line 102-105

```java
DocumentParser parser = parsers.stream()
        .filter(p -> p.supports(fileType))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No parser found for file type: " + fileType));
```

**Exception thrown:** `IllegalStateException: No parser found for file type: docx`

**Why it fails:**
| Parser | Accepted `supports()` values | Matches stored `file_type` ("docx")? |
|--------|------------------------------|--------------------------------------|
| `PdfDocumentParser` | `application/pdf`, **`pdf`** | ✅ Yes (accepts extension) |
| `DocxDocumentParser` | `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `application/msword` | ❌ No (MIME only) |
| `TextDocumentParser` | `text/plain`, `text/markdown`, `text/x-markdown` | ❌ No (MIME only) |

**Root cause:** `DocumentService.getFileType()` returns the **file extension** (`docx`, `pdf`, `txt`, `md`). Only `PdfDocumentParser.supports()` accepts the bare extension; `DocxDocumentParser` and `TextDocumentParser` only declare MIME types.

**Failure chain:**
```
upload employee.docx
  → upload validation passes (extension in allow-list)
  → documents.file_type = "docx"
  → status PENDING
  → @Async processDocument(id)
      → status PROCESSING
      → extractText(document)
          → parsers.stream().filter(p -> p.supports("docx")).findFirst()
          → no match  →  IllegalStateException
      → catch (Exception e)
          → status FAILED
          → log.error(...)  (stack trace only; no error surfaced to admin)
```

**Aggravating factors:**
1. **Silent to user** — Only `FAILED` chip shown; no error code/message exposed to admin
2. **Same bug for TXT/MD** — Same parser resolution issue affects TXT and MD

---

## 5. Document Status Lifecycle ✅ IMPLEMENTED

| Status | Enum Value | Transition |
|--------|------------|------------|
| `PENDING` | Initial state after upload | Initial |
| `PROCESSING` | Async processing started | PENDING → PROCESSING |
| `READY` | Processing succeeded | PROCESSING → READY |
| `FAILED` | Processing failed (exception caught) | PROCESSING → FAILED |
| `ARCHIVED` | Not used in code | — |

**Enum:** `DocumentStatus` (PENDING, PROCESSING, READY, FAILED, ARCHIVED)

**Transitions:**
- `uploadDocument()` → saves `PENDING`, triggers `@Async processDocument()`
- `processDocument()` → sets `PROCESSING`, on success → `READY`, on exception → `FAILED`
- `reprocessDocument()` → resets to `PENDING`, re-triggers processing
- `updateDocumentStatus()` (ADMIN only) → manual override

---

## 6. Chunking ✅ IMPLEMENTED

| Parameter | Value |
|-----------|-------|
| **Algorithm** | Sentence-aware accumulation with overlap carry-over |
| **Target chunk size** | 700 tokens (TARGET_TOKENS) |
| **Overlap** | 100 tokens (OVERLAP_TOKENS) |
| **Basis** | Token estimation (1 token ≈ 4 chars) — **character-based heuristic** |
| **Separators** | Sentence split: `(?<=[.!?])\s+` |
| **Paragraph handling** | Not preserved — sentences split globally |
| **Heading handling** | Not preserved |
| **Page boundaries** | Not preserved — chunks span pages |
| **Metadata on chunks** | documentId, chunkId, departmentId, chunkIndex, sectionTitle, pageNumber, uploadedBy |
| **Duplicate chunks** | Deleted on reprocessing (`chunkRepository.deleteByDocumentId`) |

**Implementation:** `ChunkService.chunkDocument()` — sentence splitting via regex `(?<=[.!?])\s+`, then merge with overlap carry-over.

**Token estimation:** Rough heuristic `(text.length() + 3) / 4` (1 token ≈ 4 chars) — **not a real tokenizer**.

---

## 7. Embeddings ✅ IMPLEMENTED

| Aspect | Detail |
|--------|--------|
| **Provider** | Google Gemini |
| **Model** | `text-embedding-004` |
| **Dimensions** | 3072 |
| **API calls** | Batched via `vectorStore.add(List<Document>)` |
| **Batch behavior** | Spring AI `PgVectorStore.add()` batches internally |
| **Retry behavior** | Not explicitly implemented (relies on Spring AI) |
| **Failure handling** | Exception caught → document status FAILED |
| **Rate-limit handling** | Not implemented |
| **Storage format** | `vector_store` table: `UUID id`, `TEXT content`, `JSONB metadata`, `VECTOR(3072) embedding` |
| **Vector DB column** | `embedding VECTOR(3072)` |
| **Distance metric** | Cosine (default for Spring AI PgVectorStore) |

**Per-chunk embedding:** Each chunk → one embedding vector stored in `vector_store` with metadata.

**Workload estimation (rough):**
| Document size | Chunks (est.) | Embedding calls |
|---------------|---------------|-----------------|
| 10 pages (~3000 tokens) | ~5 | 1 batch |
| 100 pages (~30k tokens) | ~43 | 1 batch |
| 1000 pages (~300k tokens) | ~429 | 1 batch |

Spring AI batches all chunks in one `vectorStore.add()` call.

---

## 8. Retrieval ✅ IMPLEMENTED (Hybrid)

**Method:** Hybrid — weighted fusion of semantic + keyword search

| Component | Implementation |
|-----------|----------------|
| **Vector search** | `vectorStore.similaritySearch()` with pgvector cosine |
| **Keyword search** | `chunkRepository.findKeywordSearchGlobal()` (PostgreSQL full-text) |
| **Fusion** | Weighted score merge: `0.7 × semantic + 0.3 × normalized keyword` |
| **Query rewriting** | `QueryRewriter` → Gemini reformulation |
| **Multi-query** | `MultiQueryRetriever` exists (generates variants) |
| **Access filtering** | Filter expression in vector search + post-filter on keyword results |
| **Top-K** | Default 5 (configurable via `TOP_K = 5` in ChatService) |
| **RRF** | ❌ NOT IMPLEMENTED |
| **Reranker** | ❌ NOT IMPLEMENTED |
| **Metadata filtering** | pgvector filterExpression on `departmentId`, `documentId`, `uploadedBy` |
| **Authorization filtering** | Applied in both semantic and keyword paths |

**Retrieval methods in `RetrieverService`:**
- `retrieve(question, departmentId, topK)` — semantic only
- `retrieve(question, user, topK)` — hybrid (semantic + keyword)
- `retrieve(question, user, topK, documentId)` — scoped to one document

**Access filtering in retrieval (`isAccessible`):**
- ADMIN: all
- Same department: allow
- EMPLOYEE own upload: allow
- Else: filtered out

---

## 9. RAG Context Construction ✅ IMPLEMENTED

| Parameter | Value |
|-----------|-------|
| **System prompt** | "You are Secure AI Knowledge Hub. Only answer from supplied context. Never hallucinate. If information is unavailable say 'I don't have enough information.'" |
| **Context cap** | 12,000 **CHARACTERS** (not tokens) ⚠️ |
| **Context ordering** | By retrieval rank (top-K) |
| **Max chunks** | Top-K (default 5) |
| **Duplicate removal** | By `chunkId` during merge |
| **Citation format** | Document N / Title / Department / Page / Chunk / Text |
| **Conversation history** | Last 10 messages + session summary (every 20 messages) |
| **Token counting** | ❌ NOT IMPLEMENTED — character cap only |
| **Truncation** | Hard character limit, no smart truncation |

**Context cap:** `MAX_CONTEXT_LENGTH = 12000` characters in `PromptBuilder.buildContext()` — **not token-aware**.

---

## 10. Authorization Boundary ⚠️ GAP

**Two independent access enforcement paths — not equivalent:**

| Rule | `DocumentService` (API) | `RetrieverService` (RAG) |
|------|-------------------------|--------------------------|
| `ADMIN` | All documents | All documents |
| `accessScope = ALL` | Visible | **Not honoured** ❌ |
| Own department | Visible | Visible |
| Ancestor departments | Visible (ancestor chain) | **Not visible** (exact match only) |
| Sub-departments | Not visible (ancestors only) | Not visible |
| Own uploads (EMPLOYEE) | N/A | Visible |
| Non-READY documents | Hidden (404) | Status not checked on all paths |

**Consequence:** Answers can cite documents the document API would refuse. Primary security work item of Phase 2.

---

## 11. LLM Processing ✅ IMPLEMENTED

| Aspect | Detail |
|--------|--------|
| **Provider** | Google Gemini |
| **Model** | `gemini-2.5-flash` (configured via Spring AI) |
| **Calls per query** | 1 (generation) + 1 (query rewrite) + 1 (summarization every 20 msgs) |
| **Streaming** | Supported via `StreamingChatModel` |
| **Prompt construction** | System + Context + History + Question |
| **Answer verification** | `AnswerVerifier` — sentence-level grounding check (≥30% word overlap) |
| **Citation building** | `CitationService` from retrieved chunks |
| **Token counting** | ❌ NOT IMPLEMENTED |

---

## 12. Chat Persistence ✅ IMPLEMENTED

| Aspect | Detail |
|--------|--------|
| **Sessions persisted** | ✅ `chat_sessions` table |
| **Messages persisted** | ✅ `chat_messages` table (role, message, citations JSONB, confidence) |
| **History survives reload** | ✅ |
| **History survives logout** | ✅ (tied to user_id) |
| **Session restoration** | Frontend loads sessions on load; detail loads messages |
| **Summarization** | Every 20 messages via `ConversationSummarizer` |
| **Session title generation** | First user message → `TitleGenerator` (Gemini) |
| **Message roles** | `user` / `assistant` |
| **Citations stored** | JSONB in `chat_messages.citations` |

---

## 12. Database / pgvector ✅ IMPLEMENTED

| Table | Key Columns |
|-------|-------------|
| `documents` | id, title, file_type, status, access_scope, group_id, version, is_latest |
| `chunks` | document_id, chunk_index, chunk_text, token_count, page_number, section_title |
| `vector_store` | UUID id, TEXT content, JSONB metadata, VECTOR(3072) embedding |
| `chat_sessions` | user_id, title, summary, updated_at |
| `chat_messages` | session_id, message_role, message, citations (JSONB), confidence |
| `activity_logs` | user_id, action, resource, ip_address |
| `jwt_revocations` | token_jti, user_id, reason, revoked_at |

**Vector config:**
- Dimensions: 3072 (matches `text-embedding-004`)
- Distance: Cosine (Spring AI default)
- **Index:** ❌ **NO ANN INDEX** (exact search) — Phase 8 item

**Migrations:** V1–V10 (Flyway), `ddl-auto=validate`

---

## 13. Token Usage ⚠️ GAP

| Capability | Status |
|------------|--------|
| Embedding token tracking | ❌ Not measured |
| Prompt token counting | ❌ Not implemented |
| Context token budgeting | ❌ Character cap only (12,000 chars) |
| Output token tracking | ❌ Not implemented |
| Token budgets | ❌ Not implemented |
| Truncation strategy | Hard character cap |
| Compression | ❌ Not implemented |
| Deduplication | ❌ Not implemented |

**Current context limit:** 12,000 **characters** (not tokens) in `PromptBuilder.MAX_CONTEXT_LENGTH`

---

## 14. Performance ⚠️ GAP

| Metric | Status |
|--------|--------|
| Upload latency | Not measured |
| Parsing latency | Not measured |
| Chunking latency | Not measured |
| Embedding latency | Not measured |
| Vector insertion latency | Not measured |
| Query embedding latency | Not measured |
| Retrieval latency | Logged in `RetrieverService` (ms) |
| Reranking latency | N/A (not implemented) |
| LLM latency | Not measured |
| Total response latency | Not measured end-to-end |

**Existing instrumentation:** `MetricsCollector` (in-memory), logs retrieval time in `RetrieverService`.

---

## 15. Current RAG Capabilities

| Capability | Status |
|------------|--------|
| PDF ingestion | ✅ |
| DOCX/DOC/TXT/MD/CSV/XLSX/PPTX ingestion | ❌ FAILED (parser resolution) |
| Semantic search | ✅ |
| Keyword search | ✅ (PostgreSQL full-text) |
| Hybrid fusion | ✅ (weighted 0.7/0.3) |
| Query rewriting | ✅ (Gemini) |
| Multi-query expansion | ✅ Component exists |
| Answer verification | ✅ (grounding check) |
| Conversation summarization | ✅ (every 20 messages) |
| Session title generation | ✅ |
| Citations | ✅ |
| Multi-hop retrieval | ❌ |
| RRF | ❌ |
| Reranking | ❌ |
| Contextual compression | ❌ |
| Token budgets | ❌ |
| Hierarchical retrieval | ❌ |
| Semantic caching | ❌ |
| ANN index | ❌ |

---

## 16. Current Limitations

| # | Limitation | Impact |
|---|------------|--------|
| 1 | DOCX/TXT/MD ingestion fails | Cannot process non-PDF formats |
| 2 | Access control divergence | RAG may cite docs API would deny |
| 3 | Character-based context cap | Inefficient token usage, no budget |
| 4 | No token measurement | Cannot optimize cost |
| 5 | No evaluation dataset | Cannot measure retrieval quality |
| 6 | No ANN index | Slow retrieval at scale |
| 7 | No RRF/reranking | Suboptimal ranking |
| 8 | No contextual compression | Wasted tokens |
| 9 | No token counting | Cannot enforce budgets |
| 10 | No evaluation framework | Cannot regress-test changes |

---

## 17. Security Findings

| Finding | Severity |
|---------|----------|
| Access control divergence (API vs RAG) | HIGH — answers may cite unauthorized docs |
| No LLM processing access tier | HIGH — sensitive content may reach LLM |
| Prompt injection defense = regex block-list only | MEDIUM — bypassable |
| No token budget enforcement | MEDIUM — cost/unbounded context |
| No ANN index | LOW — performance at scale |
| GUEST role unprovisioned | LOW — seeded but no code path |

---

## 18. Recommended Phase 2 Work

| Priority | Work Item |
|----------|-----------|
| 1 | Fix DOCX/TXT/MD parser resolution (type contract) |
| 2 | Unify DocumentService + RetrieverService access control |
| 3. | Add LLM-processing access tier (Phase 2 goal) |
| 4 | Add token counting + budget to PromptBuilder |
| 5 | Build evaluation dataset + baseline metrics (Phase 1) |
| 6 | Add RRF + reranking (Phase 3) |
| 7 | Add token budget + compression (Phase 4) |
| 8 | Add ANN index (Phase 8) |
| 9 | Add error codes + admin visibility for processing failures |
| 10 | Fix TXT/MD parser resolution (same root cause as DOCX) |

---

## 19. Files Inspected (Key)

| File | Purpose |
|------|---------|
| `DocumentController.java` | Upload endpoint |
| `DocumentService.java` | Upload validation, processing trigger |
| `DocumentProcessingService.java` | Async processing orchestration |
| `DocumentParser` + 3 implementations | Parser interface + PDF/DOCX/TXT |
| `DocumentProcessingService.extractText()` | **Failure point for DOCX** |
| `ChunkService.java` | Sentence-aware chunking |
| `RetrieverService.java` | Hybrid retrieval + access filtering |
| `PromptBuilder.java` | Context assembly (12k char cap) |
| `ChatService.java` | Chat orchestration, RAG orchestration |
| `PromptBuilder.java` | Context assembly (12k char cap) |
| `LLMService.java` | Gemini chat/streaming |
| `Document.java` entity | `fileType` stores extension |
| `DocumentStatus` enum | PENDING/PROCESSING/READY/FAILED/ARCHIVED |
| `V1`–`V10` migrations | Schema evolution |
| `ChunkService.java` | Sentence-aware chunking (700/100 tokens) |

---

**End of Baseline Document**

---

## 20. Status Summary

| Area | Status |
|------|--------|
| PDF ingestion | ✅ WORKING |
| DOCX/TXT/MD ingestion | ❌ BROKEN (parser resolution) |
| Hybrid retrieval | ✅ WORKING |
| Access control | ⚠️ DIVERGENT |
| Token management | ❌ MISSING |
| Evaluation framework | ❌ MISSING |
| Chat persistence | ✅ WORKING |
| Vector storage | ✅ WORKING (no ANN index) |
| Chat session persistence | ✅ WORKING |

**Next step:** Phase 1 baseline complete. Ready for Phase 2 (Access-Control Consistency) or Phase 5A (DOCX fix).