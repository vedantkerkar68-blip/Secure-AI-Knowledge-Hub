package com.sakh.llm;

import com.sakh.entity.Chunk;
import com.sakh.rag.RetrieverService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LLMService {

    private final RetrieverService retrieverService;
    private final LLMProvider llmProvider;

    private static final String SYSTEM_PROMPT = """
        You are a helpful AI assistant for an enterprise knowledge management system.
        Answer questions based ONLY on the provided context from the company's internal documents.
        If the context does not contain enough information to answer the question, say so clearly.
        Always cite your sources by referencing the document title and chunk content.
        Be concise and professional.
        """;

    public LLMService(RetrieverService retrieverService, LLMProvider llmProvider) {
        this.retrieverService = retrieverService;
        this.llmProvider = llmProvider;
    }

    public LLMResponse answerQuestion(String question, com.sakh.entity.User user) {
        // Retrieve relevant chunks
        List<Chunk> chunks = retrieverService.retrieve(question, user, 5);

        if (chunks.isEmpty()) {
            return LLMResponse.builder()
                    .answer("I couldn't find any relevant documents to answer your question. Please try rephrasing or check if you have access to the relevant documents.")
                    .citations(List.of())
                    .build();
        }

        // Build context from chunks
        String context = chunks.stream()
                .map(c -> "Document: " + c.getDocument().getTitle() + "\nContent: " + c.getChunkText())
                .collect(Collectors.joining("\n\n---\n\n"));

        // Build prompt
        String prompt = "Question: " + question + "\n\nContext:\n" + context;

        // Generate answer
        String answer = llmProvider.generate(prompt, SYSTEM_PROMPT);

        return LLMResponse.from(answer, chunks);
    }
}