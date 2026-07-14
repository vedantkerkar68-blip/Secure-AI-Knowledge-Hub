package com.sakh.dto;

import com.sakh.enums.ActivityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
public class ActivityLogResponse {

    private final Long id;
    private final Long userId;
    private final String userEmail;
    private final ActivityType action;
    private final String resource;
    private final String ipAddress;
    private final Instant createdAt;
}
