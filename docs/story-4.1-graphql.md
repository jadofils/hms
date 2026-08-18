# Story 4.1 — GraphQL Integration

> As a frontend developer, I want to fetch data using GraphQL queries and mutations.

## Acceptance criteria

- GraphQL schema defined for core entities
- Queries and mutations implemented
- REST and GraphQL coexist without conflict

## How it was achieved

**A real bug fixed first, independent of any missing schema.** The dependency on the
classpath is `spring-boot-starter-graphql` — real **Spring for GraphQL** — but
`application.yaml`'s `graphql:` block used `graphql.tools.schema-location-pattern`/
`graphql.servlet.*` keys, which belong to a *different* library
(`graphql-java-kickstart`'s `graphql-spring-boot-starter`). Spring for GraphQL reads
`spring.graphql.*` instead, so those keys were silently ignored regardless of whether
schema files existed. Replaced with the real properties: `spring.graphql.path`,
`spring.graphql.schema.locations: classpath:graphql/`, `spring.graphql.graphiql.enabled:
true` (a free, zero-setup GraphiQL UI at `/graphiql` — Altair or any other GraphQL client
needs none of this bundling, just this reachable, introspectable `/graphql` endpoint).

**Schema split into 9 files under `src/main/resources/graphql/`, one per real FK-
dependency cluster** — mirroring this codebase's own `model`/`repository`/`service`/
`controller` package boundaries, not an arbitrary split:

| File | Types |
|---|---|
| `common.graphqls` | Scalars, root `Query`/`Mutation` stubs |
| `user.graphqls` | `User`, `Role`, `Permission` |
| `patient.graphqls` | `Patient` |
| `doctor.graphqls` | `Doctor`, `Department`, `DoctorSchedule` |
| `appointment.graphqls` | `Appointment` (the hub every clinical vertical hangs off) |
| `pharmacy.graphqls` | `Medication`, `MedicalInventory`, `Prescription`, `PrescriptionItem` |
| `lab.graphqls` | `LabOrder`, `LabResult` |
| `finance.graphqls` | `Invoice` |
| `notification.graphqls` | `Notification` |

Every file is parsed and merged into **one** `TypeDefinitionRegistry` automatically by
Spring for GraphQL's own auto-configuration — a cross-file type reference (e.g.
`appointment.graphqls`' `patient: Patient` field) resolves the same way a Java import
resolves a class from another package, no extra merge code needed. Join tables
(`UserRole`, `RolePermission`) surface as mutations (`assignRole`, `grantPermission`, ...)
rather than their own types, matching REST exactly (no `UserRoleController`/
`RolePermissionController` exists either).

**`config/graphql/GraphQlConfig`** registers the one thing the framework's schema-merge
doesn't cover: custom scalars. `Date`/`BigDecimal` reuse `graphql-java-extended-scalars`
(one new `pom.xml` dependency); `LocalDateTime`/`LocalTime` are hand-rolled
(`LocalDateTimeScalar`/`LocalTimeScalar`) because that library's own `DateTime`/`Time`
scalars only accept `OffsetDateTime`/`OffsetTime` — confirmed by inspecting their compiled
`Coercing` — while every date-time field in this domain (`appointmentDate`, `createdAt`,
`startTime`, ...) is a plain zone-less `LocalDateTime`/`LocalTime`.

**`resolvers/` package** (parallel to `controller/`) — one `@Controller` GraphQL resolver
per entity that already has a REST controller (16 total), each delegating to the exact
same `*Service` its REST counterpart calls, so business logic is never duplicated between
the two API surfaces. Nested object fields (`Appointment.patient`, `Prescription.items`,
`MedicalInventory.medication`, ...) are resolved lazily via `@SchemaMapping` methods that
call the owning domain's own service — e.g. `AppointmentResolver.patient` calls
`PatientService.getPatient`, giving GraphQL callers the real related object instead of
REST's pre-flattened `patientName` string.

**Sorting now has full REST parity too — it didn't at first.** Every listing resolver
originally hardcoded `PageRequest.of(page, size)` with no way to request an order at all,
even though every one of their REST counterparts already supported `?sort=property,direction`
— a real functional gap, not just a documentation one, since GraphQL had *no* argument for it
whatsoever. Fixed by adding an optional `sort: String` argument (same
`"property,direction"` format REST's own `?sort=` uses, e.g. `sort: "lastName,desc"`) to
every one of the 13 listing queries, parsed by one shared `utils/GraphQlPaging.of(page, size,
sort)` helper into a real `Pageable` — REST gets this parsing for free from Spring's own
`Pageable` argument-resolver, but a GraphQL resolver's `@Argument` values are just raw
strings/ints with no equivalent binding, so this one small utility does by hand what Spring
MVC does automatically for REST. No service-layer change was needed at all: every listing
service method already reads `Pageable.getSort()` (either to drive `@FindUserData`'s
whitelisted `ORDER BY`, or, for the plain-JPA listings, gets it applied by Hibernate
automatically) — wiring a real `Sort` into the `Pageable` GraphQL builds was the entire fix.
Every listing query's SDL docstring also names its sortable columns now, matching each
REST controller's own `@Parameter(name = "sort", description = ...)`.

**`@RequirePermission`/`AuthorizationAspect` do not apply to GraphQL** — that mechanism is
scoped to `@RestController` methods; GraphQL resolvers aren't reached through the same
handler-mapping pointcut. Authorization for GraphQL is intentionally out of scope for this
pass (`SecurityConfig` already permits all requests — see its own Javadoc) and documented
here so it isn't silently assumed covered.

## Where in the codebase

- `pom.xml` — `graphql-java-extended-scalars` dependency.
- `src/main/resources/application.yaml` — corrected `spring.graphql.*` block.
- `src/main/resources/graphql/*.graphqls` — the 9 schema files.
- `config/graphql/GraphQlConfig.java`, `LocalDateTimeScalar.java`, `LocalTimeScalar.java`.
- `resolvers/*.java` — 16 resolver classes.
- `src/test/java/.../resolvers/*ResolverTest.java` — one `@GraphQlTest` slice test per
  resolver (57 tests), mocking the underlying service the same way this project's other
  layer tests do.

## Verification

```bash
./mvnw clean verify
```
Full suite: 751 tests, 0 failures. Manually confirmed end-to-end:
- `POST /graphql` with an introspection query (`{ __schema { types { name } } }`) returns
  all 16 domain types plus the 4 custom scalars merged from all 9 schema files.
- `http://localhost:8080/graphiql` loads (200 after its redirect).
- A real `{ patients(page: 0, size: 5) { patientId firstName } }` query returns actual
  rows from the same Postgres database `GET /api/v1/patients` reads — confirming REST and
  GraphQL coexist without conflict, both reachable in the same running app against the
  same service/data layer.
