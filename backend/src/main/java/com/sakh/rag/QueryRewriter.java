package com.sakh.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

@Component
public class QueryRewriter {

    private static final Logger logger = LoggerFactory.getLogger(QueryRewriter.class);

    private static final String SYSTEM_PROMPT = """
            You are a query rewriter for an enterprise knowledge base.
            Rewrite the user's question to be more specific and searchable.
            Keep it concise.
            If the question is already well-formed, return it as-is.
            Do not add pleasantries.
            Only output the rewritten query.\
            """;

    private final ChatModel chatModel;

    public QueryRewriter(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String rewrite(String question) {
        if (question == null || question.isBlank()) {
            return question;
        }

        try {
            String prompt = SYSTEM_PROMPT + "\n\nUser question: " + question;
            String rewritten = chatModel.call(prompt).trim();

            if (rewritten.isBlank() || rewritten.equalsIgnoreCase(question.trim())) {
                return question;
            }

            logger.debug("Query rewritten: '{}' -> '{}'", question, rewritten);
            return rewritten;
        } catch (Exception e) {
            logger.warn("Query rewrite failed, using original query: {}", e.getMessage());
            return question;
        }
    }
}
