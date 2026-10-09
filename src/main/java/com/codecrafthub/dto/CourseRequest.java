package com.codecrafthub.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.codecrafthub.model.CourseStatus;

/**
 * The request body for creating and updating a course.
 *
 * <p>Why not just accept a {@code Course}? Because a client must not be allowed to
 * choose the {@code id} or the {@code createdAt} of a record - those belong to the
 * server. A small "DTO" (data transfer object) describes exactly what a client is
 * allowed to send, and it is where the validation rules live.
 *
 * <p>A {@code record} is used because the request is only ever read, never modified.
 * The validation annotations are applied automatically because the controller
 * parameter is marked {@code @Valid}; a violation produces HTTP 400 with a helpful
 * message instead of a broken record in courses.json.
 *
 * <p>Example body (note the snake_case field names):
 * <pre>{@code
 * {
 *   "name": "Spring Boot Basics",
 *   "description": "Learn to build REST APIs with Spring Boot",
 *   "target_date": "2026-12-31",
 *   "status": "In Progress"
 * }
 * }</pre>
 *
 * @param name        course title, required, max 120 characters
 * @param description what the course covers, optional, max 1000 characters
 * @param targetDate  the day you want to finish by, required
 * @param status      optional - defaults to "Not Started" when creating a course
 */
public record CourseRequest(

        @NotBlank(message = "name must not be empty")
        @Size(max = 120, message = "name must be 120 characters or fewer")
        String name,

        @Size(max = 1000, message = "description must be 1000 characters or fewer")
        String description,

        @NotNull(message = "target_date must not be null (format: yyyy-MM-dd)")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate targetDate,

        CourseStatus status) {
}
