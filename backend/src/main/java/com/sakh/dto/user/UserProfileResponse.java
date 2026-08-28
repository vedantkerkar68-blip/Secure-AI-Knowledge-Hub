package com.sakh.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UserProfileResponse {

    private final Long id;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final Long roleId;
    private final String role;
    private final Long departmentId;
    private final String department;
    private final String status;
}