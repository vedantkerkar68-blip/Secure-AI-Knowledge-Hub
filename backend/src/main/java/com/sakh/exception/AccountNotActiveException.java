package com.sakh.exception;

/**
 * Thrown when an authenticated action is attempted by a user whose account is
 * not in an active state (e.g. deactivated or locked).
 */
public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException(String message) {
        super(message);
    }
}