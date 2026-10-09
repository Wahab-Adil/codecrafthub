package com.codecrafthub.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.codecrafthub.model.CourseStatus;

/**
 * Translates Java exceptions into clean HTTP error responses.
 *
 * <p>{@code @RestControllerAdvice} means "run these methods whenever a controller
 * throws". Without it, every controller method would need its own try/catch and the
 * client would get an ugly HTML error page or a stack trace. With it, the service
 * layer can simply throw and stay readable.
 *
 * <p>Each handler decides two things: the HTTP status code, and the JSON body
 * (always an {@link ApiError}).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * The resource the URL points at does not exist -> 404.
     * Example: GET /api/courses/999 when only three courses are stored.
     */
    @ExceptionHandler(CourseNotFoundException.class)
    public ResponseEntity<ApiError> handleCourseNotFound(CourseNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * The client sent a status we do not know -> 400.
     * Example: GET /api/courses?status=Finished
     */
    @ExceptionHandler(InvalidStatusException.class)
    public ResponseEntity<ApiError> handleInvalidStatus(InvalidStatusException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * Bean Validation rejected the request body -> 400, listing every problem field.
     * Example: POST /api/courses with {"name": "", "target_date": null}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ApiError body = new ApiError(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed for " + fieldErrors.size() + " field(s)",
                request.getRequestURI(),
                fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * The body was not readable JSON at all -> 400.
     * Common causes: a trailing comma, a wrong date format such as "31/12/2026",
     * or camelCase field names ("targetDate") where snake_case is expected.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        // The Jackson detail is useful for us while developing, but too technical for the client.
        log.warn("Unreadable request body for {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                "Request body could not be read as JSON. Check the syntax, use the snake_case field "
                        + "names (name, description, target_date, status), the date format yyyy-MM-dd, "
                        + "and one of these statuses: " + CourseStatus.allowedValues() + ".",
                request);
    }

    /**
     * A path variable could not be converted -> 400.
     * Example: GET /api/courses/abc, where {id} has to be a number.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Parameter '" + ex.getName() + "' must be a whole number, but was '" + ex.getValue() + "'",
                request);
    }

    /**
     * Something went wrong on our side of the JSON file -> 500.
     * The client is told the truth without exposing the stack trace.
     */
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiError> handleStorage(StorageException ex, HttpServletRequest request) {
        log.error("Course storage failure", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request);
    }

    /** Small helper so every handler builds its response the same way. */
    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        ApiError body = new ApiError(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
