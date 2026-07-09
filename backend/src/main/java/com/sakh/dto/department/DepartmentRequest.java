package com.sakh.dto.department;

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
public class DepartmentRequest {

    @NotBlank(message = ValidationMessages.FIRST_NAME_REQUIRED)
    @Size(max = ValidationConstants.FIRST_NAME_MAX_LENGTH)
    private String name;

    private String description;
}