package com.forge.domain.exceptions;

/**
 * Exception thrown when domain validation fails.
 * This exception is considered a client error and should not trigger circuit breakers or retries.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
