package com.sakh.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Aggregated activity log statistics for the admin dashboard.
 */
@Getter
@Builder
@AllArgsConstructor
public class ActivityLogSummaryResponse {

    private final long total;

    private final Map<String, Long> byAction;
}