package com.codecrafthub.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import com.codecrafthub.exception.InvalidStatusException;

/**
 * The three states a course can be in.
 *
 * <p>Using an enum instead of a plain {@code String} gives us two things for free:
 * <ol>
 *   <li>Impossible values cannot exist - a {@code Course} can never hold "In Progess".</li>
 *   <li>Anyone reading the code instantly sees the allowed values.</li>
 * </ol>
 *
 * <p>The API and the JSON file use the human friendly labels ("Not Started"), while
 * Java code uses the enum constants ({@code NOT_STARTED}). The two Jackson annotations
 * below are what translate between them:
 * <ul>
 *   <li>{@code @JsonValue} - when writing JSON, output {@link #getLabel()}.</li>
 *   <li>{@code @JsonCreator} - when reading JSON, run the string through
 *       {@link #parse(String)}.</li>
 * </ul>
 * (The annotations still live in the {@code com.fasterxml.jackson.annotation} package
 * even though Spring Boot 4 uses Jackson 3, whose core moved to {@code tools.jackson}.)
 */
public enum CourseStatus {

    NOT_STARTED("Not Started"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed");

    private final String label;

    CourseStatus(String label) {
        this.label = label;
    }

    /** The value that appears in JSON responses and in courses.json. */
    @JsonValue
    public String getLabel() {
        return label;
    }

    /** Called by Jackson when it reads a status out of a JSON request body. */
    @JsonCreator
    public static CourseStatus fromJson(String value) {
        return parse(value);
    }

    /**
     * Lenient conversion used by both Jackson and the controller (for query parameters
     * such as {@code ?status=In Progress}).
     *
     * <p>All of these are accepted, in any casing: "Not Started", "not started",
     * "NOT_STARTED", "not-started", "in_progress", "In Progress".
     *
     * @param value the raw text, may be {@code null} or blank
     * @return the matching status, or {@code null} when the input is {@code null}/blank
     * @throws InvalidStatusException when the text does not match any known status
     */
    public static CourseStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String wanted = normalize(value);
        for (CourseStatus status : values()) {
            if (normalize(status.label).equals(wanted) || normalize(status.name()).equals(wanted)) {
                return status;
            }
        }
        throw new InvalidStatusException(value, allowedValues());
    }

    /** Makes "NOT_STARTED", "not-started" and "Not Started" compare equal. */
    private static String normalize(String raw) {
        return raw.trim().replace('_', ' ').replace('-', ' ').toLowerCase(Locale.ROOT);
    }

    /** Every accepted label, used to build a helpful error message. */
    public static String allowedValues() {
        return Arrays.stream(values())
                .map(CourseStatus::getLabel)
                .collect(Collectors.joining(", "));
    }
}
