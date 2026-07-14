package com.sakh.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationSummarizerTest {

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private ConversationSummarizer summarizer;

    @Test
    void summarize_returnsSummary() {
        when(chatModel.call(anyString())).thenReturn("User asked about leave policy. Assistant explained 30-day notice.");

        String result = summarizer.summarize("User: What is leave policy?\nAssistant: 30 days notice is required.");

        assertEquals("User asked about leave policy. Assistant explained 30-day notice.", result);
    }

    @Test
    void summarize_fallbackOnException() {
        when(chatModel.call(anyString())).thenThrow(new RuntimeException("API error"));

        String result = summarizer.summarize("User: Hello");

        assertNull(result);
    }

    @Test
    void summarize_returnsNullOnBlankInput() {
        String result = summarizer.summarize("");

        assertNull(result);
    }
}
