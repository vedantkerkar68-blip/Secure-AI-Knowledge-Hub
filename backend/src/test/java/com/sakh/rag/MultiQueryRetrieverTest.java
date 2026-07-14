package com.sakh.rag;

import com.sakh.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiQueryRetrieverTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private RetrieverService retrieverService;

    @InjectMocks
    private MultiQueryRetriever multiQueryRetriever;

    @Test
    void retrieve_mergesAndDeduplicates() {
        when(chatModel.call(anyString())).thenReturn("Query1\nQuery2\nQuery3");

        Document doc1 = Document.builder().text("doc1").metadata(Map.of("chunkId", 1L)).score(0.9).build();
        Document doc2 = Document.builder().text("doc2").metadata(Map.of("chunkId", 2L)).score(0.8).build();

        when(retrieverService.retrieve(anyString(), any(User.class), anyInt()))
                .thenReturn(List.of(doc1))
                .thenReturn(List.of(doc2))
                .thenReturn(List.of(doc1)); // duplicate

        User user = new User();
        List<Document> results = multiQueryRetriever.retrieve("Leave policy", user, 5);

        assertEquals(2, results.size());
        assertEquals(0.9, results.get(0).getScore()); // highest score first
    }

    @Test
    void retrieve_fallbackOnGenerationFailure() {
        when(chatModel.call(anyString())).thenThrow(new RuntimeException("API error"));

        Document doc = Document.builder().text("policy").metadata(Map.of("chunkId", 1L)).score(0.9).build();
        when(retrieverService.retrieve(anyString(), any(User.class), anyInt()))
                .thenReturn(List.of(doc));

        User user = new User();
        List<Document> results = multiQueryRetriever.retrieve("Leave policy", user, 5);

        assertEquals(1, results.size());
        assertTrue(results.get(0).getText().contains("policy"));
    }
}
