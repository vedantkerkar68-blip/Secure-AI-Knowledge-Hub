package com.sakh.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CitationDTO {

    private final Long documentId;
    private final String documentTitle;
    private final Integer version;
    private final String department;
    private final Integer pageNumber;
    private final String sectionTitle;
    private final Integer chunkIndex;
    private final Double similarityScore;
}
