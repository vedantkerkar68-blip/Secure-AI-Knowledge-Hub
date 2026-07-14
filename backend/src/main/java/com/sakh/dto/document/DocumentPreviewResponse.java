package com.sakh.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class DocumentPreviewResponse {

    private final String title;
    private final String summary;
    private final String language;
    private final Integer pageCount;
    private final String author;
    private final List<String> tags;
    private final String version;
    private final String department;
    private final String uploadedBy;
    private final Instant createdAt;
}
