package com.sakh.rag;

import com.sakh.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MultiQueryRetriever {

    private static final Logger logger = LoggerFactory.getLogger(MultiQueryRetriever.class);

    private static final String PROMPT_TEMPLATE = """
            Generate 3 different versions of the given user question to improve search retrieval.
            Each version should rephrase or expand the original question from a different perspective.
            Return one query per line, no numbering, no extra text.

            Original question: %s\
            """;

    private final ChatModel chatModel;
    private final RetrieverService retrieverService;

    public MultiQueryRetriever(ChatModel chatModel, RetrieverService retrieverService) {
        this.chatModel = chatModel;
        this.retrieverService = retrieverService;
    }

    public List<org.springframework.ai.document.Document> retrieve(String question, User user, int topK) {
        List<String> queries = generateQueries(question);
        logger.info("MultiQueryRetriever generated {} queries for '{}'", queries.size(), question);

        Map<Long, org.springframework.ai.document.Document> merged = new HashMap<>();

        for (String query : queries) {
            List<org.springframework.ai.document.Document> results = retrieverService.retrieve(query, user, topK);
            for (org.springframework.ai.document.Document doc : results) {
                Long chunkId = extractChunkId(doc);
                if (chunkId == null) continue;
                merged.merge(chunkId, doc, (existing, incoming) -> {
                    double existingScore = existing.getScore() != null ? existing.getScore() : 0.0;
                    double incomingScore = incoming.getScore() != null ? incoming.getScore() : 0.0;
                    return incomingScore > existingScore ? incoming : existing;
                });
            }
        }

        List<org.springframework.ai.document.Document> ranked = new ArrayList<>(merged.values());
        ranked.sort(Comparator.<org.springframework.ai.document.Document>comparingDouble(
                d -> d.getScore() != null ? d.getScore() : 0.0).reversed());

        if (ranked.size() > topK) {
            ranked = ranked.subList(0, topK);
        }

        logger.info("MultiQueryRetriever returned {} chunks after merge and rank", ranked.size());
        return ranked;
    }

    private List<String> generateQueries(String question) {
        try {
            String prompt = PROMPT_TEMPLATE.formatted(question);
            String response = chatModel.call(prompt).trim();

            String[] lines = response.split("\n");
            List<String> queries = new ArrayList<>();

            for (String line : lines) {
                String trimmed = line.strip();
                if (!trimmed.isBlank()) {
                    queries.add(trimmed);
                }
            }

            if (queries.isEmpty()) {
                logger.warn("Gemini returned empty response, falling back to original query");
                return List.of(question);
            }

            return queries;
        } catch (Exception e) {
            logger.warn("Query generation failed, falling back to original query: {}", e.getMessage());
            return List.of(question);
        }
    }

    private static Long extractChunkId(org.springframework.ai.document.Document doc) {
        Object chunkIdObj = doc.getMetadata().get("chunkId");
        if (chunkIdObj instanceof Number num) {
            return num.longValue();
        }
        return null;
    }
}
