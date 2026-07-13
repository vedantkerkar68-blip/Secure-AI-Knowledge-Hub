package com.sakh.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class GeminiLLMProvider implements LLMProvider {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;

    public GeminiLLMProvider(
            @Value("${app.ai.llm.gemini.api-key:}") String apiKey,
            @Value("${app.ai.llm.gemini.model:gemini-2.5-flash}") String model,
            @Value("${app.ai.llm.gemini.temperature:0.1}") double temperature,
            @Value("${app.ai.llm.gemini.max-tokens:4096}") int maxTokens) {
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String generate(String prompt, String systemPrompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt cannot be blank");
        }

        String fullPrompt = (systemPrompt != null && !systemPrompt.isBlank())
                ? systemPrompt + "\n\n" + prompt
                : prompt;

        Map<String, Object> request = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", fullPrompt)))),
                "generationConfig", Map.of(
                        "temperature", temperature,
                        "maxOutputTokens", maxTokens,
                        "topP", 0.95,
                        "topK", 40
                )
        );

        try {
            GenerateResponse response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/{model}:generateContent")
                            .queryParam("key", apiKey)
                            .build(model))
                    .body(request)
                    .retrieve()
                    .body(GenerateResponse.class);

            if (response != null && response.candidates() != null && !response.candidates().isEmpty()) {
                var content = response.candidates().get(0).content();
                if (content != null && content.parts() != null && !content.parts().isEmpty()) {
                    return content.parts().get(0).text();
                }
            }
        } catch (Exception e) {
            throw new LLMException("Failed to generate response from Gemini", e);
        }

        return "";
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    // DTOs for response parsing
    public record GenerateResponse(List<Candidate> candidates) {}
    public record Candidate(Content content) {}
    public record Content(List<Part> parts) {}
    public record Part(String text) {}

    public static class LLMException extends RuntimeException {
        public LLMException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}