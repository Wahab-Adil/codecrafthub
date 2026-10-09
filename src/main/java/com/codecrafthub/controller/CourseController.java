package com.codecrafthub.controller;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.codecrafthub.dto.CourseRequest;
import com.codecrafthub.dto.CourseStats;
import com.codecrafthub.model.Course;
import com.codecrafthub.model.CourseStatus;
import com.codecrafthub.service.CourseService;

/**
 * The REST API of CodeCraftHub.
 *
 * <p>{@code @RestController} = "every method returns data that is written straight
 * into the HTTP response body as JSON" (it is {@code @Controller} +
 * {@code @ResponseBody}). {@code @RequestMapping("/api/courses")} is the prefix
 * shared by all endpoints below.
 *
 * <p>The mapping annotations tie one HTTP verb + path to one Java method:
 * <pre>
 *   GET    /api/courses            -> getAllCourses     (list, optional ?status= filter)
 *   GET    /api/courses/stats      -> getStats          (summary counts)
 *   GET    /api/courses/search     -> search            (?q=text, optional ?status=)
 *   GET    /api/courses/{id}       -> getCourseById     (one course)
 *   POST   /api/courses            -> createCourse      (201 + Location header)
 *   PUT    /api/courses/{id}       -> updateCourse      (replace a course)
 *   DELETE /api/courses/{id}       -> deleteCourse      (204, no body)
 * </pre>
 *
 * <p>Note that {@code /stats} and {@code /search} are literal paths, so Spring MVC
 * always prefers them over the {@code /{id}} pattern - no special ordering needed.
 *
 * <p>Nothing here parses JSON, formats dates or builds error bodies: Spring's
 * message converters and {@code GlobalExceptionHandler} do that. A controller should
 * be boring.
 */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    /** Constructor injection: Spring hands us the one CourseService bean it created. */
    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    /**
     * List all courses.
     *
     * <p>The {@code status} parameter is optional; when present it narrows the list.
     * It is received as text and converted with {@link CourseStatus#parse(String)} so
     * that both {@code ?status=In Progress} and {@code ?status=IN_PROGRESS} work.
     *
     * @return 200 with a JSON array (an empty array when nothing is stored yet)
     */
    @GetMapping
    public List<Course> getAllCourses(@RequestParam(name = "status", required = false) String status) {
        CourseStatus wanted = CourseStatus.parse(status);
        return (wanted == null) ? courseService.findAll() : courseService.findAllByStatus(wanted);
    }

    /**
     * Summary of the whole learning plan.
     *
     * @return 200 with totals per status, completion percentage and overdue count
     */
    @GetMapping("/stats")
    public CourseStats getStats() {
        return courseService.stats();
    }

    /**
     * Search by text in the name or description.
     *
     * @param q      text to look for; optional, blank means "match everything"
     * @param status optional status filter, combinable with {@code q}
     * @return 200 with the matching courses
     */
    @GetMapping("/search")
    public List<Course> search(@RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(name = "status", required = false) String status) {
        return courseService.search(q, CourseStatus.parse(status));
    }

    /**
     * One course by id.
     *
     * @return 200 with the course, or 404 if the id is unknown
     */
    @GetMapping("/{id}")
    public Course getCourseById(@PathVariable long id) {
        return courseService.findById(id);
    }

    /**
     * Add a course.
     *
     * <p>{@code @Valid} runs the Bean Validation rules on {@link CourseRequest}
     * before the method body executes, so an empty name never reaches the file.
     *
     * <p>Returning {@code ResponseEntity} (instead of the object alone) lets us
     * choose the status code and add headers. A creation answers with
     * <b>201 Created</b> plus a {@code Location} header pointing at the new resource -
     * that is the REST convention, and it is why this method is a little longer.
     *
     * @return 201 with the stored course (id and created_at filled in) and a
     *         Location header such as http://localhost:8080/api/courses/1
     */
    @PostMapping
    public ResponseEntity<Course> createCourse(@Valid @RequestBody CourseRequest request) {
        Course created = courseService.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    /**
     * Replace an existing course.
     *
     * <p>PUT means "here is the full new state of this resource". The id in the URL
     * wins over anything in the body, and {@code created_at} is preserved.
     *
     * @return 200 with the updated course, 400 if the body is invalid,
     *         or 404 if the id is unknown
     */
    @PutMapping("/{id}")
    public Course updateCourse(@PathVariable long id, @Valid @RequestBody CourseRequest request) {
        return courseService.update(id, request);
    }

    /**
     * Delete a course.
     *
     * <p><b>204 No Content</b> is the correct answer to a successful delete: the
     * request worked and there is nothing left to send back.
     *
     * @return 204 with an empty body, or 404 if the id is unknown
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCourse(@PathVariable long id) {
        courseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
