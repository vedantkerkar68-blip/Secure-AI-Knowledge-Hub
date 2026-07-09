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
 * Request payload for user login.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

	@NotBlank(message = ValidationMessages.EMAIL_REQUIRED)
	@Email(message = ValidationMessages.EMAIL_INVALID)
	@Size(max = ValidationConstants.EMAIL_MAX_LENGTH)
	private String email;

	@NotBlank(message = ValidationMessages.PASSWORD_REQUIRED)
	@Size(min = ValidationConstants.PASSWORD_MIN_LENGTH, message = ValidationMessages.PASSWORD_MIN_LENGTH)
	private String password;
}
