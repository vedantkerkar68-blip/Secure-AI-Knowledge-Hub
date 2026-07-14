package com.sakh.rag;

import com.sakh.entity.User;
import com.sakh.repository.ChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RetrieverService {

    private static final Logger logger = LoggerFactory.getLogger(RetrieverService.class);

    private static final double SEMANTIC_WEIGHT = 0.7;
    private static final double KEYWORD_WEIGHT = 0.3;
    private static final int HYBRID_MULTIPLIER = 2;

    private final VectorStore vectorStore;
    private final ChunkRepository chunkRepository;
    private final QueryRewriter queryRewriter;

    public RetrieverService(VectorStore vectorStore, ChunkRepository chunkRepository,
                            QueryRewriter queryRewriter) {
        this.vectorStore = vectorStore;
        this.chunkRepository = chunkRepository;
        this.queryRewriter = queryRewriter;
    }

    public List<Document> retrieve(String question, Long departmentId, int topK) {
        long start = System.currentTimeMillis();
        String rewritten = queryRewriter.rewrite(question);

        logger.info("Semantic-only retrieval - question: '{}' (rewritten: '{}'), department: {}, topK: {}",
                question, rewritten, departmentId, topK);

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(rewritten)
                .topK(topK);

        if (departmentId != null) {
            builder.filterExpression("departmentId == " + departmentId);
        }

        List<Document> results = vectorStore.similaritySearch(builder.build());

        long elapsed = System.currentTimeMillis() - start;
        logger.info("Retrieved {} chunks in {}ms", results.size(), elapsed);

        return results;
    }

    public List<Document> retrieve(String question, User user, int topK) {
        long start = System.currentTimeMillis();
        String rewritten = queryRewriter.rewrite(question);

        String role = user.getRole() != null ? user.getRole().getName() : "";
        Long departmentId = user.getDepartment() != null ? user.getDepartment().getId() : null;

        logger.info("Hybrid retrieval - question: '{}' (rewritten: '{}'), user: {}, role: {}, department: {}, topK: {}",
                question, rewritten, user.getEmail(), role, departmentId, topK);

        int fetchSize = topK * HYBRID_MULTIPLIER;

        List<Document> semanticResults = semanticSearch(rewritten, role, departmentId, user.getEmail(), fetchSize);
        logger.debug("Semantic search returned {} results", semanticResults.size());

        List<Document> keywordResults = keywordSearch(rewritten, role, departmentId, user.getEmail(), fetchSize);
        logger.debug("Keyword search returned {} results", keywordResults.size());

        List<Document> merged = mergeAndRank(semanticResults, keywordResults, topK);

        long elapsed = System.currentTimeMillis() - start;
        logger.info("Hybrid retrieval returned {} chunks in {}ms", merged.size(), elapsed);

        return merged;
    }

    private List<Document> semanticSearch(String question, String role, Long departmentId,
                                          String email, int topK) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(question)
                .topK(topK);

        if ("ADMIN".equals(role)) {
        } else if (departmentId != null) {
            if ("MANAGER".equals(role) || email == null) {
                builder.filterExpression("departmentId == " + departmentId);
            } else {
                builder.filterExpression("departmentId == " + departmentId
                        + " || uploadedBy == '" + email.replace("'", "''") + "'");
            }
        }

        return vectorStore.similaritySearch(builder.build());
    }

    private List<Document> keywordSearch(String question, String role, Long departmentId,
                                         String email, int topK) {
        List<Object[]> rows = chunkRepository.findKeywordSearchGlobal(question, topK);

        List<Document> results = new ArrayList<>();

        for (Object[] row : rows) {
            Long chunkId = ((Number) row[0]).longValue();
            Long docId = ((Number) row[1]).longValue();
            Integer chunkIndex = row[2] != null ? ((Number) row[2]).intValue() : 0;
            String chunkText = row[3] != null ? row[3].toString() : "";
            Integer pageNumber = row[4] != null ? ((Number) row[4]).intValue() : null;
            String sectionTitle = row[5] != null ? row[5].toString() : null;
            Long rowDeptId = row[6] != null ? ((Number) row[6]).longValue() : null;
            String uploadedBy = row[7] != null ? row[7].toString() : null;
            double rank = row[8] != null ? ((Number) row[8]).doubleValue() : 0.0;

            if (!isAccessible(role, departmentId, email, rowDeptId, uploadedBy)) {
                continue;
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("documentId", docId);
            metadata.put("chunkId", chunkId);
            metadata.put("chunkIndex", chunkIndex);
            if (pageNumber != null) metadata.put("pageNumber", pageNumber);
            if (sectionTitle != null) metadata.put("sectionTitle", sectionTitle);
            if (rowDeptId != null) metadata.put("departmentId", rowDeptId);
            if (uploadedBy != null) metadata.put("uploadedBy", uploadedBy);

            Document doc = Document.builder()
                    .text(chunkText)
                    .metadata(metadata)
                    .score(rank)
                    .build();
            results.add(doc);
        }

        return results;
    }

    private boolean isAccessible(String role, Long userDeptId, String email,
                                 Long docDeptId, String uploadedBy) {
        if ("ADMIN".equals(role)) {
            return true;
        }
        if (userDeptId == null) {
            return false;
        }
        if (docDeptId != null && docDeptId.equals(userDeptId)) {
            return true;
        }
        return "EMPLOYEE".equals(role)
                && email != null
                && uploadedBy != null
                && uploadedBy.equalsIgnoreCase(email);
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
