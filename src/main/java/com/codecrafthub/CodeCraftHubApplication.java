package com.codecrafthub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of CodeCraftHub.
 *
 * <p>{@code @SpringBootApplication} is a shortcut that combines three annotations:
 * <ul>
 *   <li>{@code @SpringBootConfiguration} - this class holds configuration,</li>
 *   <li>{@code @EnableAutoConfiguration} - Spring Boot looks at the jars on the
 *       classpath and configures itself (here: an embedded Tomcat web server and
 *       a Jackson JSON mapper),</li>
 *   <li>{@code @ComponentScan} - every class annotated with {@code @RestController},
 *       {@code @Service} or {@code @Repository} in this package and below is found
 *       automatically and turned into a bean.</li>
 * </ul>
 *
 * <p>Run it with {@code mvn spring-boot:run}, then open http://localhost:8080/api/courses.
 */
@SpringBootApplication
public class CodeCraftHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeCraftHubApplication.class, args);
    }
}
