package com.codecrafthub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: can Spring Boot assemble the whole application?
 *
 * <p>It fails if a bean cannot be created - a wrong constructor, a missing
 * dependency, a typo in a property name. That is a very cheap way to catch the most
 * common beginner mistakes before ever sending an HTTP request.
 *
 * <p>The storage property points into the system temp folder so the test never
 * touches (or overwrites) your real courses.json.
 */
@SpringBootTest(properties = "codecrafthub.storage.file=${java.io.tmpdir}/codecrafthub-context-test/courses.json")
class CodeCraftHubApplicationTests {

    @Test
    void contextLoads() {
        // No assertion needed: the test passes if the application context starts.
    }
}
