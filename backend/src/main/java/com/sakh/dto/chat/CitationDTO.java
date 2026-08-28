package com.sakh.dto.chat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CitationDTO {

    private final Long documentId;
    private final String documentTitle;
    private final Integer version;
    private final String department;
    private final Integer pageNumber;
    private final String sectionTitle;
    private final Integer chunkIndex;
    private final Double similarityScore;

    @JsonCreator
    public CitationDTO(@JsonProperty("documentId") Long documentId,
                       @JsonProperty("documentTitle") String documentTitle,
                       @JsonProperty("version") Integer version,
                       @JsonProperty("department") String department,
                       @JsonProperty("pageNumber") Integer pageNumber,
                       @JsonProperty("sectionTitle") String sectionTitle,
                       @JsonProperty("chunkIndex") Integer chunkIndex,
                       @JsonProperty("similarityScore") Double similarityScore) {
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.version = version;
        this.department = department;
        this.pageNumber = pageNumber;
        this.sectionTitle = sectionTitle;
        this.chunkIndex = chunkIndex;
        this.similarityScore = similarityScore;
    }
}
