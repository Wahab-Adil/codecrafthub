package com.codecrafthub.exception;

/**
 * Thrown when a request refers to a course id that does not exist.
 * {@link GlobalExceptionHandler} turns it into HTTP 404 Not Found.
 */
public class CourseNotFoundException extends RuntimeException {

    public CourseNotFoundException(long id) {
        super("Course with id " + id + " not found");
    }
}
