package com.codecrafthub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import com.codecrafthub.dto.CourseRequest;
import com.codecrafthub.dto.CourseStats;
import com.codecrafthub.exception.CourseNotFoundException;
import com.codecrafthub.model.Course;
import com.codecrafthub.model.CourseStatus;
import com.codecrafthub.repository.JsonCourseRepository;

/**
 * Tests for the business rules in {@link CourseService}.
 *
 * <p>The service is used together with the real {@link JsonCourseRepository} writing
 * into a temporary folder. That keeps the test fast (no web server, no Spring
 * context) while still proving the whole "logic + storage" path works.
 */
class CourseServiceTest {

    @TempDir
    Path tempDir;

    private CourseService service;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
        JsonCourseRepository repository =
                new JsonCourseRepository(tempDir.resolve("courses.json").toString(), mapper);
        repository.createFileIfMissing();
        service = new CourseService(repository);
    }

    @Test
    @DisplayName("a new course gets an id, a timestamp and defaults to 'Not Started'")
    void createFillsInTheServerSideFields() {
        Course created = service.create(new CourseRequest(
                "Spring Boot Basics", "Learn to build REST APIs", LocalDate.now().plusMonths(2), null));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(CourseStatus.NOT_STARTED);
        assertThat(service.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("a status given in the request is kept")
    void createKeepsTheRequestedStatus() {
        Course created = service.create(new CourseRequest(
                "Kubernetes", null, LocalDate.now().plusWeeks(3), CourseStatus.IN_PROGRESS));

        assertThat(created.getStatus()).isEqualTo(CourseStatus.IN_PROGRESS);
        assertThat(created.getDescription()).isNull();
    }

    @Test
    @DisplayName("names are trimmed, so '  Docker  ' is stored as 'Docker'")
    void createTrimsTheName() {
        Course created = service.create(new CourseRequest(
                "  Docker  ", "Containers", LocalDate.now().plusMonths(1), null));

        assertThat(created.getName()).isEqualTo("Docker");
    }

    @Test
    @DisplayName("asking for a course that does not exist throws CourseNotFoundException")
    void findByIdThrowsForUnknownId() {
        assertThatThrownBy(() -> service.findById(7L))
                .isInstanceOf(CourseNotFoundException.class)
                .hasMessage("Course with id 7 not found");
    }

    @Test
    @DisplayName("findById returns exactly the stored course")
    void findByIdReturnsTheCourse() {
        long id = service.create(new CourseRequest("Terraform", "Infrastructure as code",
                LocalDate.now().plusMonths(1), CourseStatus.NOT_STARTED)).getId();

        Course found = service.findById(id);

        assertThat(found.getName()).isEqualTo("Terraform");
    }

    @Test
    @DisplayName("updating replaces the editable fields but keeps id and created_at")
    void updateKeepsIdAndCreatedAt() {
        Course created = service.create(new CourseRequest("Java Streams", "Old description",
                LocalDate.now().plusMonths(1), CourseStatus.NOT_STARTED));
        long id = created.getId();
        LocalDateTime createdAtBefore = service.findById(id).getCreatedAt();

        Course updated = service.update(id, new CourseRequest("Java Streams Deep Dive", "New description",
                LocalDate.now().plusMonths(6), CourseStatus.IN_PROGRESS));

        assertThat(updated.getId()).isEqualTo(id);
        assertThat(updated.getName()).isEqualTo("Java Streams Deep Dive");
        assertThat(updated.getDescription()).isEqualTo("New description");
        assertThat(updated.getTargetDate()).isEqualTo(LocalDate.now().plusMonths(6));
        assertThat(updated.getStatus()).isEqualTo(CourseStatus.IN_PROGRESS);
        assertThat(updated.getCreatedAt()).isEqualTo(createdAtBefore);
        assertThat(service.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("updating an unknown course throws CourseNotFoundException")
    void updateThrowsForUnknownId() {
        CourseRequest request = new CourseRequest("Anything", null, LocalDate.now(), null);

        assertThatThrownBy(() -> service.update(99L, request))
                .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    @DisplayName("deleting removes the course; deleting it twice throws")
    void deleteRemovesTheCourse() {
        long id = service.create(new CourseRequest("To delete", null, LocalDate.now(), null)).getId();

        service.delete(id);

        assertThat(service.findAll()).isEmpty();
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    @DisplayName("courses can be listed per status")
    void findAllByStatusFilters() {
        service.create(new CourseRequest("A", null, LocalDate.now(), CourseStatus.NOT_STARTED));
        service.create(new CourseRequest("B", null, LocalDate.now(), CourseStatus.IN_PROGRESS));
        service.create(new CourseRequest("C", null, LocalDate.now(), CourseStatus.IN_PROGRESS));

        List<Course> inProgress = service.findAllByStatus(CourseStatus.IN_PROGRESS);

        assertThat(inProgress).hasSize(2);
        assertThat(inProgress).extracting(Course::getName).containsExactly("B", "C");
        assertThat(service.findAllByStatus(CourseStatus.COMPLETED)).isEmpty();
    }

    @Test
    @DisplayName("search is case-insensitive and looks at name and description")
    void searchMatchesNameAndDescription() {
        service.create(new CourseRequest("Spring Boot Basics", "REST APIs with Java",
                LocalDate.now(), CourseStatus.IN_PROGRESS));
        service.create(new CourseRequest("Python for Data Science", "pandas and numpy",
                LocalDate.now(), CourseStatus.NOT_STARTED));

        assertThat(service.search("spring", null)).hasSize(1);
        assertThat(service.search("PYTHON", null)).hasSize(1);
        // Matches on the description, not the name:
        assertThat(service.search("pandas", null)).hasSize(1);
        // Blank query = no text filter:
        assertThat(service.search("   ", null)).hasSize(2);
        assertThat(service.search("kubernetes", null)).isEmpty();
    }

    @Test
    @DisplayName("search can combine text and status")
    void searchCombinesTextAndStatus() {
        service.create(new CourseRequest("Spring Boot Basics", "REST", LocalDate.now(),
                CourseStatus.IN_PROGRESS));
        service.create(new CourseRequest("Spring Security", "Auth", LocalDate.now(),
                CourseStatus.COMPLETED));

        assertThat(service.search("spring", CourseStatus.COMPLETED))
                .extracting(Course::getName)
                .containsExactly("Spring Security");
    }

    @Test
    @DisplayName("stats counts every status, the completion rate and overdue courses")
    void statsSummarisesTheLearningPlan() {
        LocalDate past = LocalDate.now().minusDays(10);
        LocalDate future = LocalDate.now().plusMonths(1);

        service.create(new CourseRequest("Finished one", null, past, CourseStatus.COMPLETED));
        service.create(new CourseRequest("Also finished", null, future, CourseStatus.COMPLETED));
        service.create(new CourseRequest("Late one", null, past, CourseStatus.IN_PROGRESS));
        service.create(new CourseRequest("Not touched yet", null, future, CourseStatus.NOT_STARTED));

        CourseStats stats = service.stats();

        assertThat(stats.total()).isEqualTo(4);
        assertThat(stats.completed()).isEqualTo(2);
        assertThat(stats.inProgress()).isEqualTo(1);
        assertThat(stats.notStarted()).isEqualTo(1);
        assertThat(stats.completionPercentage()).isEqualTo(50.0);
        // Only "Late one" is unfinished with a target date in the past.
        assertThat(stats.overdue()).isEqualTo(1);
    }

    @Test
    @DisplayName("stats of an empty plan is all zeros rather than a division by zero")
    void statsHandlesAnEmptyPlan() {
        CourseStats stats = service.stats();

        assertThat(stats.total()).isZero();
        assertThat(stats.completionPercentage()).isEqualTo(0.0);
        assertThat(stats.overdue()).isZero();
    }

    @Test
    @DisplayName("the completion percentage is rounded to one decimal")
    void statsRoundsThePercentage() {
        LocalDate date = LocalDate.now().plusMonths(1);
        service.create(new CourseRequest("One", null, date, CourseStatus.COMPLETED));
        service.create(new CourseRequest("Two", null, date, CourseStatus.NOT_STARTED));
        service.create(new CourseRequest("Three", null, date, CourseStatus.NOT_STARTED));

        // 1 of 3 = 33.333...% -> 33.3
        assertThat(service.stats().completionPercentage()).isEqualTo(33.3);
    }
}
