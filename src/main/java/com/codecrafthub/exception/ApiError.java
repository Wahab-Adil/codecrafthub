package com.codecrafthub.exception;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The single error shape every failed request returns, so a client always knows
 * what to expect:
 *
 * <pre>{@code
 * {
 *   "timestamp" : "2026-10-09 10:30:00",
 *   "status" : 404,
 *   "error" : "Not Found",
 *   "message" : "Course with id 7 not found",
 *   "path" : "/api/courses/7"
 * }
 * }</pre>
 *
 * <p>{@code field_errors} is only added for validation failures, which is why the
 * record is marked {@code @JsonInclude(NON_NULL)} - null fields are left out
 * instead of cluttering every error response.
 *
 * <p>Fun fact: the modern standard for this is RFC 9457 "Problem Details"
 * ({@code org.springframework.http.ProblemDetail}). This plain record is easier to
 * read while you are learning; switching later is a good exercise.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime timestamp,

        int status,

        String error,

        String message,

        String path,

        Map<String, String> fieldErrors) {

    /** Error without field details (404, 500, malformed JSON, ...). */
    public ApiError(int status, String error, String message, String path) {
        this(LocalDateTime.now(), status, error, message, path, null);
    }

    /** Error with per-field validation details (400). */
    public ApiError(int status, String error, String message, String path, Map<String, String> fieldErrors) {
        this(LocalDateTime.now(), status, error, message, path, fieldErrors);
    }
}
