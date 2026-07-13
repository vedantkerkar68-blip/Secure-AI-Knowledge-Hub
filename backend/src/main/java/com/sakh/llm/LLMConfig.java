package com.sakh.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LLMConfig {

    @Bean
    public LLMProvider llmProvider(
            @Value("${app.ai.llm.provider:gemini}") String provider,
            @Value("${app.ai.llm.gemini.api-key:}") String geminiApiKey,
            @Value("${app.ai.llm.gemini.model:gemini-2.5-flash}") String geminiModel,
            @Value("${app.ai.llm.gemini.temperature:0.1}") double geminiTemperature,
            @Value("${app.ai.llm.gemini.max-tokens:4096}") int geminiMaxTokens) {

        return switch (provider.toLowerCase()) {
            case "gemini" -> new GeminiLLMProvider(geminiApiKey, geminiModel, geminiTemperature, geminiMaxTokens);
            default -> throw new IllegalArgumentException("Unsupported LLM provider: " + provider);
        };
    }
}