package com.sakh.controller;

import com.sakh.dto.RagMetrics;
import com.sakh.service.MetricsCollector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/rag")
@Tag(name = "Dashboard", description = "RAG metrics and admin dashboard endpoints")
@SecurityRequirement(name = "JWT")
public class MetricsController {

    private final MetricsCollector metricsCollector;

    public MetricsController(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get RAG metrics", description = "Returns RAG evaluation metrics including recall, precision, similarity, response time, and hallucination rate (admin only)")
    public ResponseEntity<RagMetrics> getMetrics() {
        return ResponseEntity.ok(metricsCollector.getMetrics());
    }
}
