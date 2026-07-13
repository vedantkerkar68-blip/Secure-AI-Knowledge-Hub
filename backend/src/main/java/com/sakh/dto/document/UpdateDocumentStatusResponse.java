package com.sakh.dto.document;

import com.sakh.enums.DocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UpdateDocumentStatusResponse {

    private final Long documentId;
    private final DocumentStatus status;
}