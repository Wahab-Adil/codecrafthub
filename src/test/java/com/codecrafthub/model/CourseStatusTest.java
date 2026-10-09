package com.codecrafthub.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import com.codecrafthub.exception.InvalidStatusException;

/**
 * Unit tests for the status enum and for the JSON shape of a course.
 *
 * <p>No Spring context is started here - that keeps the tests in the millisecond
 * range. A mapper is built by hand with the same two settings the application uses
 * (snake_case + pretty printing), which is what makes the JSON assertions meaningful.
 */
class CourseStatusTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
    }

    @ParameterizedTest(name = "\"{0}\" is read as {1}")
    @CsvSource({
            "Not Started, NOT_STARTED",
            "not started, NOT_STARTED",
            "NOT_STARTED, NOT_STARTED",
            "not-started, NOT_STARTED",
            "In Progress, IN_PROGRESS",
            "in_progress, IN_PROGRESS",
            "Completed, COMPLETED",
            "completed, COMPLETED"
    })
    @DisplayName("parse() accepts labels, enum names, any casing, spaces, dashes and underscores")
    void parseAcceptsReasonableVariants(String input, CourseStatus expected) {
        assertThat(CourseStatus.parse(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "Finished", "done", "In Progresss", "1" })
    @DisplayName("parse() rejects unknown text with a message that lists the allowed values")
    void parseRejectsUnknownValues(String input) {
        assertThatThrownBy(() -> CourseStatus.parse(input))
                .isInstanceOf(InvalidStatusException.class)
                .hasMessageContaining(input)
                .hasMessageContaining("Not Started, In Progress, Completed");
    }

    @Test
    @DisplayName("parse() treats null and blank as 'no filter given'")
    void parseTreatsMissingValueAsNull() {
        assertThat(CourseStatus.parse(null)).isNull();
        assertThat(CourseStatus.parse("   ")).isNull();
    }

    @Test
    @DisplayName("a status is written to JSON as its friendly label")
    void statusIsSerializedAsLabel() {
        assertThat(mapper.writeValueAsString(CourseStatus.IN_PROGRESS)).isEqualTo("\"In Progress\"");
    }

    @Test
    @DisplayName("a status is read back from its friendly label")
    void statusIsDeserializedFromLabel() {
        assertThat(mapper.readValue("\"Not Started\"", CourseStatus.class)).isEqualTo(CourseStatus.NOT_STARTED);
        assertThat(mapper.readValue("\"COMPLETED\"", CourseStatus.class)).isEqualTo(CourseStatus.COMPLETED);
    }

    @Test
    @DisplayName("a course becomes exactly the JSON documented in the API spec")
    void courseIsSerializedWithSnakeCaseFields() {
        Course course = new Course(1L, "Python Basics", "Learn Python fundamentals",
                LocalDate.of(2026, 12, 31), CourseStatus.NOT_STARTED);
        course.setCreatedAt(LocalDateTime.of(2026, 10, 9, 10, 30, 0));

        String json = mapper.writeValueAsString(course);

        // Read the JSON back as a plain map so we can assert on the *field names*
        // themselves, without depending on how the pretty printer indents them.
        Map<String, Object> asMap = mapper.readValue(json, new TypeReference<Map<String, Object>>() {
        });

        assertThat(asMap)
                .containsOnlyKeys("id", "name", "description", "target_date", "status", "created_at");
        assertThat(asMap)
                .containsEntry("id", 1)
                .containsEntry("name", "Python Basics")
                .containsEntry("description", "Learn Python fundamentals")
                .containsEntry("target_date", "2026-12-31")
                .containsEntry("status", "Not Started")
                .containsEntry("created_at", "2026-10-09 10:30:00");
        // ... and never the camelCase Java field names.
        assertThat(json).doesNotContain("targetDate").doesNotContain("createdAt");
    }

    @Test
    @DisplayName("that same JSON can be read back into an equal course")
    void courseSurvivesARoundTrip() {
        String json = """
                {
                  "id" : 7,
                  "name" : "Docker Deep Dive",
                  "description" : "Containers in practice",
                  "target_date" : "2026-11-15",
                  "status" : "In Progress",
                  "created_at" : "2026-10-01 09:00:00"
                }
                """;

        Course course = mapper.readValue(json, Course.class);

        assertThat(course.getId()).isEqualTo(7L);
        assertThat(course.getName()).isEqualTo("Docker Deep Dive");
        assertThat(course.getTargetDate()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(course.getStatus()).isEqualTo(CourseStatus.IN_PROGRESS);
        assertThat(course.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 9, 0, 0));
    }
}
