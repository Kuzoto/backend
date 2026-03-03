# PersonaSpace API

Spring Boot 4.0.3 REST API for user authentication and profile management.
Java 21, Maven, PostgreSQL (prod), H2 (test).

## Tech Stack

- **Framework**: Spring Boot 4.0.3 (spring-boot-starter-webmvc, spring-boot-starter-data-jpa)
- **Database**: PostgreSQL (prod), H2 (test)
- **Auth**: JWT via JJWT 0.12.6, Spring Security (stateless sessions)
- **Validation**: Jakarta Validation (spring-boot-starter-validation)
- **Env Loading**: spring-dotenv 3.0.0 (`me.paulschwarz:spring-dotenv`)

## Project Structure

```
src/main/java/com/personalspace/api/
  controller/        REST controllers (@RestController, @RequestMapping)
  service/           Business logic (@Service, @Transactional)
  repository/        Spring Data JPA interfaces (JpaRepository)
  model/entity/      JPA entities (@Entity, lifecycle hooks)
  model/enums/       Enumerations (Role)
  dto/request/       Inbound DTOs (Java records, Jakarta validation)
  dto/response/      Outbound DTOs (Java records)
  security/          SecurityConfig, JwtAuthenticationFilter, CustomUserDetailsService
  exception/         @RestControllerAdvice handler + custom RuntimeException subclasses
```

## Build & Test Commands

```bash
mvn clean package          # Build + run all tests
mvn spring-boot:run        # Start dev server
mvn test                   # Run all tests
mvn test -Dtest=ClassName  # Run a single test class
```

## Configuration

### Environment Variables (.env)

| Variable      | Used For                         |
|---------------|----------------------------------|
| `DB_URI`      | `spring.datasource.url`          |
| `DB_NAME`     | `spring.datasource.username`     |
| `DB_PASSWORD` | `spring.datasource.password`     |
| `JWT_SECRET`  | HMAC signing key (min 32 chars)  |

- `application.properties` loads `.env` via `spring.config.import=optional:file:.env[.properties]`
- JWT access token TTL: 15 min (hardcoded); refresh token TTL: 7 days (hardcoded)

### Test Profile

- `src/test/resources/application-test.properties` overrides datasource to H2 in-memory
- Provides a hardcoded `jwt.secret` so tests don't need `.env`
- Uses `ddl-auto=create-drop`

## API Endpoints

All under `/api/auth`:

| Method | Path              | Auth Required | Description            |
|--------|-------------------|---------------|------------------------|
| POST   | `/api/auth/signup` | No           | Register a new user    |
| POST   | `/api/auth/login`  | No           | Login, get tokens      |
| POST   | `/api/auth/refresh`| No           | Refresh access token   |
| POST   | `/api/auth/logout` | No           | Invalidate refresh token|

Request/response DTOs: `dto/request/SignupRequest`, `LoginRequest`, `RefreshTokenRequest` -> `dto/response/AuthResponse`

## Key Files

| File | Purpose |
|------|---------|
| `controller/AuthController.java` | Auth endpoints, delegates to AuthService |
| `service/AuthService.java` | Signup, login, refresh, logout logic |
| `service/JwtService.java` | Token generation, validation, claim extraction |
| `security/SecurityConfig.java` | Filter chain, CORS, session policy |
| `security/JwtAuthenticationFilter.java` | Extracts and validates Bearer tokens per request |
| `exception/GlobalExceptionHandler.java` | Maps exceptions to HTTP status codes |
| `model/entity/User.java` | User entity with @PrePersist/@PreUpdate timestamps |
| `model/entity/RefreshToken.java` | Refresh token entity, ManyToOne to User |

## Additional Documentation

- [Architectural Patterns](.claude/docs/architectural_patterns.md) - Detailed patterns with file:line references
