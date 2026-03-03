# PersonaSpace API - Project Structure Guide

This guide walks through every folder and file in the project. It assumes no prior Spring Boot knowledge.

---

## What This Project Is

A REST API that handles user authentication — signing up, logging in, refreshing tokens, and logging out. It's built with:

- **Java 21** — the programming language
- **Spring Boot 4.0.3** — a framework that handles all the plumbing (HTTP server, database connections, dependency injection, security) so you can focus on business logic
- **Maven** — the build tool (like npm for Node.js). Defined in `pom.xml`
- **PostgreSQL** — the production database
- **JWT (JSON Web Tokens)** — for stateless authentication (no server-side sessions)

---

## How a Request Flows Through the App

When someone sends `POST /api/auth/login` with an email and password:

```
HTTP Request
     |
     v
JwtAuthenticationFilter          (security filter — checks for Bearer token)
     |
     v
SecurityConfig                   (is this endpoint public? /api/auth/** = yes)
     |
     v
AuthController.login()           (receives the JSON, validates it, calls service)
     |
     v
AuthService.login()              (verifies password, generates tokens)
     |        |
     v        v
UserRepository          JwtService             (database lookup + token creation)
     |
     v
PostgreSQL database
     |
     v
AuthResponse                     (JSON sent back: accessToken, refreshToken, etc.)
```

If anything goes wrong at any point, `GlobalExceptionHandler` catches the exception and returns a clean JSON error instead of a stack trace.

---

## Root Files

### `pom.xml`
Maven's configuration file — the equivalent of `package.json` in Node.js. It declares:
- **Parent**: Spring Boot 4.0.3 (provides default configurations and dependency versions)
- **Java version**: 21
- **Dependencies**: everything the app needs (see "Dependencies Explained" at the bottom)

You never edit this file day-to-day unless you're adding a new library.

### `application.properties` — `src/main/resources/application.properties`
Spring Boot's main configuration file. Spring reads this on startup. Key settings:
- **Database connection** — URL, username, password (pulled from environment variables via `${DB_URI}`, `${DB_NAME}`, `${DB_PASSWORD}`)
- **JPA/Hibernate** — `ddl-auto=update` means Hibernate will auto-create/modify database tables to match your entity classes
- **JWT settings** — the signing secret and token lifetimes (15 min for access tokens, 7 days for refresh tokens)

The line `spring.config.import=optional:file:.env[.properties]` loads your `.env` file so environment variables are available.

### `.env.example`
Template for the `.env` file you need to create locally. Contains: `DB_HOST`, `DB_PASSWORD`, `DB_USERNAME`, `JWT_SECRET`. The actual `.env` is gitignored.

### `ApiApplication.java` — `src/main/java/com/personalspace/api/ApiApplication.java`
The entry point. `@SpringBootApplication` tells Spring to scan this package and all sub-packages for components, then start the HTTP server. Think of it as `main()` — you run this to start the app.

---

## Source Code — `src/main/java/com/personalspace/api/`

### `controller/` — HTTP Endpoint Definitions

**What controllers do**: They receive HTTP requests, validate the input, call a service, and return an HTTP response. They don't contain business logic.

#### `AuthController.java`
The only controller. It defines four endpoints, all under `/api/auth`:

| Annotation | Method | What It Does |
|---|---|---|
| `@PostMapping("/signup")` | `signup()` | Takes a `SignupRequest`, returns 201 + `AuthResponse` |
| `@PostMapping("/login")` | `login()` | Takes a `LoginRequest`, returns 200 + `AuthResponse` |
| `@PostMapping("/refresh")` | `refresh()` | Takes a `RefreshTokenRequest`, returns 200 + `AuthResponse` |
| `@PostMapping("/logout")` | `logout()` | Takes a `RefreshTokenRequest`, returns 204 No Content |

Key annotations:
- `@RestController` — marks this class as an HTTP controller where every method returns JSON (not an HTML view)
- `@RequestMapping("/api/auth")` — all endpoints in this class start with `/api/auth`
- `@Valid` — triggers Jakarta validation on the request body (e.g., checks that email is not blank)
- `@RequestBody` — tells Spring to parse the incoming JSON body into the specified Java object

The controller has one dependency (`AuthService`) injected through its constructor. Spring automatically provides the right instance — this is **dependency injection**.

---

### `service/` — Business Logic

**What services do**: They contain the actual logic — checking if an email is taken, hashing passwords, generating tokens, etc. Controllers call services. Services call repositories.

#### `AuthService.java`
The main service. Contains the logic for all four auth operations:

- **`signup()`** — checks if email exists, creates a `User` entity, hashes the password with BCrypt, saves to DB, generates access + refresh tokens, returns `AuthResponse`
- **`login()`** — delegates password verification to Spring's `AuthenticationManager` (which uses `CustomUserDetailsService` + BCrypt behind the scenes), then generates tokens
- **`refresh()`** — looks up the refresh token in the DB, checks if it's expired, deletes the old one, creates a new one (this is called **token rotation** — a security best practice)
- **`logout()`** — simply deletes the refresh token from the DB

Key annotations:
- `@Service` — marks this class as a Spring-managed bean (Spring creates one instance and injects it wherever needed)
- `@Transactional` — wraps the method in a database transaction (if anything fails, all DB changes roll back)
- `@Value("${jwt.refresh-token-expiration}")` — injects a value from `application.properties` into the constructor parameter

#### `JwtService.java`
Handles JWT token operations using the JJWT library:

- **`generateAccessToken(email)`** — creates a signed JWT with the user's email as the subject, an issued-at time, and an expiration time
- **`extractEmail(token)`** — parses a JWT and pulls out the email
- **`isTokenValid(token)`** — tries to parse the token; returns `false` if it's expired, tampered with, or malformed
- **`extractAllClaims(token)`** — the private method that does the actual JWT parsing and signature verification

The signing key is created from the `jwt.secret` property using HMAC-SHA.

#### `UserService.java`
A simple service for looking up users by ID or email. Used outside the auth flow when other parts of the app need to fetch a user. Throws `ResourceNotFoundException` if not found.

---

### `repository/` — Database Access

**What repositories do**: They're interfaces that Spring Data JPA implements automatically at runtime. You declare a method name, and Spring generates the SQL query from it. No SQL writing needed.

#### `UserRepository.java`
```java
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```
- `JpaRepository<User, UUID>` — gives you `save()`, `findById()`, `delete()`, `findAll()`, etc. for free
- `findByEmail` — Spring sees "findBy**Email**" and generates `SELECT * FROM users WHERE email = ?`
- `existsByEmail` — generates `SELECT EXISTS(SELECT 1 FROM users WHERE email = ?)`

#### `RefreshTokenRepository.java`
```java
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUser_Id(UUID userId);
    void deleteByToken(String token);
}
```
- `deleteByUser_Id` — the underscore tells Spring to traverse the relationship: `RefreshToken.user.id`
- `deleteByToken` — deletes the row where the token column matches

---

### `model/entity/` — Database Tables as Java Classes

**What entities do**: Each entity class maps to a database table. Each field maps to a column. JPA (Java Persistence API) handles the translation between Java objects and database rows.

#### `User.java`
Maps to the `users` table. Fields:

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key, auto-generated |
| `email` | String | Unique, not null |
| `password` | String | BCrypt-hashed, not null |
| `name` | String | Not null |
| `role` | Role (enum) | Stored as a string (`"USER"`) |
| `createdAt` | Instant | Set automatically on creation |
| `updatedAt` | Instant | Set automatically on creation and every update |

Key annotations:
- `@Entity` — marks this class as a JPA entity (a database table)
- `@Table(name = "users")` — the actual table name in PostgreSQL
- `@Id` + `@GeneratedValue(strategy = GenerationType.UUID)` — primary key, auto-generated UUID
- `@Column(nullable = false, unique = true)` — database column constraints
- `@Enumerated(EnumType.STRING)` — stores the enum as its name (`"USER"`) rather than its ordinal (`0`)
- `@PrePersist` — method runs automatically before the first `save()` (sets `createdAt` and `updatedAt`)
- `@PreUpdate` — method runs automatically before every subsequent `save()` (updates `updatedAt`)

#### `RefreshToken.java`
Maps to the `refresh_tokens` table. Fields:

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key, auto-generated |
| `token` | String | The actual random token string, unique |
| `user` | User | Many-to-one relationship (foreign key to `users`) |
| `expiryDate` | Instant | When this token expires |

- `@ManyToOne(fetch = FetchType.LAZY)` — many refresh tokens can belong to one user. `LAZY` means the User object isn't loaded from the DB until you actually access it (performance optimization).
- `@JoinColumn(name = "user_id")` — the foreign key column name in the `refresh_tokens` table

### `model/enums/` — Enumerations

#### `Role.java`
Currently just has `USER`. Exists so roles can be expanded later (e.g., `ADMIN`).

---

### `dto/request/` — Incoming JSON Shapes

**What DTOs do**: Data Transfer Objects define the shape of JSON that the API accepts or returns. They're separate from entities so you never accidentally expose internal fields (like hashed passwords) to the client.

All request DTOs are Java **records** — an immutable class where you just declare the fields and Java auto-generates the constructor, getters, `equals()`, `hashCode()`, and `toString()`.

#### `SignupRequest.java`
```java
public record SignupRequest(
    @NotBlank String name,
    @Email String email,
    @Size(min = 8) String password
) {}
```
Validation annotations (from Jakarta Validation) are checked automatically when the controller uses `@Valid`. If validation fails, Spring throws `MethodArgumentNotValidException`, which `GlobalExceptionHandler` catches and returns as a 400 error with field-level error messages.

#### `LoginRequest.java`
Fields: `email` (`@Email`, `@NotBlank`), `password` (`@NotBlank`)

#### `RefreshTokenRequest.java`
Field: `refreshToken` (`@NotBlank`)

---

### `dto/response/` — Outgoing JSON Shapes

#### `AuthResponse.java`
Returned by signup, login, and refresh. Shape:
```json
{
  "accessToken": "eyJhbG...",
  "refreshToken": "550e8400-e29b...",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```
Has a convenience constructor that defaults `tokenType` to `"Bearer"` so callers only need to pass three arguments.

#### `ApiErrorResponse.java`
Returned by `GlobalExceptionHandler` on any error. Shape:
```json
{
  "status": 400,
  "message": "Validation failed",
  "errors": { "email": "Email must be valid" },
  "timestamp": "2026-03-02T..."
}
```
Has convenience constructors that auto-set `timestamp` to now and allow `errors` to be null (for non-validation errors).

---

### `security/` — Authentication and Authorization

#### `SecurityConfig.java`
The central security configuration. Defines:

1. **CSRF disabled** — not needed for a stateless REST API (CSRF protection is for browser-based session cookies)
2. **Stateless sessions** — Spring will never create an HTTP session. Every request must carry its own JWT
3. **Public endpoints** — everything under `/api/auth/**` is accessible without a token
4. **Protected endpoints** — everything else requires a valid JWT
5. **JWT filter** — `JwtAuthenticationFilter` is inserted into Spring's filter chain *before* the default username/password filter
6. **Password encoder** — BCrypt (industry standard for hashing passwords)
7. **Authentication provider** — connects `CustomUserDetailsService` (how to load users) with the password encoder (how to verify passwords)
8. **Authentication manager** — used by `AuthService.login()` to verify credentials

Annotations:
- `@Configuration` — this class provides Spring bean definitions
- `@EnableWebSecurity` — activates Spring Security
- `@Bean` — each method annotated with this returns an object that Spring manages and injects elsewhere

#### `JwtAuthenticationFilter.java`
Runs on **every HTTP request**. Its job:

1. Look for an `Authorization: Bearer <token>` header
2. If missing, skip and let the request continue (it might be hitting a public endpoint)
3. If present, validate the JWT using `JwtService`
4. If valid, load the user from the database via `CustomUserDetailsService`
5. Set the user's identity in Spring's `SecurityContext` — this is how downstream code knows who's making the request

Extends `OncePerRequestFilter` (guarantees it runs exactly once per request, even if the request is forwarded internally).

#### `CustomUserDetailsService.java`
Bridges your `User` entity with Spring Security's `UserDetails` interface. Spring Security doesn't know about your `User` class — it works with its own `UserDetails` type. This service:

1. Looks up a user by email using `UserRepository`
2. Converts your `User` into Spring Security's `User` object (with email as username, hashed password, and role as an authority)

This is called by both the `JwtAuthenticationFilter` (to populate the security context) and the `AuthenticationManager` (during login to verify the password).

---

### `exception/` — Error Handling

#### `GlobalExceptionHandler.java`
Annotated with `@RestControllerAdvice` — this tells Spring: "whenever *any* controller throws an exception, run it through these handler methods instead of returning a raw error."

| Exception | HTTP Status | When It Happens |
|---|---|---|
| `MethodArgumentNotValidException` | 400 Bad Request | A `@Valid` check fails (e.g., blank email) |
| `EmailAlreadyExistsException` | 409 Conflict | Signup with an already-registered email |
| `BadCredentialsException` | 401 Unauthorized | Login with wrong password |
| `RefreshTokenException` | 401 Unauthorized | Invalid or expired refresh token |
| `ResourceNotFoundException` | 404 Not Found | Entity not found in database |
| `Exception` (catch-all) | 500 Internal Server Error | Anything unexpected |

All handlers return an `ApiErrorResponse` for a consistent JSON error format.

#### Custom Exception Classes
- `EmailAlreadyExistsException` — thrown by `AuthService.signup()`
- `RefreshTokenException` — thrown by `AuthService.refresh()`
- `ResourceNotFoundException` — thrown by `UserService`

All three extend `RuntimeException`, so they don't need to be declared in method signatures.

---

## Test Code — `src/test/`

### `src/test/resources/application-test.properties`
Overrides the main config for tests:
- Swaps PostgreSQL for **H2** (an in-memory database that requires no setup)
- Uses `ddl-auto=create-drop` (creates tables on startup, drops them on shutdown — clean slate every test run)
- Provides a hardcoded `jwt.secret` so tests don't need a `.env` file

Activated by `@ActiveProfiles("test")` on test classes.

### Test Classes

#### `AuthControllerTest.java` — Controller/HTTP layer tests
Uses `@WebMvcTest` — loads *only* the web layer (controller + serialization), not the full application. The `AuthService` is replaced with a mock. Tests verify:
- Correct HTTP status codes (201, 200, 400, 409)
- Request validation works
- JSON request/response serialization

Uses `MockMvc` to simulate HTTP requests without starting a real server.

#### `AuthServiceTest.java` — Business logic tests
Uses `@ExtendWith(MockitoExtension.class)` — pure unit tests with no Spring context. All dependencies (repositories, password encoder, JWT service, auth manager) are mocked with Mockito. Tests verify:
- Signup creates a user and returns tokens
- Signup throws on duplicate email
- Login returns tokens for valid credentials

#### `JwtServiceTest.java` — JWT token tests
Plain JUnit 5, no Spring annotations. Constructs `JwtService` directly with a test secret. Tests verify token generation, email extraction, validation, and expiration detection.

#### `UserRepositoryTest.java` — Database query tests
Uses `@DataJpaTest` — loads only the JPA layer with an in-memory H2 database. Tests verify that `findByEmail()` and `existsByEmail()` actually generate correct SQL queries.

#### `ApiApplicationTests.java` — Smoke test
Uses `@SpringBootTest` — loads the entire application context. Just verifies the app starts without errors.

---

## Folder Structure at a Glance

```
api/
 |- pom.xml                          Build config + dependencies
 |- .env.example                     Template for environment variables
 |- src/
     |- main/
     |   |- java/com/personalspace/api/
     |   |   |- ApiApplication.java           Entry point
     |   |   |- controller/
     |   |   |   |- AuthController.java       HTTP endpoints
     |   |   |- service/
     |   |   |   |- AuthService.java          Auth business logic
     |   |   |   |- JwtService.java           Token generation/validation
     |   |   |   |- UserService.java          User lookups
     |   |   |- repository/
     |   |   |   |- UserRepository.java       User DB queries
     |   |   |   |- RefreshTokenRepository.java  Token DB queries
     |   |   |- model/
     |   |   |   |- entity/
     |   |   |   |   |- User.java             Users table
     |   |   |   |   |- RefreshToken.java     Refresh tokens table
     |   |   |   |- enums/
     |   |   |       |- Role.java             USER (expandable)
     |   |   |- dto/
     |   |   |   |- request/
     |   |   |   |   |- SignupRequest.java     Signup input shape
     |   |   |   |   |- LoginRequest.java      Login input shape
     |   |   |   |   |- RefreshTokenRequest.java  Refresh/logout input
     |   |   |   |- response/
     |   |   |       |- AuthResponse.java      Token response shape
     |   |   |       |- ApiErrorResponse.java  Error response shape
     |   |   |- security/
     |   |   |   |- SecurityConfig.java        Security rules + beans
     |   |   |   |- JwtAuthenticationFilter.java  Per-request token check
     |   |   |   |- CustomUserDetailsService.java  Loads users for Spring Security
     |   |   |- exception/
     |   |       |- GlobalExceptionHandler.java  Catches and formats errors
     |   |       |- EmailAlreadyExistsException.java
     |   |       |- RefreshTokenException.java
     |   |       |- ResourceNotFoundException.java
     |   |- resources/
     |       |- application.properties        App configuration
     |- test/
         |- java/com/personalspace/api/
         |   |- ApiApplicationTests.java      Smoke test
         |   |- controller/
         |   |   |- AuthControllerTest.java   HTTP layer tests
         |   |- service/
         |   |   |- AuthServiceTest.java      Business logic tests
         |   |   |- JwtServiceTest.java       Token tests
         |   |- repository/
         |       |- UserRepositoryTest.java   DB query tests
         |- resources/
             |- application-test.properties   H2 test database config
```

---

## Dependencies Explained (from `pom.xml`)

| Dependency | What It Does |
|---|---|
| `spring-boot-starter-webmvc` | HTTP server, REST controllers, JSON serialization |
| `spring-boot-starter-data-jpa` | Database access via JPA/Hibernate (ORM) |
| `spring-boot-starter-security` | Authentication, authorization, filter chains |
| `spring-boot-starter-validation` | `@NotBlank`, `@Email`, `@Size` etc. |
| `postgresql` | PostgreSQL database driver |
| `h2` (test only) | In-memory database for tests |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` | JWT token creation and parsing |
| `spring-dotenv` | Loads `.env` files into Spring's property system |
| `spring-boot-devtools` | Auto-restart on code changes during development |
| `spring-boot-starter-*-test` | Test utilities (MockMvc, DataJpaTest, etc.) |
| `spring-security-test` | Test utilities for security (mock users, CSRF helpers) |

---

## Key Spring Concepts Used in This Project

**Dependency Injection**: You never write `new AuthService(...)` yourself. You declare what a class needs in its constructor, and Spring provides (injects) those objects automatically. Every class annotated with `@Service`, `@Component`, `@RestController`, or `@Configuration` is managed by Spring.

**Beans**: Objects that Spring creates and manages. Defined either by class annotations (`@Service`, `@Component`) or by `@Bean` methods in `@Configuration` classes (see `SecurityConfig`).

**Annotations**: Most of Spring Boot's behavior is driven by annotations (the `@` things). They're metadata that tells the framework what to do with a class or method without you writing boilerplate code.

**Profiles**: Configuration variants. `application.properties` is the default. `application-test.properties` activates when a test class has `@ActiveProfiles("test")`. This is how tests use H2 instead of PostgreSQL.

**Filter Chain**: HTTP requests pass through a chain of filters before reaching your controller. `JwtAuthenticationFilter` is a custom filter inserted into this chain. Spring Security's filter chain handles auth decisions.
