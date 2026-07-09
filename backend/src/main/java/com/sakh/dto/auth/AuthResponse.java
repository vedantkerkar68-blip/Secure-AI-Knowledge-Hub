package com.sakh.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Response payload returned after successful authentication.
 */
@Getter
@Builder
@AllArgsConstructor
public class AuthResponse {

	private final String token;
	private final String tokenType;
	private final long expiresIn;
}
