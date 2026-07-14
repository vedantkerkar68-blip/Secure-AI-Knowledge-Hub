package com.sakh.llm;

import com.sakh.exception.AiServiceException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

@Service
public class LLMService {

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;

    public LLMService(ChatModel chatModel, StreamingChatModel streamingChatModel) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
    }

    public Flux<String> streamAnswer(Prompt prompt) {
        return streamingChatModel.stream(prompt)
                .map(response -> {
                    if (response.getResult() != null
                            && response.getResult().getOutput() != null
                            && response.getResult().getOutput().getText() != null) {
                        return response.getResult().getOutput().getText();
                    }
                    return "";
                })
                .filter(text -> !text.isEmpty());
    }

    public String generateAnswer(Prompt prompt) {
        try {
            ChatResponse response = chatModel.call(prompt);
            String text = response.getResult().getOutput().getText();
            if (text == null || text.isBlank()) {
                throw new AiServiceException("AI service returned an empty response.");
            }
            return text;
        } catch (AiServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("timed out")) {
                throw new AiServiceException("AI service timed out. Please try again.", e);
            }
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("rate")) {
                throw new AiServiceException("AI service rate limit exceeded. Please wait and try again.", e);
            }
            throw new AiServiceException("AI service is currently unavailable.", e);
        }
    }
}
