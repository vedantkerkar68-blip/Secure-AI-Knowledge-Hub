package com.sakh.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
public class DocumentVersionResponse {

    private final Long id;
    private final Long groupId;
    private final String originalFilename;
    private final Integer version;
    private final Boolean isLatest;
    private final String fileType;
    private final Long fileSize;
    private final Instant createdAt;
    private final Instant updatedAt;
}