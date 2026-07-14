package com.sakh.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

@Component
public class ConversationSummarizer {

    private static final Logger logger = LoggerFactory.getLogger(ConversationSummarizer.class);

    private static final String PROMPT_TEMPLATE = """
            Summarize the following conversation concisely, capturing key questions, answers, and decisions.
            Keep the summary under 200 words.

            %s

            Summary:\
            """;

    private final ChatModel chatModel;

    public ConversationSummarizer(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String summarize(String conversation) {
        if (conversation == null || conversation.isBlank()) {
            return null;
        }

        try {
            String prompt = PROMPT_TEMPLATE.formatted(conversation);
            String summary = chatModel.call(prompt).trim();
            if (summary.isBlank()) {
                return null;
            }
            logger.debug("Generated conversation summary ({} chars)", summary.length());
            return summary;
        } catch (Exception e) {
            logger.warn("Conversation summarization failed: {}", e.getMessage());
            return null;
        }
    }
}
