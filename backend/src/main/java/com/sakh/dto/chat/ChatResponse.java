package com.sakh.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ChatResponse {

    private final String answer;
    private final BigDecimal confidence;
    private final List<CitationDTO> citations;
}
