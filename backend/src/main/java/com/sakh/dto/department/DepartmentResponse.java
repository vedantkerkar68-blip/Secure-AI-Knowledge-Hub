package com.sakh.dto.department;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class DepartmentResponse {

    private final Long id;
    private final String name;
    private final String description;
    private final Instant createdAt;
}