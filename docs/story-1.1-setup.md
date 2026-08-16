# Story 1.1 — Application Setup and Dependency Management

> As a developer, I want to configure and structure a Spring Boot project so that it
> runs efficiently across multiple environments.

## Acceptance criteria

- Spring Boot project initialized with required dependencies
- Profiles configured for dev, test, and prod
- Constructor-based dependency injection used consistently

## How it was achieved

**Project init & dependencies.** Standard Maven-based Spring Boot 4.1 (Java 25)
scaffold: Spring Web, Spring Data JPA, Spring Data Redis, Spring Security, Bean
Validation, springdoc-openapi, `com.auth0:java-jwt`, `spring-boot-starter-mail`, Lombok,
`spring-boot-devtools`. See `pom.xml` for the full dependency list.

**Environment profiles.** Originally the project had a single `application.yaml` with
`ddl-auto`/`show-sql` hardcoded — no profile separation existed. This was split into:
- `application.yaml` — the shared base every profile loads on top of (datasource, Redis,
  mail, JWT/security, Swagger config). Sets
  `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}` so an unset env var still
  defaults sensibly.
- `application-dev.yaml` — `ddl-auto: update`, `show-sql: true`, `DEBUG` logging for
  `amalitech.hospital.management`. Active by default.
- `application-prod.yaml` — `ddl-auto: validate` (schema must already match the
  entities; Hibernate never auto-migrates prod), `show-sql: false`, `WARN`/`INFO`
  logging.
- `application-test.yaml` — same `ddl-auto: validate` + quiet logging, but activated via
  `@ActiveProfiles("test")` on `HmsApplicationTests` rather than a second
  `application.yaml` on the test classpath. That second-file approach was considered and
  rejected: Spring resolves `classpath:/application.yaml` as a single resource, so a
  same-named file under `src/test/resources` would *shadow* (not merge with) the main
  `application.yaml` — the test run would lose the datasource/Redis/mail config entirely
  rather than just override `ddl-auto`/logging. `@ActiveProfiles` avoids that failure
  mode completely.

  Tests still hit the same real Postgres instance as dev (see CLAUDE.md — the Postgres
  port note), not an in-memory database — `FindUserDataAspect`'s native queries use
  Postgres-specific SQL (`ILIKE`), so swapping engines for tests would have been a much
  bigger, riskier change than this story called for.

**Constructor-based DI.** Every `@Service`/`@RestController`/`@Component` in the project
uses Lombok's `@RequiredArgsConstructor` on `private final` fields — Spring wires the
generated constructor automatically, so there isn't a single `@Autowired` field anywhere
in the codebase. The one recurring exception that still fits the same pattern: each
service that owns an AOP-driven method (see Story 2.2/5.1) self-injects its own proxy as
a `@Lazy private final XService self` constructor parameter, to route
self-invocation through the Spring AOP proxy instead of bypassing it.

## Where in the codebase

- `pom.xml` — dependencies.
- `src/main/resources/application.yaml` — shared base config.
- `src/main/resources/application-dev.yaml`, `application-prod.yaml`,
  `application-test.yaml` — per-profile overrides.
- `src/test/java/amalitech/hospital/management/HmsApplicationTests.java` —
  `@ActiveProfiles("test")`.
- Every class under `service/`, `controller/`, `config/` — `@RequiredArgsConstructor`.

## Verification

```bash
./mvnw test -Dtest=HmsApplicationTests
```
Watch the startup log for `The following 1 profile is active: "test"` — confirms the
test profile actually took effect, not just that a file with that name exists.

`./mvnw spring-boot:run` (no `SPRING_PROFILES_ACTIVE` set) should log `"dev"` as the
active profile instead.
