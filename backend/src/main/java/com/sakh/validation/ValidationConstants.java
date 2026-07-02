package com.sakh.validation;

/**
 * Centralized validation constants used by request validation.
 */
public final class ValidationConstants {

	public static final int PASSWORD_MIN_LENGTH = 8;
	public static final int EMAIL_MAX_LENGTH = 255;
	public static final int FIRST_NAME_MAX_LENGTH = 100;
	public static final int LAST_NAME_MAX_LENGTH = 100;
	public static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";

	private ValidationConstants() {
	}
}
