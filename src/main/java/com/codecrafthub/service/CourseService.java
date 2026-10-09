package com.codecrafthub.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.codecrafthub.dto.CourseRequest;
import com.codecrafthub.dto.CourseStats;
import com.codecrafthub.exception.CourseNotFoundException;
import com.codecrafthub.model.Course;
import com.codecrafthub.model.CourseStatus;
import com.codecrafthub.repository.JsonCourseRepository;

/**
 * The business logic layer - the "what may happen" of the application.
 *
 * <p>Keeping this between the controller and the repository has a very practical
 * benefit while learning REST: the controller stays tiny (it only translates HTTP
 * into method calls and back) and this class can be tested without starting a web
 * server at all.
 *
 * <p>Rules implemented here:
 * <ul>
 *   <li>Asking for a course that does not exist is an error -> {@link CourseNotFoundException}.</li>
 *   <li>Ids and creation timestamps are assigned by the server, never taken from the request.</li>
 *   <li>A new course without an explicit status starts as "Not Started".</li>
 *   <li>Updating a course keeps its id and its {@code created_at}.</li>
 * </ul>
 *
 * <p>{@code @Service} registers this class as a Spring bean, so the controller can
 * simply ask for it in its constructor (constructor injection).
 */
@Service
public class CourseService {

    private final JsonCourseRepository repository;

    public CourseService(JsonCourseRepository repository) {
        this.repository = repository;
    }

    /** GET /api/courses - everything, in file order. */
    public List<Course> findAll() {
        return repository.findAll();
    }

    /** GET /api/courses?status=In%20Progress - only the courses in one state. */
    public List<Course> findAllByStatus(CourseStatus status) {
        return repository.findAll().stream()
                .filter(course -> course.getStatus() == status)
                .toList();
    }

    /**
     * GET /api/courses/{id}.
     *
     * @throws CourseNotFoundException when no course has this id (becomes HTTP 404)
     */
    public Course findById(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException(id));
    }

    /**
     * GET /api/courses/search?q=spring&status=In%20Progress.
     *
     * <p>Case-insensitive substring match against the name and the description.
     * A blank query matches everything, so {@code ?status=Completed} alone works too.
     */
    public List<Course> search(String query, CourseStatus status) {
        String needle = (query == null) ? "" : query.trim().toLowerCase(Locale.ROOT);

        return repository.findAll().stream()
                .filter(course -> status == null || course.getStatus() == status)
                .filter(course -> needle.isEmpty() || contains(course.getName(), needle)
                        || contains(course.getDescription(), needle))
                .toList();
    }

    /** POST /api/courses - adds a course and returns it including its new id. */
    public Course create(CourseRequest request) {
        Course course = new Course();
        // No setId(...) here on purpose: the repository assigns the next free id.
        applyRequest(course, request);
        if (course.getStatus() == null) {
            course.setStatus(CourseStatus.NOT_STARTED);
        }
        return repository.save(course);
    }

    /**
     * PUT /api/courses/{id} - replaces the editable fields of an existing course.
     *
     * @throws CourseNotFoundException when the id in the URL is unknown (HTTP 404)
     */
    public Course update(long id, CourseRequest request) {
        Course course = findById(id);   // 404 if it does not exist
        applyRequest(course, request);
        // id and createdAt are deliberately left untouched.
        return repository.save(course);
    }

    /**
     * DELETE /api/courses/{id}.
     *
     * @throws CourseNotFoundException when there was nothing to delete (HTTP 404)
     */
    public void delete(long id) {
        if (!repository.deleteById(id)) {
            throw new CourseNotFoundException(id);
        }
    }

    /** GET /api/courses/stats - a summary computed from the stored courses. */
    public CourseStats stats() {
        List<Course> courses = repository.findAll();
        LocalDate today = LocalDate.now();

        long total = courses.size();
        long notStarted = countWithStatus(courses, CourseStatus.NOT_STARTED);
        long inProgress = countWithStatus(courses, CourseStatus.IN_PROGRESS);
        long completed = countWithStatus(courses, CourseStatus.COMPLETED);

        long overdue = courses.stream()
                .filter(course -> course.getStatus() != CourseStatus.COMPLETED)
                .filter(course -> course.getTargetDate() != null)
                .filter(course -> course.getTargetDate().isBefore(today))
                .count();

        // One decimal place, and no division by zero when the list is empty.
        double completionPercentage = (total == 0)
                ? 0.0
                : Math.round((completed * 1000.0) / total) / 10.0;

        return new CourseStats(total, notStarted, inProgress, completed, completionPercentage, overdue);
    }

    // ------------------------------------------------------------------
    // Small private helpers
    // ------------------------------------------------------------------

    /** Copies the client-supplied fields onto a course. */
    private void applyRequest(Course course, CourseRequest request) {
        course.setName(request.name().trim());
        course.setDescription(request.description());
        course.setTargetDate(request.targetDate());
        course.setStatus(request.status());
    }

    private long countWithStatus(List<Course> courses, CourseStatus status) {
        return courses.stream()
                .filter(course -> course.getStatus() == status)
                .count();
    }

    private boolean contains(String text, String needle) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(needle);
    }
}
