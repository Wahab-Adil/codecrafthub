package com.codecrafthub.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import com.codecrafthub.exception.StorageException;
import com.codecrafthub.model.Course;
import com.codecrafthub.model.CourseStatus;

/**
 * Tests for the JSON file storage layer.
 *
 * <p>{@code @TempDir} gives every test method its own empty folder that JUnit
 * deletes afterwards, so these tests never touch your real courses.json and can run
 * in any order, in parallel, on any machine.
 *
 * <p>The mapper is built by hand with the same settings the application uses
 * (snake_case + indentation) because the point of this class is precisely what ends
 * up on disk.
 */
class JsonCourseRepositoryTest {

    @TempDir
    Path tempDir;

    private Path file;

    private JsonCourseRepository repository;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
        file = tempDir.resolve("courses.json");
        repository = new JsonCourseRepository(file.toString(), mapper);
        repository.createFileIfMissing();
    }

    @Test
    @DisplayName("the file is created as an empty JSON array if it does not exist")
    void createsAnEmptyFileWhenMissing() throws IOException {
        Path fresh = tempDir.resolve("nested/folder/new-courses.json");
        JsonCourseRepository freshRepository = new JsonCourseRepository(fresh.toString(), newMapper());

        freshRepository.createFileIfMissing();

        assertThat(Files.exists(fresh)).isTrue();
        assertThat(Files.readString(fresh)).isEqualTo("[]");
        assertThat(freshRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("saving new courses assigns increasing ids and a creation timestamp")
    void assignsIncreasingIds() {
        Course first = repository.save(newCourse("Python Basics"));
        Course second = repository.save(newCourse("Docker Deep Dive"));

        assertThat(first.getId()).isEqualTo(1L);
        assertThat(second.getId()).isEqualTo(2L);
        assertThat(first.getCreatedAt()).isNotNull();
        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("findById returns the stored course, or an empty Optional")
    void findByIdWorks() {
        long id = repository.save(newCourse("Kafka for Beginners")).getId();

        Optional<Course> found = repository.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Kafka for Beginners");
        assertThat(repository.findById(12345L)).isEmpty();
    }

    @Test
    @DisplayName("saving a course that already has an id replaces it instead of duplicating it")
    void saveUpdatesInPlace() {
        Course saved = repository.save(newCourse("Old title"));
        saved.setName("New title");
        saved.setStatus(CourseStatus.COMPLETED);

        repository.save(saved);

        List<Course> all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getId()).isEqualTo(saved.getId());
        assertThat(all.get(0).getName()).isEqualTo("New title");
        assertThat(all.get(0).getStatus()).isEqualTo(CourseStatus.COMPLETED);
    }

    @Test
    @DisplayName("deleteById reports whether something was actually removed")
    void deleteByIdRemovesTheCourse() {
        long id = repository.save(newCourse("Course to delete")).getId();
        repository.save(newCourse("Course to keep"));

        assertThat(repository.deleteById(id)).isTrue();
        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findById(id)).isEmpty();
        // Deleting the same id again is not an error here, it just returns false.
        assertThat(repository.deleteById(id)).isFalse();
    }

    @Test
    @DisplayName("courses are persisted to disk immediately, in snake_case JSON")
    void writesSnakeCaseJsonToDisk() throws IOException {
        Course course = newCourse("Spring Boot Basics");
        course.setStatus(CourseStatus.IN_PROGRESS);
        repository.save(course);

        String content = Files.readString(file);

        assertThat(content)
                .contains("\"target_date\"")
                .contains("\"created_at\"")
                .contains("\"status\"")
                .contains("In Progress")
                .doesNotContain("targetDate")
                .doesNotContain("createdAt");
        assertThat(repository.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("edits made to the file by hand are visible to the next request")
    void picksUpExternalChangesToTheFile() throws IOException {
        Files.writeString(file, """
                [ {
                  "id" : 99,
                  "name" : "Hand edited course",
                  "description" : "Added with a text editor",
                  "target_date" : "2026-12-01",
                  "status" : "Completed",
                  "created_at" : "2026-01-01 08:00:00"
                } ]
                """);

        List<Course> courses = repository.findAll();

        assertThat(courses).hasSize(1);
        assertThat(courses.get(0).getId()).isEqualTo(99L);
        assertThat(courses.get(0).getStatus()).isEqualTo(CourseStatus.COMPLETED);
        assertThat(courses.get(0).getTargetDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        // New ids continue after the highest id already in the file.
        assertThat(repository.save(newCourse("Added after the edit")).getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("a file that is not valid JSON produces a clear StorageException")
    void failsClearlyOnBrokenJson() throws IOException {
        Files.writeString(file, "sorry, this is not json");

        assertThatThrownBy(() -> repository.findAll())
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Could not read course file");
    }

    // ------------------------------------------------------------------

    private Course newCourse(String name) {
        Course course = new Course();
        course.setName(name);
        course.setDescription("A course used by the tests");
        course.setTargetDate(LocalDate.now().plusMonths(1));
        course.setStatus(CourseStatus.NOT_STARTED);
        return course;
    }

    private ObjectMapper newMapper() {
        return JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
    }
}
