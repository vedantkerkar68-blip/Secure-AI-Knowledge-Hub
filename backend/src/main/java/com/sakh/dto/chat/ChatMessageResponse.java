package com.sakh.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ChatMessageResponse {

    private final Long id;
    private final String role;
    private final String content;
    private final List<CitationDTO> citations;
    private final BigDecimal confidence;
    private final Instant createdAt;
}