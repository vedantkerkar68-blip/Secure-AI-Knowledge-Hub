package com.sakh.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
public class DocumentResponse {

    private final Long id;
    private final String title;
    private final String originalFilename;
    private final String fileType;
    private final Long fileSize;
    private final String department;
    private final String uploadedBy;
    private final Instant uploadedAt;
    private final String status;
}