package com.sakh.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerVerifierTest {

    private final AnswerVerifier verifier = new AnswerVerifier();

    @Test
    void verify_keepsSupportedSentence() {
        Document doc = Document.builder()
                .text("The company leave policy allows 30 days.")
                .metadata(Map.of("chunkId", 1L))
                .build();

        String answer = "The leave policy allows 30 days.";

        var result = verifier.verify(answer, List.of(doc));

        assertTrue(result.text().contains("leave policy"));
        assertEquals(0, result.removedSentences());
    }

    @Test
    void verify_removesUnsupportedSentence() {
        Document doc = Document.builder()
                .text("Company policy requires 30 days notice.")
                .metadata(Map.of("chunkId", 1L))
                .build();

        String answer = "The sky is purple. Company policy requires 30 days notice.";

        var result = verifier.verify(answer, List.of(doc));

        assertTrue(result.text().contains("30 days notice"));
        assertEquals(1, result.removedSentences());
    }

    @Test
    void verify_handlesEmptyDocuments() {
        String answer = "Some text here.";

        var result = verifier.verify(answer, List.of());

        assertEquals("Some text here.", result.text());
        assertEquals(0, result.totalSentences());
    }

    @Test
    void verify_allRemovedWhenNothingMatches() {
        Document doc = Document.builder()
                .text("The document is about something completely different unrelated to the answer here guys")
                .metadata(Map.of("chunkId", 1L))
                .build();

        String answer = "Quantum physics explains the nature of reality at the subatomic level.";

        var result = verifier.verify(answer, List.of(doc));

        assertTrue(result.text().isBlank());
        assertTrue(result.removedSentences() > 0);
    }
}
