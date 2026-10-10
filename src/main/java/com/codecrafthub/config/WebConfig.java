package com.codecrafthub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cross-Origin Resource Sharing (CORS) settings for the API.
 *
 * <p><b>Why this class is needed.</b> The CodeCraftHub dashboard
 * ({@code index.html} in the project root) is usually opened with a live-reload web
 * server on its own port, for example http://localhost:5500, while the API runs on
 * http://localhost:8080. Browsers treat two different ports as two different
 * "origins", and by default they block JavaScript on one origin from calling an API
 * on another. Without CORS the dashboard loads fine but every fetch fails with a
 * message like:
 *
 * <pre>
 * Access to fetch at 'http://localhost:8080/api/courses' from origin
 * 'http://localhost:5500' has been blocked by CORS policy
 * </pre>
 *
 * <p>Registering the mapping below makes Spring MVC answer the browser's pre-flight
 * {@code OPTIONS} request and add {@code Access-Control-Allow-Origin} to real
 * responses, so the dashboard can create, read, update and delete courses.
 *
 * <p><b>Security note.</b> {@code allowedOrigins("*")} is deliberately wide open:
 * this project has no authentication and no sensitive data, and it is meant to run on
 * your own machine. A real deployment should list the exact front-end origins it
 * serves, for example {@code allowedOrigins("https://learn.example.com")}.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
