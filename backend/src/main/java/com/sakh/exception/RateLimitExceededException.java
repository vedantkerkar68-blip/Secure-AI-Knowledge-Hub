package com.sakh.exception;

/**
 * Thrown when a client exceeds the allowed request rate for an endpoint.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}