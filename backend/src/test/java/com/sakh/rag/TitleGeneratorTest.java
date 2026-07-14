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
class TitleGeneratorTest {

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private TitleGenerator titleGenerator;

    @Test
    void generate_returnsTitle() {
        when(chatModel.call(anyString())).thenReturn("Leave Policy Discussion");

        String result = titleGenerator.generate("Explain Leave Policy");

        assertEquals("Leave Policy Discussion", result);
    }

    @Test
    void generate_fallbackOnException() {
        when(chatModel.call(anyString())).thenThrow(new RuntimeException("API error"));

        String result = titleGenerator.generate("Explain Leave Policy");

        assertNull(result);
    }

    @Test
    void generate_returnsNullOnBlankInput() {
        String result = titleGenerator.generate("");

        assertNull(result);
    }
}
