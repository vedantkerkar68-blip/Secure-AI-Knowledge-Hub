package com.sakh.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class RagMetrics {

    private final long totalQueries;
    private final double retrievalRecall;
    private final double contextPrecision;
    private final double averageSimilarity;
    private final double averageResponseTimeMs;
    private final double hallucinationRate;
}
