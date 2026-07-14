package com.sakh.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ApiError {

    private final Instant timestamp;
    private final int status;
    private final String error;
    private final String message;
    private final String path;
    private final List<FieldError> fields;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class FieldError {
        private final String field;
        private final String message;
    }
}
