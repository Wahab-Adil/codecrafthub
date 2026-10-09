package com.codecrafthub.exception;

import java.nio.file.Path;

/**
 * Thrown when the JSON file cannot be read or written (missing folder, no write
 * permission, hand-edited file that is no longer valid JSON, ...).
 * {@link GlobalExceptionHandler} turns it into HTTP 500 Internal Server Error.
 *
 * <p>Wrapping {@code IOException} in an unchecked exception keeps the service and
 * controller code clean - they do not have to declare {@code throws IOException}
 * for a problem they cannot fix anyway.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public static StorageException readFailure(Path file, Throwable cause) {
        return new StorageException("Could not read course file " + file
                + ". Check that it exists and contains a valid JSON array of courses.", cause);
    }

    public static StorageException writeFailure(Path file, Throwable cause) {
        return new StorageException("Could not write course file " + file
                + ". Check the folder exists and is writable.", cause);
    }
}
