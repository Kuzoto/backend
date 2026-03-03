# Architectural Patterns

Detailed patterns observed across the PersonaSpace API codebase.

## Layered Architecture

Request flow: Controller -> Service -> Repository -> Entity

- `AuthController` (controller/AuthController.java:18) receives HTTP requests, validates with `@Valid`, delegates to `AuthService`
- `AuthService` (service/AuthService.java:25) orchestrates business logic, calls repositories and `JwtService`
- `UserRepository` (repository/UserRepository.java:8) and `RefreshTokenRepository` (repository/RefreshTokenRepository.java:8) handle persistence
- `User` (model/entity/User.java:10) and `RefreshToken` (model/entity/RefreshToken.java:9) are JPA entities

## Constructor Injection

All Spring-managed beans use constructor injection (no `@Autowired` field injection):

- `AuthController` — single dependency: `AuthService` (controller/AuthController.java:22)
- `AuthService` — five dependencies injected via constructor (service/AuthService.java:34-44)
- `JwtAuthenticationFilter` — `JwtService` + `UserDetailsService` (security/JwtAuthenticationFilter.java:23)
- `CustomUserDetailsService` — `UserRepository` (security/CustomUserDetailsService.java:18)
- `JwtService` — `@Value`-injected secret and expiration (service/JwtService.java:19)
- `UserService` — `UserRepository` (service/UserService.java:15)
- `SecurityConfig` — `JwtAuthenticationFilter` + `CustomUserDetailsService` (security/SecurityConfig.java:24)

## DTO Pattern

Separate `dto/request/` and `dto/response/` packages. All DTOs are Java records for immutability:

**Request records** (with Jakarta validation annotations):
- `SignupRequest` (dto/request/SignupRequest.java:7) — `@NotBlank` name, `@Email` email, `@Size(min=8)` password
- `LoginRequest` (dto/request/LoginRequest.java:6) — `@Email` email, `@NotBlank` password
- `RefreshTokenRequest` (dto/request/RefreshTokenRequest.java:5) — `@NotBlank` refreshToken

**Response records:**
- `AuthResponse` (dto/response/AuthResponse.java:3) — accessToken, refreshToken, tokenType (defaults "Bearer"), expiresIn
- `ApiErrorResponse` (dto/response/ApiErrorResponse.java:6) — status, message, errors map, timestamp

## Global Exception Handling

`@RestControllerAdvice` in `GlobalExceptionHandler` (exception/GlobalExceptionHandler.java:16) maps exceptions to HTTP responses:

| Exception | Status Code | Handler Line |
|-----------|-------------|-------------|
| `MethodArgumentNotValidException` | 400 BAD_REQUEST | :19 |
| `EmailAlreadyExistsException` | 409 CONFLICT | :30 |
| `BadCredentialsException` | 401 UNAUTHORIZED | :37 |
| `RefreshTokenException` | 401 UNAUTHORIZED | :44 |
| `ResourceNotFoundException` | 404 NOT_FOUND | :51 |
| `Exception` (catch-all) | 500 INTERNAL_SERVER_ERROR | :58 |

Custom exceptions extend `RuntimeException`:
- `EmailAlreadyExistsException` (exception/EmailAlreadyExistsException.java:3)
- `RefreshTokenException` (exception/RefreshTokenException.java:3)
- `ResourceNotFoundException` (exception/ResourceNotFoundException.java:3)

## JWT Security Filter Chain

Stateless authentication configured in `SecurityConfig` (security/SecurityConfig.java:31):

1. CSRF disabled (security/SecurityConfig.java:33)
2. Session management set to STATELESS (security/SecurityConfig.java:34)
3. `/api/auth/**` endpoints permitted without authentication (security/SecurityConfig.java:36)
4. All other requests require authentication (security/SecurityConfig.java:37)
5. `JwtAuthenticationFilter` added before `UsernamePasswordAuthenticationFilter` (security/SecurityConfig.java:40)

**Filter logic** (`JwtAuthenticationFilter.doFilterInternal` at security/JwtAuthenticationFilter.java:29):
1. Extract `Authorization` header (line 31)
2. Return early if missing or not "Bearer " prefix (lines 33-36)
3. Extract token by stripping "Bearer " prefix (line 38)
4. Validate token via `JwtService.isTokenValid()` (line 40)
5. Extract email, load `UserDetails`, set `SecurityContext` (lines 41-49)
6. Continue filter chain (line 52)

Password encoding: `BCryptPasswordEncoder` bean (security/SecurityConfig.java:53)

## Repository Conventions

Spring Data JPA query derivation — no custom `@Query` annotations:

**UserRepository** (repository/UserRepository.java:8):
- `findByEmail(String email)` returns `Optional<User>` (line 9)
- `existsByEmail(String email)` returns `boolean` (line 10)

**RefreshTokenRepository** (repository/RefreshTokenRepository.java:8):
- `findByToken(String token)` returns `Optional<RefreshToken>` (line 9)
- `deleteByUser_Id(UUID userId)` (line 10) — underscore notation for nested property
- `deleteByToken(String token)` (line 11)

## Test Strategy

Three testing patterns used across the project:

**Controller tests** — `@WebMvcTest` with MockMvc:
- `AuthControllerTest` (controller/AuthControllerTest.java:37) uses `@WebMvcTest(controllers = AuthController.class)` (line 27)
- Security auto-configuration excluded to test controller logic in isolation (lines 28-34)
- `@AutoConfigureMockMvc(addFilters = false)` disables security filters (line 35)
- `@ActiveProfiles("test")` (line 36)
- Dependencies mocked with `@MockitoBean` (line 42)

**Service tests** — `@ExtendWith(MockitoExtension.class)`:
- `AuthServiceTest` (service/AuthServiceTest.java:32) uses pure Mockito (line 31)
- `@Mock` on all dependencies (lines 34-47)
- Manual construction in `@BeforeEach` (lines 51-56)
- `JwtServiceTest` (service/JwtServiceTest.java:8) is plain JUnit 5 with direct construction

**Repository tests** — `@DataJpaTest`:
- `UserRepositoryTest` (repository/UserRepositoryTest.java:17) uses `@DataJpaTest` (line 15)
- `@ActiveProfiles("test")` activates H2 datasource (line 16)
- Tests query derivation methods against in-memory database

All test classes use `@ActiveProfiles("test")` to load `application-test.properties`.

## Entity Timestamps

`User` entity uses JPA lifecycle callbacks for automatic timestamps:

- `@PrePersist` on `onCreate()` (model/entity/User.java:35-39) — sets both `createdAt` and `updatedAt` to `Instant.now()`
- `@PreUpdate` on `onUpdate()` (model/entity/User.java:41-44) — updates only `updatedAt`

Note: `RefreshToken` entity does not use lifecycle callbacks; it has an `expiryDate` field set manually at creation time (service/AuthService.java:106).
