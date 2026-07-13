package com.sakh.llm;

import com.sakh.entity.Chunk;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMResponse {

    private String answer;
    private List<Citation> citations;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Citation {
        private Long documentId;
        private String documentTitle;
        private Integer pageNumber;
        private String sectionTitle;
        private Integer chunkIndex;
        private String chunkText;
    }

    public static LLMResponse from(String answer, List<Chunk> chunks) {
        List<Citation> citations = chunks.stream()
                .map(LLMResponse::toCitation)
                .collect(Collectors.toList());

        return LLMResponse.builder()
                .answer(answer)
                .citations(citations)
                .build();
    }

    private static Citation toCitation(Chunk chunk) {
        return Citation.builder()
                .documentId(chunk.getDocument() != null ? chunk.getDocument().getId() : null)
                .documentTitle(chunk.getDocument() != null ? chunk.getDocument().getTitle() : null)
                .pageNumber(chunk.getPageNumber())
                .sectionTitle(chunk.getSectionTitle())
                .chunkIndex(chunk.getChunkIndex())
                .chunkText(truncate(chunk.getChunkText(), 200))
                .build();
    }

    private static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}