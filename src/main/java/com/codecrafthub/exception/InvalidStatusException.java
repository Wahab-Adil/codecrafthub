package com.codecrafthub.exception;

/**
 * Thrown when a status value cannot be understood, for example
 * {@code GET /api/courses?status=Finished}.
 * {@link GlobalExceptionHandler} turns it into HTTP 400 Bad Request.
 *
 * <p>The list of allowed values is passed in by the caller instead of being looked up
 * here, so this package does not have to depend on the model package.
 */
public class InvalidStatusException extends RuntimeException {

    public InvalidStatusException(String value, String allowedValues) {
        super("Unknown status '" + value + "'. Allowed values: " + allowedValues);
    }
}
