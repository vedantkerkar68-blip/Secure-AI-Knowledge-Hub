package com.sakh.service;

import com.sakh.entity.Chunk;
import com.sakh.entity.Document;
import com.sakh.repository.ChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ChunkService {

    private static final int TARGET_TOKENS = 700;
    private static final int OVERLAP_TOKENS = 100;
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");

    private final ChunkRepository chunkRepository;

    public ChunkService(ChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    @Transactional
    public List<Chunk> chunkDocument(Document document, String text) {
        chunkRepository.deleteByDocumentId(document.getId());
        chunkRepository.flush();

        List<String> sentences = splitIntoSentences(text);
        List<String> chunks = mergeIntoChunks(sentences);

        List<Chunk> chunkEntities = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = new Chunk();
            chunk.setDocument(document);
            chunk.setChunkIndex(i);
            chunk.setChunkText(chunks.get(i));
            chunk.setTokenCount(estimateTokenCount(chunks.get(i)));
            chunkEntities.add(chunk);
        }

        return chunkRepository.saveAll(chunkEntities);
    }

    private List<String> splitIntoSentences(String text) {
        String[] parts = SENTENCE_SPLIT.split(text.trim());
        List<String> sentences = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                sentences.add(part.trim());
            }
        }
        return sentences;
    }

    private List<String> mergeIntoChunks(List<String> sentences) {
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        int currentTokens = 0;

        for (String sentence : sentences) {
            int sentenceTokens = estimateTokenCount(sentence);

            if (currentTokens + sentenceTokens > TARGET_TOKENS && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());

                // Keep overlap
                String overlapText = getOverlapText(currentChunk.toString(), OVERLAP_TOKENS);
                currentChunk = new StringBuilder(overlapText);
                currentTokens = estimateTokenCount(overlapText);
            }

            if (currentChunk.length() > 0) {
                currentChunk.append(" ");
            }
            currentChunk.append(sentence);
            currentTokens += sentenceTokens;
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    private String getOverlapText(String text, int targetTokens) {
        String[] words = text.split("\\s+");
        if (words.length <= targetTokens / 4) {
            return text;
        }
        int start = Math.max(0, words.length - targetTokens / 4);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < words.length; i++) {
            sb.append(words[i]).append(" ");
        }
        return sb.toString().trim();
    }

    private int estimateTokenCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        // Rough estimation: 1 token ≈ 4 characters
        return (text.length() + 3) / 4;
    }
}