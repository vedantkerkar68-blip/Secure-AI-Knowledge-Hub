package com.sakh.llm;

public interface LLMProvider {

    String generate(String prompt, String systemPrompt);

    String getProviderName();
}