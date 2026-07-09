package com.sakh.dto.user;

import com.sakh.validation.ValidationConstants;
import com.sakh.validation.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @NotBlank(message = ValidationMessages.FIRST_NAME_REQUIRED)
    @Size(max = ValidationConstants.FIRST_NAME_MAX_LENGTH)
    private String firstName;

    @NotBlank(message = ValidationMessages.LAST_NAME_REQUIRED)
    @Size(max = ValidationConstants.LAST_NAME_MAX_LENGTH)
    private String lastName;

    private Long departmentId;

    private String status;
}