package com.sakh.service;

import com.sakh.dto.RagMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsCollectorTest {

    private MetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new MetricsCollector();
    }

    @Test
    void getMetrics_returnsZeroWhenNoData() {
        RagMetrics metrics = collector.getMetrics();

        assertEquals(0, metrics.getTotalQueries());
        assertEquals(0.0, metrics.getRetrievalRecall());
        assertEquals(0.0, metrics.getContextPrecision());
        assertEquals(0.0, metrics.getAverageSimilarity());
        assertEquals(0.0, metrics.getAverageResponseTimeMs());
        assertEquals(0.0, metrics.getHallucinationRate());
    }

    @Test
    void getMetrics_computesCorrectValues() {
        collector.recordQuery(100);
        collector.recordQuery(200);
        collector.recordRetrieval(0.9);
        collector.recordRetrieval(0.5);
        collector.recordRetrieval(0.8);
        collector.recordHallucination(10, 2);
        collector.recordHallucination(5, 0);

        RagMetrics metrics = collector.getMetrics();

        assertEquals(2, metrics.getTotalQueries());
        assertEquals(2.0 / 3, metrics.getRetrievalRecall(), 0.0001); // 2 above threshold out of 3
        assertEquals(2.0 / 3, metrics.getContextPrecision(), 0.0001);
        assertEquals((0.9 + 0.5 + 0.8) / 3, metrics.getAverageSimilarity(), 0.0001);
        assertEquals(150.0, metrics.getAverageResponseTimeMs(), 0.01);
        assertEquals(2.0 / 15, metrics.getHallucinationRate(), 0.0001);
    }

    @Test
    void getMetrics_roundsAppropriately() {
        collector.recordQuery(1);
        collector.recordRetrieval(0.123456);

        RagMetrics metrics = collector.getMetrics();

        assertEquals(0.1235, metrics.getAverageSimilarity(), 0.0001);
    }
}
