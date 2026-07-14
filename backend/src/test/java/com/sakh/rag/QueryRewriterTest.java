package com.sakh.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryRewriterTest {

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private QueryRewriter queryRewriter;

    @Test
    void rewrite_returnsRewrittenQuery() {
        when(chatModel.call(anyString())).thenReturn("Employee leave policy details");

        String result = queryRewriter.rewrite("Leave");

        assertEquals("Employee leave policy details", result);
    }

    @Test
    void rewrite_fallbackOnException() {
        when(chatModel.call(anyString())).thenThrow(new RuntimeException("API error"));

        String result = queryRewriter.rewrite("Leave");

        assertEquals("Leave", result);
    }

    @Test
    void rewrite_returnsOriginalOnBlank() {
        when(chatModel.call(anyString())).thenReturn("   ");

        String result = queryRewriter.rewrite("Leave");

        assertEquals("Leave", result);
    }

    @Test
    void rewrite_returnsNullOnNullInput() {
        String result = queryRewriter.rewrite(null);

        assertEquals(null, result);
    }
}
