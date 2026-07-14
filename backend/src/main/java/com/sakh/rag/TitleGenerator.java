package com.sakh.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

@Component
public class TitleGenerator {

    private static final Logger logger = LoggerFactory.getLogger(TitleGenerator.class);

    private static final String PROMPT_TEMPLATE = """
            Generate a short, descriptive title (max 6 words) for a chat session based on the user's first question.
            Return only the title, no quotes or extra text.

            User question: %s\
            """;

    private final ChatModel chatModel;

    public TitleGenerator(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String generate(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }

        try {
            String prompt = PROMPT_TEMPLATE.formatted(question);
            String title = chatModel.call(prompt).trim();
            if (title.isBlank() || title.length() > 100) {
                return null;
            }
            logger.debug("Generated title: '{}' for question: '{}'", title, question);
            return title;
        } catch (Exception e) {
            logger.warn("Title generation failed: {}", e.getMessage());
            return null;
        }
    }
}
