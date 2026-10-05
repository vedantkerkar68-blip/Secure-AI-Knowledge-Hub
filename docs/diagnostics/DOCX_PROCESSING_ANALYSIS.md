# DOCX Processing Analysis

**Project:** Secure AI Knowledge Hub (SAKH)  
**Status:** Root cause identified — not yet fixed  
**Phase:** 1 (Baseline) → 5A (fix planned)  

---

## 1. Current Upload Path for DOCX

```
POST /api/documents (multipart/form-data)
    ├── file: DOCX file
    ├── departmentId: Long (required)
    └── accessScope: String (optional, defaults to DEPARTMENT)
          │
          ▼
DocumentController.uploadDocument()
    │
    ▼
DocumentService.uploadDocument(MultipartFile file, Long departmentId, String accessScope)
    ├── validateFile(file) — extension allow-list: pdf, docx, md, txt ✅
    ├── department lookup ✅
    ├── resolveAccessScope() ✅
    ├── validateUploadTarget() ✅
    ├── storageService.store() → storage path ✅
    ├── Versioning: group_id, version, is_latest ✅
    ├── Save Document entity:
    │     fileType = getFileType(file) → "docx" (extension only)
    │     status = PENDING
    ├── Log UPLOAD activity ✅
    └── processingService.processDocument(id)  [@Async @Transactional]
          │
          ▼
DocumentProcessingService.processDocument()
    ├── status → PROCESSING ✅
    ├── extractText(document)  ← **FAILS HERE**
    ├── metadataService.extractAndSave() — not reached
    ├── chunkService.chunkDocument() — not reached
    ├── vectorStore.add() — not reached
    └── status → FAILED (exception caught)
```

---

## 2. Parser Resolution Mechanism

### 2.1 Parser Interface
```java
public interface DocumentParser {
    boolean supports(String contentType);
    String extractText(Path file);
}
```

### 2.2 Parser Resolution in `DocumentProcessingService.extractText()`
```java
private String extractText(com.sakh.entity.Document document) {
    Path filePath = Paths.get(document.getStoragePath());
    String fileType = document.getFileType();  // Returns "docx" (extension)

    DocumentParser parser = parsers.stream()
            .filter(p -> p.supports(fileType))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No parser found for file type: " + fileType));

    return parser.extractText(filePath);
}
```

### 2.3 Parser `supports()` Implementations

| Parser | `supports()` returns true for | Matches "docx"? |
|--------|------------------------------|-----------------|
| `PdfDocumentParser` | `"application/pdf"` **OR** `"pdf"` | N/A |
| `DocxDocumentParser` | `"application/vnd.openxmlformats-officedocument.wordprocessingml.document"` OR `"application/msword"` | ❌ **NO** |
| `TextDocumentParser` | `"text/plain"`, `"text/markdown"`, `"text/x-markdown"` | ❌ **NO** |

**Key finding:** Only `PdfDocumentParser` accepts the bare extension (`"pdf"`). The DOCX and Text parsers only declare MIME types.

---

## 3. Dependency Status

| Dependency | Artifact | Version | Status |
|------------|----------|---------|--------|
| Apache PDFBox | `pdfbox` | 3.0.4 | ✅ Present |
| Apache POI | `poi-ooxml` | 5.3.0 | ✅ Present |

**Conclusion:** The DOCX parsing library (Apache POI) **is present** and the parser class (`DocxDocumentParser`) **exists and compiles**. The failure is **not** a missing dependency.

---

## 4. Exact Failure Point

**File:** `DocumentProcessingService.java`  
**Method:** `extractText(Document)`  
**Lines:** 98–108  

```java
private String extractText(com.sakh.entity.Document document) {
    Path filePath = Paths.get(document.getStoragePath());
    String fileType = document.getFileType();  // Returns "docx"

    DocumentParser parser = parsers.stream()
            .filter(p -> p.supports(fileType))  // ← NO MATCH for "docx"
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No parser found for file type: " + fileType));

    return parser.extractText(filePath);
}
```

**Exception thrown:**
```
IllegalStateException: No parser found for file type: docx
```

**Caught at:** `DocumentProcessingService.processDocument()` line 91–95
```java
} catch (Exception e) {
    logger.error("Document processing failed for document ID: {}", documentId, e);
    document.setStatus(DocumentStatus.FAILED);
    documentRepository.save(document);
}
```

**Result:** Document status = `FAILED`, error only in logs, not exposed to admin UI.

---

## 5. Root Cause Summary

| Factor | Detail |
|--------|--------|
| **Primary cause** | Type contract mismatch: `fileType` stores extension (`"docx"`), but `DocxDocumentParser.supports()` only accepts MIME types |
| **Affected parsers** | `DocxDocumentParser`, `TextDocumentParser` (both MIME-only) |
| **Working parser** | `PdfDocumentParser` (accepts both `"application/pdf"` AND `"pdf"`) |
| **Impact** | DOCX, TXT, MD all fail with same root cause |
| **PDF works because** | `PdfDocumentParser.supports("pdf")` returns `true` |

---

## 6. Affected Components

| Component | Impact |
|-----------|--------|
| `DocumentProcessingService.extractText()` | Direct failure point |
| `DocumentProcessingService.processDocument()` | Catches exception → sets FAILED |
| `DocumentService.uploadDocument()` | Returns ACCEPTED, but async processing fails silently |
| Admin UI | Shows `FAILED` chip, no error details |
| DOCX, TXT, MD uploads | All fail with same root cause |

---

## 7. Recommended Fix

**Option 1 — Extend parser `supports()` to accept extensions (minimal change):**
```java
// In DocxDocumentParser
@Override
public boolean supports(String contentType) {
    return "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(contentType)
            || "application/msword".equalsIgnoreCase(contentType)
            || "docx".equalsIgnoreCase(contentType)          // ADD
            || "doc".equalsIgnoreCase(contentType);          // ADD for legacy
}

// In TextDocumentParser
@Override
public boolean supports(String contentType) {
    return "text/plain".equalsIgnoreCase(contentType)
            || "text/markdown".equalsIgnoreCase(contentType)
            || "text/x-markdown".equalsIgnoreCase(contentType)
            || "txt".equalsIgnoreCase(contentType)           // ADD
            || "md".equalsIgnoreCase(contentType)            // ADD
            || "markdown".equalsIgnoreCase(contentType);     // ADD
}
```

**Option 2 — Add MIME type detection (robust):**
- Use `Files.probeContentType()` or Apache Tika to detect actual MIME type from file content
- Map detected MIME to parser
- More robust but larger change

**Option 3 — Centralized type resolver:**
- Add `FileTypeResolver` service that maps extensions → canonical MIME types
- Parsers only declare MIME types; resolver handles extension mapping

---

## 8. Regression Tests Required

| Test Case | Expected |
|-----------|----------|
| Valid PDF upload | READY |
| Valid DOCX upload | READY (currently FAILED) |
| Valid TXT upload | READY (currently FAILED) |
| Valid MD upload | READY (currently FAILED) |
| Empty document | FAILED — `EMPTY_CONTENT` |
| Corrupted DOCX | FAILED — `CORRUPTED_FILE` |
| Unsupported extension (e.g., `.exe`) | Rejected at upload validation |
| Incorrect MIME type (DOCX with `.pdf` extension) | Rejected — content/type mismatch |
| Oversized file (>10MB) | Rejected at upload |
| DOCX with tables | Tables extracted and searchable |
| Large DOCX (100+ pages) | Processed incrementally, no OOM |

**Critical regression assertion:** The **PDF pipeline must not regress** — existing PDF fixtures must stay green.

---

## 9. Fix Must Preserve

- ✅ Working PDF path must not regress
- ✅ Authentication and login behaviour unchanged
- ✅ Parser output compatible with existing chunking pipeline
- ✅ Files continue to be processed asynchronously
- ✅ Authentication/login behaviour unchanged

---

## 10. Related Documents

| Document | Relevance |
|----------|-----------|
| `docs/architecture/ARCHITECTURE.md` §4.1 | Parser resolution gap documented |
| `docs/INGESTION_PIPELINE.md` §2 | Full root-cause analysis |
| `docs/06_BASELINE_AND_DIAGNOSTICS.md` §4 | Baseline diagnostic entry |
| `docs/14_DEVELOPMENT_ROADMAP.md` Phase 5A | Planned fix in roadmap |

---

**End of Analysis**

---

**Status:** Root cause identified and documented. Fix not yet implemented. Ready for Phase 5A implementation.