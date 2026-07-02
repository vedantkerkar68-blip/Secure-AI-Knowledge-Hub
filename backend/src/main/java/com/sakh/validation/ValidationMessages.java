package com.sakh.validation;

/**
 * Centralized validation messages used by request validation.
 */
public final class ValidationMessages {

	public static final String EMAIL_REQUIRED = "Email is required.";
	public static final String EMAIL_INVALID = "Email must be valid.";
	public static final String PASSWORD_REQUIRED = "Password is required.";
	public static final String PASSWORD_MIN_LENGTH = "Password must be at least 8 characters long.";
	public static final String FIRST_NAME_REQUIRED = "First name is required.";
	public static final String LAST_NAME_REQUIRED = "Last name is required.";
	public static final String ROLE_REQUIRED = "Role is required.";
	public static final String DEPARTMENT_REQUIRED = "Department is required.";

	private ValidationMessages() {
	}
}
