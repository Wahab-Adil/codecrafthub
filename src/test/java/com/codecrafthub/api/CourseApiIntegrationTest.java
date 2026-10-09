package com.codecrafthub.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.codecrafthub.dto.CourseRequest;
import com.codecrafthub.dto.CourseStats;
import com.codecrafthub.model.Course;
import com.codecrafthub.model.CourseStatus;

/**
 * End-to-end test of the REST API.
 *
 * <p>{@code webEnvironment = RANDOM_PORT} starts the application for real - embedded
 * Tomcat, real controllers, real service, real JSON file - on a free port. The tests
 * then talk to it over HTTP with {@link RestClient}, exactly like curl or Postman
 * would. This is the test that proves the API works, not just that the classes
 * compile.
 *
 * <p>The storage property is redirected into the system temp folder so the tests never
 * read or overwrite your real courses.json, and the file is deleted before each test
 * so every test starts from an empty learning plan.
 *
 * <p>Request and response bodies are converted with the application's own
 * {@code ObjectMapper} (injected below). That mapper knows the snake_case convention,
 * so the tests exercise exactly the configuration a real client sees.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "codecrafthub.storage.file=${java.io.tmpdir}/codecrafthub-api-test/courses.json")
class CourseApiIntegrationTest {

    /** Must match the property above - used to check what really landed on disk. */
    private static final Path STORAGE_FILE =
            Path.of(System.getProperty("java.io.tmpdir"), "codecrafthub-api-test", "courses.json");

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private RestClient client;

    @BeforeEach
    void setUp() throws IOException {
        client = RestClient.create("http://localhost:" + port);
        Files.deleteIfExists(STORAGE_FILE);
    }

    // ------------------------------------------------------------------
    // GET /api/courses
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/courses returns an empty array when nothing is stored yet")
    void listingStartsEmpty() {
        assertThat(get("/api/courses", Course[].class)).isEmpty();
    }

    @Test
    @DisplayName("GET /api/courses?status=In Progress filters the list")
    void listCanBeFilteredByStatus() {
        create("A", CourseStatus.NOT_STARTED);
        create("B", CourseStatus.IN_PROGRESS);
        create("C", CourseStatus.IN_PROGRESS);

        // A space in a query parameter has to be percent-encoded. Building the URI
        // with the UriBuilder lets Spring do that encoding for us.
        String inProgressJson = client.get()
                .uri(uriBuilder -> uriBuilder.path("/api/courses").queryParam("status", "In Progress").build())
                .retrieve()
                .body(String.class);

        Course[] inProgress = readCourses(inProgressJson);
        assertThat(inProgress).hasSize(2);
        assertThat(inProgress).extracting(Course::getName).containsExactly("B", "C");

        // The enum constant spelling works too, in any casing.
        assertThat(get("/api/courses?status=NOT_STARTED", Course[].class)).hasSize(1);
        assertThat(get("/api/courses?status=completed", Course[].class)).isEmpty();
        // No parameter at all = everything.
        assertThat(get("/api/courses", Course[].class)).hasSize(3);
    }

    @Test
    @DisplayName("GET /api/courses?status=Finished returns 400 listing the allowed values")
    void listRejectsAnUnknownStatusFilter() {
        RestClientResponseException ex = assertThrows(RestClientResponseException.class,
                () -> client.get().uri("/api/courses?status=Finished").retrieve().toBodilessEntity());

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(readAsMap(ex.getResponseBodyAsString()).get("message")))
                .contains("Not Started, In Progress, Completed");
    }

    // ------------------------------------------------------------------
    // POST /api/courses
    // ------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/courses returns 201, a Location header and the stored course")
    void createReturns201WithALocationHeader() {
        ResponseEntity<String> response = client.post()
                .uri("/api/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestJson("Spring Boot Basics", "Learn to build REST APIs",
                        LocalDate.now().plusMonths(2), null))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString()).endsWith("/api/courses/1");

        String body = response.getBody();
        // The API speaks snake_case, as documented.
        assertThat(body).contains("\"target_date\"").contains("\"created_at\"");

        Course created = read(body, Course.class);
        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getName()).isEqualTo("Spring Boot Basics");
        assertThat(created.getDescription()).isEqualTo("Learn to build REST APIs");
        assertThat(created.getTargetDate()).isEqualTo(LocalDate.now().plusMonths(2));
        assertThat(created.getStatus()).isEqualTo(CourseStatus.NOT_STARTED);
        assertThat(created.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("POST /api/courses keeps the status when the client sends one")
    void createKeepsTheGivenStatus() {
        ResponseEntity<String> response = client.post()
                .uri("/api/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestJson("Kubernetes", null, LocalDate.now().plusWeeks(1), CourseStatus.IN_PROGRESS))
                .retrieve()
                .toEntity(String.class);

        assertThat(read(response.getBody(), Course.class).getStatus()).isEqualTo(CourseStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("POST /api/courses with a blank name and no date returns 400 naming both fields")
    void createRejectsAnInvalidBody() {
        RestClientResponseException ex = assertThrows(RestClientResponseException.class,
                () -> client.post()
                        .uri("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\": \"   \", \"target_date\": null}")
                        .retrieve()
                        .toBodilessEntity());

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> error = readAsMap(ex.getResponseBodyAsString());
        assertThat(error)
                .containsEntry("status", 400)
                .containsEntry("path", "/api/courses")
                .containsKey("field_errors");
        // Bean Validation reports the Java field names of the record, not the JSON names.
        assertThat(String.valueOf(error.get("field_errors"))).contains("name").contains("targetDate");

        // Nothing was written to disk.
        assertThat(get("/api/courses", Course[].class)).isEmpty();
    }

    @Test
    @DisplayName("POST /api/courses with an unknown status returns 400")
    void createRejectsAnUnknownStatus() {
        RestClientResponseException ex = assertThrows(RestClientResponseException.class,
                () -> client.post()
                        .uri("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\": \"Mystery\", \"target_date\": \"2026-12-31\", \"status\": \"Finished\"}")
                        .retrieve()
                        .toBodilessEntity());

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(readAsMap(ex.getResponseBodyAsString())).containsEntry("status", 400);
    }

    @Test
    @DisplayName("POST /api/courses with malformed JSON returns 400, not 500")
    void createRejectsMalformedJson() {
        RestClientResponseException ex = assertThrows(RestClientResponseException.class,
                () -> client.post()
                        .uri("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        // Unterminated object: guaranteed to be unparseable.
                        .body("{\"name\": \"broken\"")
                        .retrieve()
                        .toBodilessEntity());

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------------
    // GET /api/courses/{id}
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/courses/{id} returns that course")
    void getOneByIdWorks() {
        long id = create("Docker Deep Dive", CourseStatus.IN_PROGRESS).getId();

        Course found = get("/api/courses/" + id, Course.class);

        assertThat(found.getId()).isEqualTo(id);
        assertThat(found.getName()).isEqualTo("Docker Deep Dive");
        assertThat(found.getStatus()).isEqualTo(CourseStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("GET /api/courses/{id} returns 404 with a useful body for an unknown id")
    void getOneByIdReturns404WhenUnknown() {
        RestClientResponseException ex = assertThrows(RestClientResponseException.class,
                () -> client.get().uri("/api/courses/9999").retrieve().toBodilessEntity());

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(readAsMap(ex.getResponseBodyAsString()))
                .containsEntry("status", 404)
                .containsEntry("error", "Not Found")
                .containsEntry("message", "Course with id 9999 not found")
                .containsEntry("path", "/api/courses/9999");
    }

    @Test
    @DisplayName("GET /api/courses/abc returns 400 because the id must be a number")
    void getOneRejectsANonNumericId() {
        RestClientResponseException ex = assertThrows(RestClientResponseException.class,
                () -> client.get().uri("/api/courses/abc").retrieve().toBodilessEntity());

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(readAsMap(ex.getResponseBodyAsString())).containsEntry("status", 400);
    }

    // ------------------------------------------------------------------
    // PUT /api/courses/{id}
    // ------------------------------------------------------------------

    @Test
    @DisplayName("PUT /api/courses/{id} replaces the course and keeps its id")
    void updateWorks() {
        Course created = create("Old Title", CourseStatus.NOT_STARTED);
        LocalDate newDate = LocalDate.now().plusMonths(9);

        ResponseEntity<String> response = client.put()
                .uri("/api/courses/" + created.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestJson("New Title", "Updated description", newDate, CourseStatus.COMPLETED))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Course updated = read(response.getBody(), Course.class);
        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(updated.getName()).isEqualTo("New Title");
        assertThat(updated.getDescription()).isEqualTo("Updated description");
        assertThat(updated.getTargetDate()).isEqualTo(newDate);
        assertThat(updated.getStatus()).isEqualTo(CourseStatus.COMPLETED);
        assertThat(updated.getCreatedAt()).isNotNull();

        // Still exactly one course, and the change reached the file.
        assertThat(get("/api/courses", Course[].class)).hasSize(1);
        assertThat(get("/api/courses/" + created.getId(), Course.class).getName()).isEqualTo("New Title");
    }

    @Test
    @DisplayName("PUT /api/courses/{id} on an unknown id returns 404")
    void updateUnknownIdReturns404() {
        RestClientResponseException ex = assertThrows(RestClientResponseException.class,
                () -> client.put()
                        .uri("/api/courses/4242")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestJson("Anything", null, LocalDate.now(), null))
                        .retrieve()
                        .toBodilessEntity());

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------
    // DELETE /api/courses/{id}
    // ------------------------------------------------------------------

    @Test
    @DisplayName("DELETE /api/courses/{id} returns 204 and the course is gone")
    void deleteWorks() {
        long id = create("Course To Delete", null).getId();

        ResponseEntity<Void> response = client.delete()
                .uri("/api/courses/" + id)
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/api/courses", Course[].class)).isEmpty();

        // Deleting it a second time is a 404, not a silent success.
        RestClientResponseException ex = assertThrows(RestClientResponseException.class,
                () -> client.delete().uri("/api/courses/" + id).retrieve().toBodilessEntity());
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------
    // Searching and stats
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/courses/search?q=... matches name and description, case-insensitively")
    void searchWorks() {
        create("Spring Boot Basics", CourseStatus.IN_PROGRESS);
        create("Python for Data Science", CourseStatus.COMPLETED);

        assertThat(get("/api/courses/search?q=SPRING", Course[].class)).hasSize(1);
        assertThat(get("/api/courses/search?q=data", Course[].class)).hasSize(1);
        assertThat(get("/api/courses/search?q=kubernetes", Course[].class)).isEmpty();
        // Text and status can be combined; an empty q means "no text filter".
        assertThat(get("/api/courses/search?q=&status=Completed", Course[].class))
                .extracting(Course::getName)
                .containsExactly("Python for Data Science");
    }

    @Test
    @DisplayName("GET /api/courses/stats summarises the whole learning plan")
    void statsWorks() {
        create("Finished", CourseStatus.COMPLETED);
        create("Running", CourseStatus.IN_PROGRESS);
        create("Waiting", CourseStatus.NOT_STARTED);
        create("Also finished", CourseStatus.COMPLETED);

        CourseStats stats = get("/api/courses/stats", CourseStats.class);

        assertThat(stats.total()).isEqualTo(4);
        assertThat(stats.completed()).isEqualTo(2);
        assertThat(stats.inProgress()).isEqualTo(1);
        assertThat(stats.notStarted()).isEqualTo(1);
        assertThat(stats.completionPercentage()).isEqualTo(50.0);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Test
    @DisplayName("every change is really written to the JSON file on disk")
    void changesArePersistedToTheJsonFile() throws IOException {
        create("Persisted Course", CourseStatus.IN_PROGRESS);

        assertThat(Files.exists(STORAGE_FILE)).isTrue();
        String content = Files.readString(STORAGE_FILE);
        assertThat(content)
                .contains("Persisted Course")
                .contains("\"target_date\"")
                .contains("In Progress");
    }

    // ------------------------------------------------------------------
    // Helpers - keep the tests above short and readable
    // ------------------------------------------------------------------

    /** POSTs a course and returns it as the server stored it. */
    private Course create(String name, CourseStatus status) {
        ResponseEntity<String> response = client.post()
                .uri("/api/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestJson(name, "created by the test", LocalDate.now().plusMonths(1), status))
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return read(response.getBody(), Course.class);
    }

    /** Performs a GET and converts the body with the application's own mapper. */
    private <T> T get(String uri, Class<T> type) {
        String body = client.get().uri(uri).retrieve().body(String.class);
        return read(body, type);
    }

    private <T> T read(String json, Class<T> type) {
        return objectMapper.readValue(json, type);
    }

    private Course[] readCourses(String json) {
        return objectMapper.readValue(json, Course[].class);
    }

    private Map<String, Object> readAsMap(String json) {
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
        });
    }

    private String requestJson(String name, String description, LocalDate targetDate, CourseStatus status) {
        return objectMapper.writeValueAsString(new CourseRequest(name, description, targetDate, status));
    }
}
