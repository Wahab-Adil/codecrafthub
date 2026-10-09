# CodeCraftHub

A small **personalized learning platform**: a REST API that lets a developer track the
courses they want to learn, with a target completion date and a status.

Built with **Java 21** and **Spring Boot 4.1** as a learning project for REST API
basics. There is **no database** - everything is stored in one JSON text file
(`courses.json`) - and there is **no authentication**, so you can focus entirely on
how a REST API is put together.

```
POST /api/courses   {"name":"Spring Boot Basics","target_date":"2026-12-31"}
                    -> 201 Created + the stored course with its new id
GET  /api/courses   -> 200 with every course you are tracking
```

---

## 1. Requirements

| Tool | Version | Check with |
| --- | --- | --- |
| JDK | 17 or newer (21 LTS recommended) | `java -version` |
| Maven | 3.6.3 or newer | `mvn -version` |
| curl / any HTTP client | any | `curl --version` |

Spring Boot 4.1 requires Java 17+ and Maven 3.6.3+. Nothing else to install - the web
server (Tomcat) is embedded and starts with the app.

## 2. Run it

```bash
mvn spring-boot:run
```

The first start creates an empty `courses.json` next to `pom.xml`. Then:

```bash
curl http://localhost:8080/api/courses
# []
```

Other useful commands:

```bash
mvn test                 # run the whole test suite
mvn clean package        # build target/codecrafthub-0.0.1-SNAPSHOT.jar
java -jar target/codecrafthub-0.0.1-SNAPSHOT.jar   # run the packaged jar
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=9090   # different port
```

## 3. Project structure

```
codecrafthub/
├── pom.xml                                    # Maven build + dependencies
├── courses.json                               # the "database" (created on first run, git-ignored)
├── api-requests.http                          # every endpoint as a runnable request
├── README.md
└── src
    ├── main
    │   ├── java/com/codecrafthub
    │   │   ├── CodeCraftHubApplication.java   # entry point (@SpringBootApplication)
    │   │   ├── controller
    │   │   │   └── CourseController.java      # the REST endpoints (HTTP in, HTTP out)
    │   │   ├── service
    │   │   │   └── CourseService.java         # business rules
    │   │   ├── repository
    │   │   │   └── JsonCourseRepository.java  # reads/writes courses.json with Jackson
    │   │   ├── model
    │   │   │   ├── Course.java                # the data we store
    │   │   │   └── CourseStatus.java          # enum: Not Started / In Progress / Completed
    │   │   ├── dto
    │   │   │   ├── CourseRequest.java         # what a client may send (+ validation rules)
    │   │   │   └── CourseStats.java           # what GET /stats returns
    │   │   └── exception
    │   │       ├── GlobalExceptionHandler.java# turns exceptions into clean HTTP errors
    │   │       ├── ApiError.java              # the error response body
    │   │       ├── CourseNotFoundException.java
    │   │       ├── InvalidStatusException.java
    │   │       └── StorageException.java
    │   └── resources
    │       └── application.properties         # port, file location, JSON style
    └── test/java/com/codecrafthub
        ├── CodeCraftHubApplicationTests.java  # does the app start?
        ├── model/CourseStatusTest.java        # status parsing + JSON shape
        ├── repository/JsonCourseRepositoryTest.java  # file storage
        ├── service/CourseServiceTest.java     # business rules
        └── api/CourseApiIntegrationTest.java  # real HTTP calls against a running server
```

**Why these layers?** A request flows down and back up, and each layer has one job:

```
HTTP request
   -> CourseController   "translate HTTP into a method call"
   -> CourseService      "apply the rules, throw if something is wrong"
   -> JsonCourseRepository  "read/write the JSON file"
```

Because of that split you can test `CourseService` without a web server, and you can
replace the JSON file with a real database later by changing **one** class.

## 4. The data model

A course is stored like this in `courses.json`:

```json
[ {
  "id" : 1,
  "name" : "Python Basics",
  "description" : "Learn Python fundamentals",
  "target_date" : "2026-12-31",
  "status" : "Not Started",
  "created_at" : "2026-10-09 10:30:00"
} ]
```

| Field | Type | Set by | Notes |
| --- | --- | --- | --- |
| `id` | number | server | 1, 2, 3 ... assigned when the course is first saved |
| `name` | string | client | required, max 120 characters |
| `description` | string | client | optional, max 1000 characters |
| `target_date` | string | client | required, format `yyyy-MM-dd` |
| `status` | string | client | `Not Started`, `In Progress` or `Completed`; defaults to `Not Started` |
| `created_at` | string | server | format `yyyy-MM-dd HH:mm:ss`, never changed afterwards |

Java uses camelCase (`targetDate`) while JSON uses snake_case (`target_date`). One line
in `application.properties` does the translation for every class at once:

```properties
spring.jackson.property-naming-strategy=SNAKE_CASE
```

## 5. API reference

Base URL: `http://localhost:8080`

| Method | Path | What it does | Success | Errors |
| --- | --- | --- | --- | --- |
| GET | `/api/courses` | list all courses, optional `?status=` filter | 200 + array | 400 bad status |
| GET | `/api/courses/stats` | totals per status, % complete, overdue count | 200 + object | - |
| GET | `/api/courses/search` | `?q=text` in name/description, optional `?status=` | 200 + array | 400 bad status |
| GET | `/api/courses/{id}` | one course | 200 + object | 400 non-numeric id, 404 unknown id |
| POST | `/api/courses` | create a course | **201** + object + `Location` header | 400 invalid body |
| PUT | `/api/courses/{id}` | replace a course | 200 + object | 400 invalid body, 404 unknown id |
| DELETE | `/api/courses/{id}` | delete a course | **204**, no body | 404 unknown id |

Note that `/stats` and `/search` are literal paths, so Spring MVC always prefers them
over the `/{id}` pattern - `GET /api/courses/stats` is never mistaken for id = "stats".

### Create a course

```bash
curl -i -X POST http://localhost:8080/api/courses \
  -H "Content-Type: application/json" \
  -d '{
        "name": "Spring Boot Basics",
        "description": "Learn to build REST APIs with Spring Boot",
        "target_date": "2026-12-31",
        "status": "In Progress"
      }'
```

```
HTTP/1.1 201 Created
Location: http://localhost:8080/api/courses/1
Content-Type: application/json

{
  "id" : 1,
  "name" : "Spring Boot Basics",
  "description" : "Learn to build REST APIs with Spring Boot",
  "target_date" : "2026-12-31",
  "status" : "In Progress",
  "created_at" : "2026-10-09 10:30:00"
}
```

`status` may be omitted - the course then starts as `Not Started`. The value is
understood in several spellings: `In Progress`, `in progress`, `IN_PROGRESS`,
`in-progress`.

### List, filter and read

```bash
curl http://localhost:8080/api/courses
curl "http://localhost:8080/api/courses?status=In%20Progress"   # note the encoded space
curl http://localhost:8080/api/courses/1
```

### Search and stats

```bash
curl "http://localhost:8080/api/courses/search?q=spring"
curl "http://localhost:8080/api/courses/search?q=spring&status=Completed"
curl http://localhost:8080/api/courses/stats
```

```json
{
  "total" : 4,
  "not_started" : 1,
  "in_progress" : 2,
  "completed" : 1,
  "completion_percentage" : 25.0,
  "overdue" : 1
}
```

`overdue` counts courses that are not `Completed` whose `target_date` is already in
the past - the ones your learning plan should worry about.

### Update and delete

```bash
curl -X PUT http://localhost:8080/api/courses/1 \
  -H "Content-Type: application/json" \
  -d '{
        "name": "Spring Boot Basics",
        "description": "Finished the REST module",
        "target_date": "2026-12-31",
        "status": "Completed"
      }'

curl -i -X DELETE http://localhost:8080/api/courses/1     # -> 204 No Content
```

PUT is a *full* replacement of the editable fields: send the whole course. `id` and
`created_at` are preserved by the server whatever the body says.

### Error responses

Every failure returns the same shape, so a client always knows what to expect:

```json
{
  "timestamp" : "2026-10-09 10:35:12",
  "status" : 404,
  "error" : "Not Found",
  "message" : "Course with id 999 not found",
  "path" : "/api/courses/999"
}
```

Validation failures add a `field_errors` object:

```bash
curl -X POST http://localhost:8080/api/courses \
  -H "Content-Type: application/json" \
  -d '{"name": "  ", "target_date": null}'
```

```json
{
  "timestamp" : "2026-10-09 10:36:40",
  "status" : 400,
  "error" : "Bad Request",
  "message" : "Validation failed for 2 field(s)",
  "path" : "/api/courses",
  "field_errors" : {
    "name" : "name must not be empty",
    "targetDate" : "target_date must not be null (format: yyyy-MM-dd)"
  }
}
```

(The keys inside `field_errors` are the **Java** field names, because that is what Bean
Validation reports.)

## 6. How the JSON file storage works

`JsonCourseRepository` is the only class that touches the disk:

1. **On startup** (`@PostConstruct`) it creates `courses.json` containing `[]` if the
   file is missing, and creates any missing parent folders.
2. **On every read** it loads the file and parses it into a `List<Course>` with
   Jackson's `ObjectMapper`.
3. **On every write** it serialises the full list back to the file, overwriting it.
4. Mutating methods are `synchronized`, so two simultaneous requests cannot overwrite
   each other's change.

Consequences worth knowing:

* You can open `courses.json` in an editor, change it by hand, and the next request
  sees your edit - there is no cache to go stale. One of the tests proves this.
* If the file is not valid JSON you get a clear HTTP 500 explaining that the course
  file could not be read, instead of a stack trace.
* The location is configurable, which is also how the tests keep your real data safe:
  `codecrafthub.storage.file=/tmp/somewhere/courses.json`.

This design is deliberately the simplest thing that is correct. Writing the whole file
on every change is fine for dozens or hundreds of courses; it would not survive
thousands of concurrent users - that is exactly the problem a real database solves.

## 7. Tests

```bash
mvn test
```

| Test class | What it proves | Starts a server? |
| --- | --- | --- |
| `CodeCraftHubApplicationTests` | all beans wire up, the app boots | yes (no port) |
| `CourseStatusTest` | status text is parsed leniently; JSON uses `target_date`, `created_at` and `"In Progress"` | no |
| `JsonCourseRepositoryTest` | ids increase, updates replace instead of duplicate, deletes work, the file really contains snake_case JSON, broken JSON fails clearly | no |
| `CourseServiceTest` | defaults, trimming, 404 rules, filtering, searching, stats maths | no |
| `CourseApiIntegrationTest` | the real API over HTTP: status codes, `Location` header, error bodies, persistence | yes (random port) |

All tests write into a temporary folder or the system temp directory, so your
`courses.json` is never touched.

## 8. Notes on the versions used here

If you compare this project with online tutorials you will see two differences, both
because it uses **Spring Boot 4.1** (released after most tutorials were written):

1. **`spring-boot-starter-webmvc` instead of `spring-boot-starter-web`.** The old
   starter still exists in 4.1 but is deprecated. Both give you Spring MVC, embedded
   Tomcat and Jackson.
2. **Jackson 3, whose packages moved.** Core and databind are now `tools.jackson.*`
   (for example `tools.jackson.databind.ObjectMapper`), while the *annotations* kept
   their old home `com.fasterxml.jackson.annotation` (`@JsonValue`, `@JsonFormat`, ...).
   In Jackson 3 exceptions are unchecked, `java.time` support is built in (no
   `JavaTimeModule` to register), and `WRITE_DATES_AS_TIMESTAMPS` moved out of
   `SerializationFeature` into `DateTimeFeature` - so date formatting here is done
   explicitly with `@JsonFormat` patterns.

To build the same project on Spring Boot 3.5 instead: change the parent version, swap
`spring-boot-starter-webmvc` for `spring-boot-starter-web`, and change the two Jackson
imports (`tools.jackson.*` -> `com.fasterxml.jackson.*`).

## 9. Ideas for the next step

Good practice exercises, roughly in order of difficulty:

1. Add `PATCH /api/courses/{id}` that updates only the fields present in the body.
2. Add sorting and paging to the list: `?sort=target_date&order=asc&page=0&size=10`.
3. Reject a `target_date` in the past when creating a course (`@FutureOrPresent`).
4. Return RFC 9457 Problem Details (`org.springframework.http.ProblemDetail`) instead
   of the custom `ApiError` record.
5. Add a `GET /api/courses/upcoming` endpoint returning the next three deadlines.
6. Write the file atomically: write to `courses.json.tmp`, then `Files.move` it over
   the real file so a crash can never leave a half-written file behind.
7. Introduce a `CourseRepository` interface so a JPA/database implementation can be
   added next to the JSON one and selected with a Spring profile.
8. Add a tiny HTML/JavaScript front page under `src/main/resources/static` that calls
   this API with `fetch()`.
