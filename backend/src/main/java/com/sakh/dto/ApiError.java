package com.sakh.dto;

import java.time.Instant;

/**
 * Represents a standard API error response.
 */
public record ApiError(
		Instant timestamp,
		int status,
		String error,
		String message,
		String path) {
}
