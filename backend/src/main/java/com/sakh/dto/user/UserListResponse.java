package com.sakh.dto.user;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UserListResponse {

    private final Long id;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String role;
    private final String department;
    private final String status;
}