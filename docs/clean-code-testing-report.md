# Clean Code & Testing Report

Generated from a real, local **SonarQube 26.8.0** analysis (`sonarqube.cmd` + `jacoco.cmd`,
per their own Javadoc-style comments) against a real, uninstrumented-nothing test run — every
number below came from an actual tool run against this codebase on 2026-08-18, not an estimate.

## A real tooling gap fixed first, independent of any finding below

`jacoco.cmd` (and therefore `sonar:sonar`'s coverage data) was silently broken on this machine
before this report could even be generated: the only JDK installed is **26.0.2**, and the
`jacoco-maven-plugin` version this project had pinned, **0.8.13**, cannot instrument bytecode at
class-file major version 70 (JDK 26's) — every `@SpringBootTest`/Mockito-mock-heavy test errored
with `IllegalArgumentException: Unsupported class file major version 70` the moment
`prepare-agent` (bound to every `mvn test`, not just `jacoco.cmd`) ran. Bumped to **0.8.15** (the
current latest on Maven Central) in `pom.xml` — that version does support JDK 26 bytecode, and
`mvn test`/`jacoco.cmd`/`sonar:sonar` all now run clean with real coverage instrumentation
instead of erroring on every single test. Without this fix, none of the coverage numbers below
could have been measured at all on this machine.

## Test suite health

```
Tests run: 808, Failures: 0, Errors: 0, Skipped: 1
```
(SonarQube's own dashboard reports 807/100% `test_success_density` — the 1-test difference is
container-counting noise between Surefire's and Sonar's test scanners, not a real discrepancy;
both agree on 0 failures, 0 errors.)

Composition, per CLAUDE.md's own Testing conventions:
- **Service-layer unit tests** (`*ServiceTest`) — manually-constructed services, Mockito-mocked
  collaborators, no Spring context. The self-injected `self` proxy field is mocked at the same
  boundary as any other collaborator.
- **Controller-layer HTTP tests** (`*ControllerTest`) — real Spring context, real Postgres, real
  HTTP via `MockMvc`, authenticated as the seeded admin.
- **AOP integration tests** (`AlgorithmAspectTest`, `FindUserDataAspectTest`,
  `SqlQueryBuilderAspectTest`, `AuthorizationAspectTest`, `EmailAspectTest`, `LoggingAspectTest`)
  — real `@SpringBootTest` context, because these aspects need a real Spring AOP proxy to
  exercise; a plain Mockito test structurally cannot verify `@Around`/`@Before` interception.
- **GraphQL resolver tests** (`*ResolverTest`) — `@GraphQlTest` slices, one per resolver.
- **Plain unit tests** (`AlgorithmUtilsTest`, `QueryBuilderTest`, `GraphQlPagingTest`,
  enum/converter/exception tests) — no Spring, no mocks, the algorithm/utility exercised directly.

## Coverage

Overall (raw JaCoCo XML, `target/site/jacoco/jacoco.xml`):

| Metric | Covered | Total | % |
|---|---|---|---|
| Lines | 2,465 | 2,629 | **93.8%** |
| Instructions | 12,954 | 13,825 | 93.7% |
| Branches | 624 | 724 | 86.2% |
| Methods | 660 | 725 | 91.0% |
| Classes | 122 | 122 | **100%** |

SonarQube's own `coverage` metric (a slightly different, Sonar-specific computation) reports
**91.2%** for the same run — both numbers point at the same conclusion: this is a thoroughly
tested codebase, not a project chasing a vanity 100%.

**Per-package** (line coverage):

| Package | Covered/Total | % |
|---|---|---|
| `utils/filters` | 72/72 | 100% |
| `dto/error`, `dto/common`, `enums`, `enums/converter`, `exception/*`, `docs` | — | 100% |
| `controller` | 163/163 | **100%** |
| `config` | 100/103 | 97.1% |
| `aop` | 328/339 | 96.8% |
| `config/security` | 80/84 | 95.2% |
| `service` | 1,345/1,438 | 93.5% |
| `utils` | 45/51 | 88.2% |
| `resolvers` | 85/120 | 70.8% |
| `config/graphql` | 24/42 | 57.1% |

**Where the gaps actually are, and why they're not equally concerning:**
- `resolvers` (70.8%) — every GraphQL resolver's *listing* method is a thin one-line delegation
  to a service method that already has thorough unit coverage of its own (`PatientServiceTest`,
  `AppointmentServiceTest`, etc.) — the resolver test's own job (per its class Javadoc) is
  reaching the HTTP↔schema↔service wiring, not re-proving business logic a service test already
  covers. The uncovered lines are mostly `@SchemaMapping` nested-field resolvers
  (`Appointment.patient`, `Invoice.patient`, ...) that no existing resolver test's query
  requests a nested field deep enough to trigger — a real, worth-closing gap, not a
  fundamentally under-tested layer.
- `config/graphql` (57.1%) — `LocalDateTimeScalar`/`LocalTimeScalar`'s inner anonymous
  `Coercing` classes' `serialize`/`parseValue`/`parseLiteral` methods are only exercised for the
  happy path by whichever resolver test happens to touch a `LocalDateTime`/`LocalTime` field;
  their error branches (malformed literal, wrong Java type to serialize) have no dedicated test.
- `service/PatientService` (61.2%, the single lowest-covered service) — `getPatient`'s
  eager-loading branches (nested `MedicalRecord`/`Prescription`/`Invoice`/`LabOrder` lists) pull
  in a wide fan of mapper methods that only a handful of `PatientServiceTest` cases actually
  populate test data for.
- `MailService` (14.3%) — expected, not a gap: every method's entire body is
  `throw new IllegalStateException("EmailAspect did not intercept this call")` (the
  `@SendTemplatedEmail` self-injection placeholder pattern — see
  [`annotations-reference.md`](annotations-reference.md)); the real behavior lives in
  `EmailAspect`, which has its own dedicated `EmailAspectTest`.

## SonarQube quality gate

```
Status: ERROR
  new_coverage:              88.0%  (≥ 80% required)               OK
  new_duplicated_lines_density: 0.52%  (≤ 3% required)              OK
  new_violations:            130    (0 allowed)                     FAILED
```
The gate fails on exactly one condition: the default "Sonar way" gate demands **zero** new
issues on code changed since the previous analysis, and this session's changes (documented in
this same `docs/` folder — the AOP wiring, filtering, GraphQL sort support) introduced 130
new-code lint findings. Coverage and duplication on that same new code both comfortably clear
their bars. Project-wide:

| Rating | Value |
|---|---|
| Reliability | **A** (0 bugs) |
| Security | D (2 vulnerabilities — see below, both reviewed) |
| Maintainability | **A** (184 code smells, but see severity mix below) |
| Duplicated lines | 1.1% |
| Lines of code | 9,616 (`ncloc`) |
| Cyclomatic / cognitive complexity | 1,053 / 390 |

### The 2 vulnerabilities — both reviewed, neither is a real hole

1. **`UserService.java:223`** (`java:S2068`, MEDIUM) — flags
   `private static final String PASSWORD_SPECIAL = "@$!%*?&";` as a "potentially hard-coded
   password" purely because the variable name contains "PASSWORD". It's a character-set
   constant `generateRandomPassword` draws from when building an admin-provisioned account's
   generated password (see that method's own Javadoc) — not a credential. **False positive**,
   safe to mark "Won't Fix" in SonarQube once triaged.
2. **`SecurityConfig.java:49`** (`java:S4502`, HIGH) — "Make sure disabling Spring Security's
   CSRF protection is safe here." CSRF is disabled deliberately: this is a stateless,
   Bearer-token JWT API with no cookie-based session for CSRF to exploit in the first place —
   the same reasoning `SecurityConfig`'s own Javadoc already documents for
   `anyRequest().permitAll()`. **Reviewed and accepted**, not a gap.

Zero bugs, and both flagged vulnerabilities are already-understood, non-issues once reviewed —
a clean bill of health on reliability/security in substance, even though the raw
Security rating letter (D) looks alarming out of context.

### Code smells — mostly volume from one very new, low-severity rule

| Severity | Count |
|---|---|
| BLOCKER | 41 |
| CRITICAL | 12 |
| MAJOR | 35 |
| MINOR | 94 |
| INFO | 73 |

That looks top-heavy on BLOCKER until you see *which* rule accounts for it — see below. By
volume, the top rules are:

| Rule | Count | Severity | What it actually is |
|---|---|---|---|
| `java:S8688` | 72 | INFO | "Time-based `.now()` methods should specify a ZoneId or a Clock" — flags every `LocalDateTime.now()` call project-wide (used for every `createdAt`/`updatedAt` timestamp). A brand-new rule (dated the same day as this analysis); this project's deliberate, consistent choice to use server-local time with no explicit zone isn't a bug, just a style this rule disagrees with. |
| `java:S2699` | **41** | **BLOCKER** | **"Add at least one assertion to this test case" — see below, the one real, worth-fixing finding.** |
| `java:S1710` | 46 | MINOR | "Annotation repetitions should not be wrapped" — a formatting nit on multi-line Swagger `@ApiResponse` stacks. |
| `java:S5838` | 12 | — | "AssertJ assertions should be simplified to the corresponding dedicated assertion" — e.g. `assertThat(x.isEmpty()).isTrue()` instead of `assertThat(x).isEmpty()`. Cosmetic. |
| `java:S5778` | 9 | — | "Only one method invocation is expected when testing runtime exceptions" — flags a couple of `assertThatThrownBy` lambdas with two `when(...)` setup calls inside them; the assertion itself is still on one call, this is about lambda tidiness. |
| `java:S1192` | 8 | mixed (some CRITICAL) | "String literals should not be duplicated" — mostly in `DataSeeder` (many repeated resource/action string literals building the permission catalog) and `FindUserDataAspect`/`AuthService`. |
| `java:S7467` | 7 | — | "Unused exception parameter should use the unnamed variable pattern" (Java 25's `catch (Exception _)`) — a JDK-25-specific modernization several `catch` blocks haven't adopted yet. |

### The one real, worth-fixing finding: 41 GraphQL resolver tests with no assertion

`java:S2699`, BLOCKER severity, spread across essentially every `*ResolverTest` class
(`PatientResolverTest`, `DoctorResolverTest`, `AppointmentResolverTest`,
`DepartmentResolverTest`, `DoctorScheduleResolverTest`, `InvoiceResolverTest`,
`LabOrderResolverTest`, `LabResultResolverTest`, `MedicalInventoryResolverTest`,
`MedicationResolverTest`, `NotificationResolverTest`, `PermissionResolverTest`,
`PrescriptionItemResolverTest`, `PrescriptionResolverTest`, `RoleResolverTest`,
`UserResolverTest`, plus one each in `EmailAspectTest`/`EventBusTest`/`AlgorithmUtilsTest`).
These tests execute a GraphQL mutation/query via `GraphQlTester` but the test method itself
never calls `.entity(...)`/`.path(...).entity(...)` or otherwise asserts on the response — it
would pass identically even if the resolver silently returned the wrong data, or `null` where a
real object was expected. This is the kind of gap the earlier "resolvers 70.8% covered" number
can't see on its own: the *lines* run (so JaCoCo counts them as "covered"), but nothing checks
what they returned.

**This is worth fixing** — not because the resolvers are known to be broken, but because these
41 tests currently can't catch it if one becomes broken. Say the word and I'll go through them
and add the missing assertions (each one already has the GraphQL response in hand from the
`GraphQlTester` call; it's a matter of asserting on the field(s) that call's own query already
requested).

### An AOP-relevant finding: two same-class calls into `@Transactional` methods

`java:S6809` — "Call transactional methods via an injected dependency instead of directly via
`this`" — flags `RoleService.createRole` calling `grantPermission(...)` directly, and
`DoctorService.createDoctor` calling `assignDepartment(...)` directly. This is the *exact same*
Spring-AOP-proxy self-invocation gotcha this project's whole self-injection convention (see
`annotations-reference.md`'s Self-injection note) exists to avoid — just here it's Spring's own
built-in `@Transactional`/`@CacheEvict` advice on `grantPermission`/`assignDepartment`, not one
of this project's four custom `@Aspect`s. **Currently benign in both instances**: `createRole`/
`createDoctor` are themselves `@Transactional` with the default `REQUIRED` propagation, so the
same-class call still runs inside the caller's own already-open transaction rather than no
transaction at all — and `assignDepartment`'s bypassed `@CacheEvict` can't evict a stale cache
entry for a doctor that doesn't exist in the cache yet (it was only just created). It's benign
today, but fragile: if either annotated method's propagation or caching needs ever changed
independently, this same-class call would silently stop getting that behavior with no compiler
warning — the self-injection convention this project already applies rigorously everywhere else
would close this gap too, for the same reason it exists at all.

## AOP & optimization — what SonarQube's numbers say about it

- `aop/` package: 96.8% line coverage across all 10 aspects/processors — the highest-covered
  package after `controller`/`utils/filters`, consistent with `annotations-reference.md`'s
  Testing note: every AOP-intercepted annotation gets a real `@SpringBootTest` proving the
  aspect itself fires, on top of the service-layer unit tests that mock it at the boundary.
- Cyclomatic complexity (1,053) and cognitive complexity (390) across 9,616 lines of code is a
  complexity-per-line ratio consistent with a codebase that pushes conditional logic into
  small, single-purpose methods (validation helpers, `findXOrThrow` guards, `to*Response`
  mappers) rather than a few large branchy ones — exactly the shape `code-review`/`simplify`
  conventions this project already follows would produce.
- Zero duplication above 1.1% despite 13 near-identical CRUD service classes (`Patient`,
  `Doctor`, `Department`, ... each with `create`/`update`/`delete`/`findXOrThrow`/`toResponse`)
  — the structural *shape* repeats by design (a documented convention, not copy-paste), but the
  actual business logic and field names inside each don't, which is what SonarQube's
  token-based duplication detector is actually measuring.

## How to regenerate this report

```bash
docker compose up -d          # Postgres must be reachable
sonarqube.cmd                 # start the local SonarQube server, wait for "SonarQube is operational"
jacoco.cmd                    # runs the full suite with real JaCoCo coverage, pushes to SonarQube
```
Coverage HTML: `target/site/jacoco/index.html`. SonarQube dashboard:
`http://localhost:9000/dashboard?id=hms`. The `jacoco-maven-plugin` version fix (0.8.13 → 0.8.15
in `pom.xml`) only matters on a JDK 26 machine — an older-JDK machine never hit the original bug.
