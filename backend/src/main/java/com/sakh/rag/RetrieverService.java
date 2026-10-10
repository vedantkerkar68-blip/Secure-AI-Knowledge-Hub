package com.sakh.rag;

import com.sakh.entity.User;
import com.sakh.enums.AccessScope;
import com.sakh.enums.DocumentStatus;
import com.sakh.exception.ResourceNotFoundException;
import com.sakh.repository.ChunkRepository;
import com.sakh.repository.DocumentRepository;
import com.sakh.repository.UserRepository;
import com.sakh.security.DocumentAuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RetrieverService {

    private static final Logger logger = LoggerFactory.getLogger(RetrieverService.class);

    private static final double SEMANTIC_WEIGHT = 0.7;
    private static final double KEYWORD_WEIGHT = 0.3;
    private static final int HYBRID_MULTIPLIER = 2;

    /**
     * Sentinel department id used to keep the keyword SQL IN-list non-empty for users
     * with no visible department. No real department uses this id.
     */
    private static final Long NO_DEPARTMENT_SENTINEL = -1L;

    private final VectorStore vectorStore;
    private final ChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final QueryRewriter queryRewriter;
    private final DocumentAuthorizationService authorizationService;

    public RetrieverService(VectorStore vectorStore, ChunkRepository chunkRepository,
                            DocumentRepository documentRepository, UserRepository userRepository,
                            QueryRewriter queryRewriter, DocumentAuthorizationService authorizationService) {
        this.vectorStore = vectorStore;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.queryRewriter = queryRewriter;
        this.authorizationService = authorizationService;
    }

    public List<Document> retrieve(String question, User user, int topK) {
        long start = System.currentTimeMillis();
        String rewritten = queryRewriter.rewrite(question);

        String role = user.getRole() != null ? user.getRole().getName() : "";
        Long departmentId = user.getDepartment() != null ? user.getDepartment().getId() : null;

        logger.info("Hybrid retrieval - question: '{}' (rewritten: '{}'), user: {}, role: {}, department: {}, topK: {}",
                question, rewritten, user.getEmail(), role, departmentId, topK);

        int fetchSize = topK * HYBRID_MULTIPLIER;

        List<Long> visibleDepartmentIds = "ADMIN".equals(role)
                ? List.of()
                : authorizationService.getVisibleDepartmentIds(user);

        List<Document> semanticResults = semanticSearch(rewritten, user, fetchSize);
        logger.debug("Semantic search returned {} results", semanticResults.size());

        List<Document> keywordResults = keywordSearch(rewritten, role, visibleDepartmentIds, user.getEmail(), fetchSize);
        logger.debug("Keyword search returned {} results", keywordResults.size());

        // Filter results through centralized authorization - optimize by checking per document, not per chunk
        List<Document> merged = mergeAndRank(semanticResults, keywordResults, topK);
        
        // Collect distinct document IDs to avoid repeated DB queries
        Set<Long> distinctDocIds = merged.stream()
                .map(doc -> {
                    Object docIdObj = doc.getMetadata().get("documentId");
                    return docIdObj instanceof Number num ? num.longValue() : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        // Fetch documents once and build authorization map
        Map<Long, Boolean> authCache = new HashMap<>();
        if (!distinctDocIds.isEmpty()) {
            List<com.sakh.entity.Document> documents = documentRepository.findAllById(distinctDocIds);
            for (com.sakh.entity.Document doc : documents) {
                authCache.put(doc.getId(), authorizationService.isAccessibleForRag(doc, user));
            }
        }
        // Missing documents fail closed
        for (Long docId : distinctDocIds) {
            authCache.putIfAbsent(docId, false);
        }
        
        List<Document> authorized = merged.stream()
                .filter(doc -> {
                    Object docIdObj = doc.getMetadata().get("documentId");
                    if (docIdObj instanceof Number num) {
                        return authCache.getOrDefault(num.longValue(), false);
                    }
                    return false;
                })
                .toList();

        long elapsed = System.currentTimeMillis() - start;
        logger.info("Hybrid retrieval returned {} authorized chunks in {}ms", authorized.size(), elapsed);

        return authorized;
    }

    /**
     * Retrieves chunks scoped to a single document while still enforcing the
     * requesting user's access rights. Access is verified at the document level
     * before any retrieval happens.
     */
    public List<Document> retrieve(String question, User user, int topK, Long documentId) {
        if (documentId == null) {
            return retrieve(question, user, topK);
        }

        verifyDocumentAccess(user, documentId);

        long start = System.currentTimeMillis();
        String rewritten = queryRewriter.rewrite(question);

        String role = user.getRole() != null ? user.getRole().getName() : "";
        Long departmentId = user.getDepartment() != null ? user.getDepartment().getId() : null;

        logger.info("Scoped retrieval - question: '{}' (rewritten: '{}'), user: {}, document: {}, topK: {}",
                question, rewritten, user.getEmail(), documentId, topK);

        int fetchSize = topK * HYBRID_MULTIPLIER;

        List<Document> semanticResults = semanticSearch(rewritten, user, fetchSize, documentId);
        logger.debug("Scoped semantic search returned {} results", semanticResults.size());

        List<Document> keywordResults = keywordSearchInDocument(rewritten, documentId, fetchSize);
        logger.debug("Scoped keyword search returned {} results", keywordResults.size());

        List<Document> merged = mergeAndRank(semanticResults, keywordResults, topK);

        // Apply final centralized authorization filter - optimized for single document
        // Since this is scoped to one document, we check once
        boolean docAuthorized = authorizationService.isAccessibleForRag(
                documentRepository.findById(documentId).orElse(null), user);
        
        List<Document> authorized = merged.stream()
                .filter(doc -> docAuthorized)
                .toList();

        long elapsed = System.currentTimeMillis() - start;
        logger.info("Scoped retrieval returned {} authorized chunks in {}ms", authorized.size(), elapsed);

        return authorized;
    }

    private void verifyDocumentAccess(User user, Long documentId) {
        com.sakh.entity.Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        // Use centralized authorization policy for consistency with DocumentService and RAG retrieval
        authorizationService.checkDocumentViewAccess(document, user);
        authorizationService.requireReadable(document, user);
    }

    /**
     * Semantic candidate generation. The filter expression is produced by the
     * centralized {@link DocumentAuthorizationService} so that candidate recall
     * covers ancestor departments and the no-department own-upload case.
     * ADMIN receives no filter (null).
     *
     * <p>This is a recall filter only. The authoritative authorization decision
     * is always made afterwards by {@code isAccessibleForRag}.
     */
    private List<Document> semanticSearch(String question, User user, int topK) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(question)
                .topK(topK);

        String filter = authorizationService.buildVectorFilterExpression(user);
        if (filter != null) {
            builder.filterExpression(filter);
        }

        return vectorStore.similaritySearch(builder.build());
    }

    /**
     * Scoped semantic candidate generation. Access to the document has already
     * been verified by {@link #verifyDocumentAccess} before this is called.
     */
    private List<Document> semanticSearch(String question, User user, int topK, Long documentId) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .filterExpression("documentId == " + documentId);

        return vectorStore.similaritySearch(builder.build());
    }

    private List<Document> keywordSearch(String question, String role, List<Long> visibleDepartmentIds,
                                         String email, int topK) {
        boolean unrestricted = "ADMIN".equals(role);
        // Shared documents are visible to every authenticated user, so the candidate
        // query must admit them for every non-admin role.
        boolean includeShared = true;
        // Own-upload fallback is an EMPLOYEE-only privilege.
        boolean includeOwnUploads = "EMPLOYEE".equals(role);

        // The IN list must never be empty; -1 is a sentinel that matches no real department.
        List<Long> deptIds = visibleDepartmentIds == null || visibleDepartmentIds.isEmpty()
                ? List.of(NO_DEPARTMENT_SENTINEL)
                : visibleDepartmentIds;

        List<Object[]> rows = chunkRepository.findKeywordSearchGlobal(
                question, topK, deptIds, email == null ? "" : email,
                unrestricted, includeShared, includeOwnUploads);

        List<Document> results = new ArrayList<>();

        for (Object[] row : rows) {
            Long rowDeptId = row[6] != null ? ((Number) row[6]).longValue() : null;
            String uploadedBy = row[7] != null ? row[7].toString() : null;
            AccessScope rowScope = parseAccessScope(row[8]);

            // Defence in depth: the SQL predicate already restricted the rows, but the
            // policy is re-applied here so a query change cannot widen the candidate set.
            if (!isCandidateAccessible(role, visibleDepartmentIds, email, rowDeptId, uploadedBy, rowScope)) {
                continue;
            }

            results.add(toKeywordDocument(row));
        }

        return results;
    }

    private List<Document> keywordSearchInDocument(String question, Long documentId, int topK) {
        List<Object[]> rows = chunkRepository.findKeywordSearchInDocument(question, documentId, topK);
        return rows.stream()
                .map(this::toKeywordDocument)
                .collect(Collectors.toList());
    }

    private Document toKeywordDocument(Object[] row) {
        Long chunkId = ((Number) row[0]).longValue();
        Long docId = ((Number) row[1]).longValue();
        Integer chunkIndex = row[2] != null ? ((Number) row[2]).intValue() : 0;
        String chunkText = row[3] != null ? row[3].toString() : "";
        Integer pageNumber = row[4] != null ? ((Number) row[4]).intValue() : null;
        String sectionTitle = row[5] != null ? row[5].toString() : null;
        Long rowDeptId = row[6] != null ? ((Number) row[6]).longValue() : null;
        String uploadedBy = row[7] != null ? row[7].toString() : null;
        double rank = row[9] != null ? ((Number) row[9]).doubleValue() : 0.0;

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("documentId", docId);
        metadata.put("chunkId", chunkId);
        metadata.put("chunkIndex", chunkIndex);
        if (pageNumber != null) metadata.put("pageNumber", pageNumber);
        if (sectionTitle != null) metadata.put("sectionTitle", sectionTitle);
        if (rowDeptId != null) metadata.put("departmentId", rowDeptId);
        if (uploadedBy != null) metadata.put("uploadedBy", uploadedBy);

        return Document.builder()
                .text(chunkText)
                .metadata(metadata)
                .score(rank)
                .build();
    }

    /**
     * Candidate-level pre-filter that mirrors {@code DocumentAuthorizationService.isAccessibleForRag}.
     *
     * <p>This exists purely to keep the candidate set close to the permitted set
     * (recall). It is deliberately permissive within the permitted scope and is
     * never the authorization boundary: the final centralized
     * {@code isAccessibleForRag} filter in {@link #retrieve} still runs over every
     * merged chunk before it can reach the LLM prompt.
     */
    private boolean isCandidateAccessible(String role, List<Long> visibleDepartmentIds, String email,
                                          Long docDeptId, String uploadedBy, AccessScope scope) {
        if ("ADMIN".equals(role)) {
            return true;
        }

        // Documents shared with everyone are permitted regardless of department.
        if (scope == AccessScope.ALL) {
            return true;
        }

        // Own department + ancestors (for a department-less user this set is the
        // [-1] sentinel, which never matches a real department id).
        if (docDeptId != null && visibleDepartmentIds.contains(docDeptId)) {
            return true;
        }

        // An employee can always reach their own uploads, including when their
        // account has no department assigned.
        return "EMPLOYEE".equals(role)
                && email != null
                && uploadedBy != null
                && uploadedBy.equalsIgnoreCase(email);
    }

    /**
     * Parses the raw {@code documents.access_scope} value from the native query.
     * An unrecognised or missing value fails closed to {@link AccessScope#DEPARTMENT},
     * the restrictive default, rather than aborting the whole retrieval.
     */
    private static AccessScope parseAccessScope(Object raw) {
        if (raw == null) {
            return AccessScope.DEPARTMENT;
        }
        try {
            return AccessScope.valueOf(raw.toString().trim());
        } catch (IllegalArgumentException e) {
            logger.warn("Unrecognised access scope '{}' - treating as DEPARTMENT", raw);
            return AccessScope.DEPARTMENT;
        }
    }

    private List<Document> mergeAndRank(List<Document> semantic, List<Document> keyword, int topK) {
        if (semantic.isEmpty() && keyword.isEmpty()) {
            return List.of();
        }

        Map<Long, Double> semanticScores = new HashMap<>();
        Map<Long, Document> docsByChunkId = new HashMap<>();

        for (Document doc : semantic) {
            Long chunkId = extractChunkId(doc);
            if (chunkId == null) continue;
            Double semScore = doc.getScore();
            if (semScore != null) {
                semanticScores.merge(chunkId, semScore, Double::max);
            }
            docsByChunkId.putIfAbsent(chunkId, doc);
        }

        double maxKeywordScore = 0.0;
        Map<Long, Double> keywordScores = new HashMap<>();
        for (Document doc : keyword) {
            Long chunkId = extractChunkId(doc);
            if (chunkId == null) continue;
            Double kwScore = doc.getScore();
            if (kwScore != null && kwScore > 0) {
                keywordScores.merge(chunkId, kwScore, Double::max);
                if (kwScore > maxKeywordScore) {
                    maxKeywordScore = kwScore;
                }
            }
            if (!docsByChunkId.containsKey(chunkId)) {
                docsByChunkId.put(chunkId, doc);
            }
        }

        double finalMaxKeyword = maxKeywordScore;

        List<Document> ranked = docsByChunkId.entrySet().stream()
                .map(entry -> {
                    Long chunkId = entry.getKey();
                    Document doc = entry.getValue();
                    double semScore = semanticScores.getOrDefault(chunkId, 0.0);
                    double kwRaw = keywordScores.getOrDefault(chunkId, 0.0);
                    double kwScore = finalMaxKeyword > 0 ? kwRaw / finalMaxKeyword : 0.0;
                    double combined = SEMANTIC_WEIGHT * semScore + KEYWORD_WEIGHT * kwScore;

                    return Pair.of(doc, combined);
                })
                .sorted(Comparator.<Pair>comparingDouble(p -> p.score).reversed())
                .limit(topK)
                .map(p -> {
                    Document doc = p.doc;
                    Document rankedDoc = Document.builder()
                            .text(doc.getText())
                            .metadata(new HashMap<>(doc.getMetadata()))
                            .score(Math.min(p.score, 1.0))
                            .build();
                    return rankedDoc;
                })
                .collect(Collectors.toList());

        return ranked;
    }

    private static Long extractChunkId(Document doc) {
        Object chunkIdObj = doc.getMetadata().get("chunkId");
        if (chunkIdObj instanceof Number num) {
            return num.longValue();
        }
        return null;
    }

private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String email;
        if (principal instanceof UserDetails userDetails) {
            email = userDetails.getUsername();
        } else {
            email = principal.toString();
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private static class Pair {
        final Document doc;
        final double score;
        private Pair(Document doc, double score) {
            this.doc = doc;
            this.score = score;
        }
        static Pair of(Document doc, double score) {
            return new Pair(doc, score);
        }
    }
}
