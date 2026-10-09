package com.codecrafthub.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.codecrafthub.exception.StorageException;
import com.codecrafthub.model.Course;

/**
 * Our "database": a single JSON text file.
 *
 * <p>This class is the only place in the whole application that touches the file
 * system. Everything above it (service, controller) just asks for courses and never
 * learns where or how they are stored - that separation is exactly what will let you
 * swap this file for a real database later by changing one class.
 *
 * <p><b>How storing works</b> (the simplest model that is still correct):
 * <ol>
 *   <li>Read: the file is parsed into a {@code List<Course>} at the start of every
 *       operation.</li>
 *   <li>Write: after any change the whole list is serialised back to the file.</li>
 * </ol>
 * Reading and writing a few kilobytes is instant at this scale, and there is no
 * in-memory cache that could drift out of sync with the file - you can open
 * courses.json, edit it by hand, and the next request sees your edit.
 *
 * <p><b>Why {@code synchronized}?</b> Two people saving at the same moment could
 * otherwise overwrite each other's change. The methods are synchronised on this bean
 * (there is exactly one instance), so file access is one-at-a-time.
 *
 * <p><b>Which ObjectMapper?</b> The one Spring Boot configured for us is injected.
 * It already knows about {@code snake_case} and pretty printing because of
 * application.properties, so the file we write looks exactly like the API responses.
 * (Spring Boot 4 ships Jackson 3, hence the {@code tools.jackson} imports.)
 */
@Repository
public class JsonCourseRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonCourseRepository.class);

    /** An empty JSON array - the content of a brand new courses.json. */
    private static final String EMPTY_FILE_CONTENT = "[]";

    private final Path file;

    private final ObjectMapper mapper;

    /**
     * @param location where the JSON file lives; read from
     *                 {@code codecrafthub.storage.file} with a sensible default so
     *                 the app still starts if the property is missing
     * @param mapper   Boot's pre-configured Jackson mapper
     */
    public JsonCourseRepository(@Value("${codecrafthub.storage.file:courses.json}") String location,
            ObjectMapper mapper) {
        this.file = Paths.get(location).toAbsolutePath().normalize();
        this.mapper = mapper;
    }

    /**
     * Runs once, right after Spring creates this bean and before the web server
     * accepts requests: make sure the file exists so the first GET does not fail.
     */
    @PostConstruct
    void createFileIfMissing() {
        try {
            if (Files.notExists(file)) {
                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(file, EMPTY_FILE_CONTENT,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                log.info("Created empty course file at {}", file);
            }
            else {
                log.info("Using course file at {}", file);
            }
        }
        catch (IOException ex) {
            throw StorageException.writeFailure(file, ex);
        }
    }

    /** Every stored course, in the order they appear in the file. */
    public synchronized List<Course> findAll() {
        return read();
    }

    /** One course by id, or an empty {@link Optional} if there is no such course. */
    public synchronized Optional<Course> findById(long id) {
        return read().stream()
                .filter(course -> Objects.equals(course.getId(), id))
                .findFirst();
    }

    /**
     * Inserts or updates one course and returns the stored version.
     *
     * <p>If {@code course.getId()} is null this is an insert: the next free id and
     * {@code createdAt} are assigned here, because ids and timestamps are the
     * server's business, not the client's. Otherwise the existing entry with the
     * same id is replaced.
     */
    public synchronized Course save(Course course) {
        List<Course> courses = read();

        if (course.getId() == null) {
            course.setId(nextId(courses));
            course.setCreatedAt(LocalDateTime.now());
            courses.add(course);
        }
        else {
            boolean replaced = false;
            for (int i = 0; i < courses.size(); i++) {
                if (Objects.equals(courses.get(i).getId(), course.getId())) {
                    courses.set(i, course);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                // An id was supplied for a course that is not in the file yet.
                courses.add(course);
            }
        }

        write(courses);
        return course;
    }

    /**
     * Deletes one course.
     *
     * @return true if a course was removed, false if the id was unknown (the caller
     *         turns that into HTTP 404)
     */
    public synchronized boolean deleteById(long id) {
        List<Course> courses = read();
        boolean removed = courses.removeIf(course -> Objects.equals(course.getId(), id));
        if (removed) {
            write(courses);
        }
        return removed;
    }

    /** True when no course is stored yet. */
    public synchronized boolean isEmpty() {
        return read().isEmpty();
    }

    /** Where the data lives - handy for the startup log and for tests. */
    public Path getFile() {
        return file;
    }

    // ------------------------------------------------------------------
    // File I/O - the only two methods that touch the disk
    // ------------------------------------------------------------------

    /**
     * Parses courses.json into a mutable list.
     * A missing or blank file simply means "no courses yet".
     */
    private List<Course> read() {
        try {
            if (Files.notExists(file)) {
                return new ArrayList<>();
            }
            String json = Files.readString(file);
            if (json.isBlank()) {
                return new ArrayList<>();
            }
            Course[] courses = mapper.readValue(json, Course[].class);
            // Arrays.asList(...) is fixed-size, and we need to add/remove later.
            return new ArrayList<>(Arrays.asList(courses));
        }
        catch (IOException ex) {
            throw StorageException.readFailure(file, ex);
        }
        catch (JacksonException ex) {
            // Invalid JSON in the file, e.g. after a hand edit that dropped a quote.
            throw StorageException.readFailure(file, ex);
        }
    }

    /** Serialises the full list back to courses.json, overwriting the old content. */
    private void write(List<Course> courses) {
        try {
            String json = mapper.writeValueAsString(courses);
            Files.writeString(file, json,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }
        catch (IOException ex) {
            throw StorageException.writeFailure(file, ex);
        }
        catch (JacksonException ex) {
            throw StorageException.writeFailure(file, ex);
        }
    }

    /**
     * Ids are simple increasing numbers: highest existing id + 1.
     * Deleted ids are not reused unless they were the highest one, which keeps the
     * file easy to read while you are learning.
     */
    private long nextId(List<Course> courses) {
        return courses.stream()
                .map(Course::getId)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L) + 1;
    }
}
