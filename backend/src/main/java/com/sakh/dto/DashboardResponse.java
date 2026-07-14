package com.sakh.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class DashboardResponse {

    private final long users;
    private final long departments;
    private final long documents;
    private final long processedDocuments;
    private final long failedDocuments;
    private final long chatSessions;
    private final long chatMessages;
    private final long vectorDocuments;
}
