package com.sakh.service;

import com.sakh.dto.RagMetrics;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsCollector {

    private static final double SIMILARITY_THRESHOLD = 0.7;

    private final AtomicLong totalQueries = new AtomicLong();
    private final AtomicLong totalRetrievedDocs = new AtomicLong();
    private final AtomicLong docsAboveThreshold = new AtomicLong();
    private final AtomicLong totalResponseTimeMs = new AtomicLong();
    private final AtomicLong totalSentencesGenerated = new AtomicLong();
    private final AtomicLong totalSentencesRemoved = new AtomicLong();
    private final AtomicLong sumSimilarityNumerator = new AtomicLong();

    public void recordQuery(long responseTimeMs) {
        totalQueries.incrementAndGet();
        totalResponseTimeMs.addAndGet(responseTimeMs);
    }

    public void recordRetrieval(double similarity) {
        totalRetrievedDocs.incrementAndGet();
        long scaled = (long) (similarity * 1_000_000);
        sumSimilarityNumerator.addAndGet(scaled);
        if (similarity >= SIMILARITY_THRESHOLD) {
            docsAboveThreshold.incrementAndGet();
        }
    }

    public void recordHallucination(int generated, int removed) {
        totalSentencesGenerated.addAndGet(generated);
        totalSentencesRemoved.addAndGet(removed);
    }

    public RagMetrics getMetrics() {
        long queries = totalQueries.get();
        long retrieved = totalRetrievedDocs.get();
        long above = docsAboveThreshold.get();
        long responseMs = totalResponseTimeMs.get();
        long generated = totalSentencesGenerated.get();
        long removed = totalSentencesRemoved.get();
        long sumNum = sumSimilarityNumerator.get();

        double recall = retrieved > 0 ? (double) above / retrieved : 0.0;
        double precision = retrieved > 0 ? (double) above / retrieved : 0.0;
        double avgSimilarity = retrieved > 0 ? (double) sumNum / (retrieved * 1_000_000) : 0.0;
        double avgResponseTime = queries > 0 ? (double) responseMs / queries : 0.0;
        double hallucinationRate = generated > 0 ? (double) removed / generated : 0.0;

        return RagMetrics.builder()
                .totalQueries(queries)
                .retrievalRecall(round(recall, 4))
                .contextPrecision(round(precision, 4))
                .averageSimilarity(round(avgSimilarity, 4))
                .averageResponseTimeMs(round(avgResponseTime, 2))
                .hallucinationRate(round(hallucinationRate, 4))
                .build();
    }

    private static double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
