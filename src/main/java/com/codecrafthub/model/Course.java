package com.codecrafthub.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * The thing we store: one course a developer wants to learn.
 *
 * <p>This is a plain old Java object (POJO) with private fields plus getters and
 * setters. Jackson turns it into JSON by looking at those getters, and rebuilds it
 * from JSON by calling the no-argument constructor followed by the setters.
 *
 * <p>With {@code spring.jackson.property-naming-strategy=SNAKE_CASE} (see
 * application.properties) this class is written to disk and to HTTP responses as:
 *
 * <pre>{@code
 * {
 *   "id" : 1,
 *   "name" : "Python Basics",
 *   "description" : "Learn Python fundamentals",
 *   "target_date" : "2026-12-31",
 *   "status" : "Not Started",
 *   "created_at" : "2026-10-09 10:30:00"
 * }
 * }</pre>
 */
public class Course {

    /** Primary key. Assigned by the application when the course is first saved. */
    private Long id;

    /** Short title, e.g. "Spring Boot Basics". Required. */
    private String name;

    /** Why this course matters / what it covers. Optional. */
    private String description;

    /** The day the learner wants to finish by. Written as yyyy-MM-dd. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate targetDate;

    /** Where the learner is right now: Not Started / In Progress / Completed. */
    private CourseStatus status;

    /** When the record was created. Set by the application, never by the client. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** No-argument constructor: Jackson needs it to create an empty object first. */
    public Course() {
    }

    /** Convenience constructor used by tests and by hand-written seed data. */
    public Course(Long id, String name, String description, LocalDate targetDate, CourseStatus status) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.targetDate = targetDate;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public CourseStatus getStatus() {
        return status;
    }

    public void setStatus(CourseStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Two courses are the same course when they have the same id.
     * Implementing equals/hashCode makes the tests readable:
     * {@code assertThat(saved).isEqualTo(expected)}.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        Course course = (Course) other;
        return id != null && id.equals(course.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Course{id=" + id
                + ", name='" + name + '\''
                + ", targetDate=" + targetDate
                + ", status=" + (status == null ? "null" : status.getLabel())
                + ", createdAt=" + createdAt
                + '}';
    }
}
