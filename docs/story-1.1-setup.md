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

**Constructor-based DI — what's used, where, and why.** Every `@Service`/
`@RestController`/`@Component` in the project uses Lombok's `@RequiredArgsConstructor`
on `private final` fields — Spring wires the generated constructor automatically, so
there isn't a single `@Autowired` field anywhere in the codebase. Concretely:
```java
@Service
@RequiredArgsConstructor
public class DoctorService {
    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    // Lombok generates: DoctorService(DoctorRepository, DepartmentRepository) { ... }
    // Spring calls that generated constructor automatically — no @Autowired anywhere.
}
```
Every collaborator a service/controller needs — repositories, other services, Redis
templates, `PasswordEncoder`, `MailService`, etc. — arrives this same way, everywhere in
the codebase (`repository/` field-injects nothing; it's an interface Spring Data
implements, there's nothing *to* inject into it). `@Value`-injected config
(`app.frontend-base-url`, etc. in `UserService`) follows the identical shape: a
`@Value`-annotated `private final` field, populated through the same generated
constructor rather than a separate `@PostConstruct` setter.

### Three DI styles, and why this project picked constructor injection

Spring supports three ways to get a dependency into a bean; all three still exist to
compare against, even though only one is used here:

```java
// 1. Field injection — NOT used anywhere in this codebase
@Service
public class DoctorService {
    @Autowired
    private DoctorRepository doctorRepository; // package-private/no explicit constructor at all
}

// 2. Setter injection — NOT used anywhere in this codebase
@Service
public class DoctorService {
    private DoctorRepository doctorRepository;
    @Autowired
    public void setDoctorRepository(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }
}

// 3. Constructor injection — used everywhere in this codebase (via Lombok)
@Service
@RequiredArgsConstructor
public class DoctorService {
    private final DoctorRepository doctorRepository; // final — can only be set once, at construction
}
```

| | Field injection | Setter injection | Constructor injection (used here) |
|---|---|---|---|
| Can the field be `final`? | ❌ No — `@Autowired` sets it after construction | ❌ No — same reason | ✅ Yes — set once, in the constructor, then never reassignable |
| Works with a plain `new XService(...)` in a unit test, no Spring context? | ❌ No — the field stays `null` unless something calls a reflection-based setter (`ReflectionTestUtils`) or a real `ApplicationContext` runs | ⚠️ Technically yes, but only by remembering to call every setter manually | ✅ Yes — pass mocks straight into the constructor, exactly what every `*ServiceTest` in this project does (`new DoctorService(doctorRepository, departmentRepository, self)`) |
| Can a bean be constructed with a missing required dependency? | ❌ Fails silently until first *use* (a `NullPointerException` deep in some unrelated method, far from the actual cause) | ❌ Same failure mode as field injection if a setter call is ever missed | ✅ Fails immediately at bean creation (`BeanCreationException` naming the exact missing dependency) — impossible to construct a half-wired object at all |
| Makes a circular dependency between two beans obvious? | ❌ Masks it — Spring can often paper over a field-injection cycle by creating both beans first and wiring fields after, which just defers the real design problem | ❌ Same masking as field injection | ✅ Surfaces it immediately (a real, actionable startup error) — which is *exactly* what happened when `DoctorService self` was first added (see below), and constructor injection is what forced fixing it correctly with `@Lazy` rather than papering over it |
| Boilerplate | None visible (just an annotation on the field) | A getter/setter pair per dependency | None, thanks to Lombok's `@RequiredArgsConstructor` generating the constructor from the `final` fields |

Field and setter injection both make a class's dependencies *optional-looking* — nothing
stops constructing the object without them, the failure just shows up later, somewhere
else, as a null-pointer instead of a clear startup error. Constructor injection makes a
service's dependencies part of its actual type signature: a `DoctorService` cannot exist
at all without a `DoctorRepository`/`DepartmentRepository`, which is precisely the
guarantee this project's "manually-constructed services, no Spring context" unit-test
convention (see CLAUDE.md's Testing section) depends on — a test can `new` up a real
service instance with mocked collaborators and get the exact same object Spring would
have built, with zero framework machinery involved.

**The one wrinkle constructor injection surfaces that the other two styles would have
silently hidden: self-injection.** Every service that owns an `@ApplyAlgorithm`/
`@FindUserData`/`@SqlQueryBuilder`-annotated method needs to call that method *through
its own Spring AOP proxy*, not via a plain `this.method(...)` call (which bypasses the
proxy silently — see `annotations-reference.md`'s Self-injection section). The fix is a
constructor parameter of the bean's own type:
```java
@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;

    /** @Lazy breaks the circular dependency a self-reference field creates at bean-creation time. */
    @Lazy
    private final RoleService self;
}
```
Naively, `RoleService` depending on `RoleService` is a circular dependency — Spring can't
construct a bean that needs itself as an argument to its own constructor. `@Lazy` breaks
this: Spring injects a lazy proxy that only resolves the real `RoleService` bean the
first time a method is actually called on it (long after both objects already exist),
rather than eagerly during construction. This only works *because* the project already
committed to constructor injection everywhere — field/setter injection would have let a
circular self-reference compile and even run without error (Spring resolves
field-injection cycles more permissively), silently deferring the exact same underlying
problem to runtime instead of surfacing it as an immediate, fixable `BeanCurrentlyInCreationException`
the first time it was tried without `@Lazy`.

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
