package com.sakh.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AnswerVerifier {

    private static final Logger logger = LoggerFactory.getLogger(AnswerVerifier.class);

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern WORD_SPLIT = Pattern.compile("[^a-zA-Z0-9]+");
    private static final double SIMILARITY_THRESHOLD = 0.3;

    public VerificationResult verify(String answer, List<Document> documents) {
        if (answer == null || answer.isBlank() || documents == null || documents.isEmpty()) {
            return new VerificationResult(answer, 0, 0);
        }

        String[] sentences = SENTENCE_SPLIT.split(answer.trim());
        int total = 0;
        List<String> verified = new ArrayList<>();
        int removed = 0;

        List<Set<String>> chunkWordSets = documents.stream()
                .map(d -> words(d.getText()))
                .toList();

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            total++;

            if (isSupported(trimmed, chunkWordSets)) {
                verified.add(trimmed);
            } else {
                removed++;
                logger.debug("Removed unsupported sentence: '{}'", trimmed);
            }
        }

        if (removed > 0) {
            logger.info("AnswerVerifier removed {} of {} sentences", removed, total);
        }

        String text = verified.stream().collect(Collectors.joining(" "));
        return new VerificationResult(text, total, removed);
    }

    public record VerificationResult(String text, int totalSentences, int removedSentences) {}

    private boolean isSupported(String sentence, List<Set<String>> chunkWordSets) {
        Set<String> sentenceWords = words(sentence);
        if (sentenceWords.isEmpty()) {
            return true;
        }

        return chunkWordSets.stream().anyMatch(chunkWords -> {
            long intersection = sentenceWords.stream().filter(chunkWords::contains).count();
            return (double) intersection / sentenceWords.size() >= SIMILARITY_THRESHOLD;
        });
    }

    private static Set<String> words(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(WORD_SPLIT.split(text.toLowerCase()))
                .filter(w -> !w.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }
}
