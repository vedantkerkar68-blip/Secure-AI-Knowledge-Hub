package com.sakh.exception;

/**
 * Exception thrown when a resource violates a uniqueness constraint.
 */
public class DuplicateResourceException extends RuntimeException {

	public DuplicateResourceException(String message) {
		super(message);
	}
}
