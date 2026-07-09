package com.sakh.dto.auth;

import com.sakh.validation.ValidationConstants;
import com.sakh.validation.ValidationMessages;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for user registration.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

	@NotBlank(message = ValidationMessages.FIRST_NAME_REQUIRED)
	@Size(max = ValidationConstants.FIRST_NAME_MAX_LENGTH)
	private String firstName;

	@NotBlank(message = ValidationMessages.LAST_NAME_REQUIRED)
	@Size(max = ValidationConstants.LAST_NAME_MAX_LENGTH)
	private String lastName;

	@NotBlank(message = ValidationMessages.EMAIL_REQUIRED)
	@Email(message = ValidationMessages.EMAIL_INVALID)
	@Size(max = ValidationConstants.EMAIL_MAX_LENGTH)
	private String email;

	@NotBlank(message = ValidationMessages.PASSWORD_REQUIRED)
	@Size(min = ValidationConstants.PASSWORD_MIN_LENGTH, message = ValidationMessages.PASSWORD_MIN_LENGTH)
	private String password;

	@NotNull(message = ValidationMessages.ROLE_REQUIRED)
	private Long roleId;

	private Long departmentId;
}
