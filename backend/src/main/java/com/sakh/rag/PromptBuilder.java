package com.sakh.rag;

import com.sakh.entity.Chunk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class PromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are an enterprise knowledge assistant. Answer questions using ONLY the provided context from internal documents.

            Rules:
            1. Base your answer solely on the provided context.
            2. If the context does not contain enough information, say "I cannot answer based on the available documents."
            3. Cite sources using [Doc X, Chunk Y] format after each factual claim.
            4. Do not use external knowledge.
            5. Be concise and professional.
            """;

    private static final String USER_TEMPLATE = """
            Question: %s

            Context:
            %s

            Answer:
            """;

    public String buildPrompt(String question, List<Chunk> chunks) {
        String context = buildContext(chunks);
        return String.format(USER_TEMPLATE, question, context);
    }

    public String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    private String buildContext(List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "No relevant context found.";
        }

        return chunks.stream()
                .map(this::formatChunk)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private String formatChunk(Chunk chunk) {
        String docTitle = chunk.getDocument() != null ? chunk.getDocument().getTitle() : "Unknown";
        long docId = chunk.getDocument() != null ? chunk.getDocument().getId() : 0;
        int chunkIndex = chunk.getChunkIndex();
        String text = chunk.getChunkText();

        return String.format("[Doc %d, Chunk %d] %s\n%s", docId, chunkIndex, docTitle, text);
    }
}