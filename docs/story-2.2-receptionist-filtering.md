# Story 2.2 — RESTful API Development (Receptionist view/sort/filter)

> As a receptionist, I want to view, sort, and filter patients and appointments.

## Acceptance criteria

- Pagination, sorting, and filtering supported
- Efficient retrieval algorithms applied
- Performance documented and analyzed

## How it was achieved

**The pattern (established pre-existing for `User`, extended to `Patient`/`Doctor`/
`Appointment`).** Rather than `repository.findAll(pageable)`, listing goes through a
custom `@FindUserData`-annotated method — an AOP-driven mechanism that builds and runs a
native SQL query via `aop/FindUserDataAspect.java`'s fluent `utils/filters/QueryBuilder`,
per the project's DSA-integration requirement (sorting/searching/pagination
"algorithms", not just a framework convenience call). The shape is always the same:

```java
@Lazy private final XService self;   // self-injected proxy — see Story 5.1's Javadoc note

public PagedModel<XResponse> getXs(Pageable pageable, /* optional filters */) {
    Sort.Order order = pageable.getSort().stream().findFirst().orElse(null);
    String sortBy = order != null ? order.getProperty() : null;
    String sortDir = order != null ? order.getDirection().name() : null;
    PagedRawResult raw = self.findXsPage(pageable.getPageNumber(), pageable.getPageSize(), sortBy, sortDir, /* filters */);
    // map raw Object[] rows -> XResponse
}

@FindUserData(domain = "x")
public PagedRawResult findXsPage(int page, int size, String sortBy, String sortDir /*, filters */) {
    throw new IllegalStateException("FindUserDataAspect did not intercept this call");
}
```
`self.findXsPage(...)` (never `this.findXsPage(...)`) is required because Spring AOP
proxies only intercept calls made *through* the proxy — a same-class call bypasses it
silently. `page`/`size`/`sortBy`/`sortDir` (and any filter values) are read off the
*method's actual runtime arguments* via `ProceedingJoinPoint.getArgs()`, since annotation
attributes are fixed at compile time and can't carry something only known at request time.

**Sorting.** `FindUserDataAspect.resolveSortColumn` whitelists a caller-supplied
`sortBy` against that domain's own `SELECT` list (`sortableColumnsFor`) — an
unrecognized or missing column falls back to the first selected column rather than
leaving the query unordered (Postgres gives no row-order guarantee at all without an
`ORDER BY`). This is what makes it safe to take `?sort=property,direction` straight off
a request without a bespoke param or manual validation.

**Filtering.** Patient adds an optional `status`/`gender` filter; Appointment adds an
optional `status` filter. Both are validated against their enum's own allowed values
*before* the value ever reaches the query (`PatientStatus.fromDbValue`/`Gender.fromDbValue`/
`AppointmentStatus.fromDbValue` — throws → clean `400 BadRequestException`), so only an
already-validated `dbValue` is ever concatenated into the SQL — the same safety principle
the sort-column whitelist already relies on. This required extending
`FindUserDataAspect.executeFindUserData` to read two more optional trailing string
arguments (`filter1`/`filter2`) off the method call, threaded through to `buildQuery`.

**Doctor & Appointment schema fixes.** Before wiring these two domains, two pre-existing
(but never-exercised) cases in `FindUserDataAspect` were actually broken against the real
schema and had to be fixed first: `"doctor"` referenced a nonexistent `doctor_department`
table and a `users` join Doctor doesn't have; `"appointment"` joined `users u ON
u.user_id = a.patient_id`, which is nonsensical (`patient_id` FKs `patients`, not
`users`). Both are fixed to join the real tables (`"doctor"` was simplified to drop the
department join entirely — Doctor↔Department is many-to-many, so joining it into a
paginated listing would fan a doctor with N departments out into N duplicate rows,
breaking both pagination and its `COUNT(*)`; `"appointment"` now joins `patients`/
`doctors`, both safe single-valued FKs with no fan-out risk).

**Appointments didn't exist at all.** The receptionist story explicitly asks for
sortable/filterable *appointments*, but no `Appointment` repository/service/controller
existed anywhere before this pass — it was built as a full vertical (see
`AppointmentService`/`AppointmentController`) specifically to close this half of the story.

**"Efficient retrieval algorithms."** Two custom `@Aspect`-driven mechanisms exist
project-wide for this: `@FindUserData` (above) for pagination/sort/filter, and
`@ApplyAlgorithm("mergeSort"|"binarySearch")` (`aop/AlgorithmAspect.java`) for in-memory
sort/search over an already-loaded collection (used by `RoleService.getRolePermissions`
via `RoleService.sort`). Patient/Doctor/Appointment listing uses the DB-level
`@FindUserData` path rather than `@ApplyAlgorithm`, since the whole point of paginating
at the database is to avoid loading more rows into memory than the current page needs —
`@ApplyAlgorithm` is for the opposite situation (a collection you've already loaded and
now need to sort/search without another round-trip).

**"Performance documented and analyzed" — see [`performance-report.md`](performance-report.md).**
The README's own Deliverables table names this "REST vs GraphQL analysis" — it couldn't
be written before GraphQL (Epic 4) existed to compare against; now that it does, both
styles are benchmarked head-to-head (real HTTP round trips, same service layer, same
PostgreSQL data) via `RestVsGraphQlBenchmarkTest`.

## Where in the codebase

- `aop/FindUserDataAspect.java` — the aspect itself; `"patient"`/`"doctor"`/
  `"appointment"` cases.
- `annotation/FindUserData.java` — the annotation.
- `utils/filters/QueryBuilder.java`, `utils/filters/PagedRawResult.java`.
- `service/PatientService.java` (`getPatients`/`findPatientsPage`),
  `service/DoctorService.java` (`getDoctors`/`findDoctorsPage`),
  `service/AppointmentService.java` (`getAppointments`/`findAppointmentsPage`).
- `service/RoleService.java` (`sort`, `@ApplyAlgorithm("mergeSort")`) and
  `aop/AlgorithmAspect.java`, `utils/AlgorithmUtils.java` for the in-memory case.

## Verification

```bash
./mvnw test -Dtest=PatientServiceTest,DoctorServiceTest,AppointmentServiceTest
```
Manually: `GET /api/v1/patients?sort=lastName,desc&status=active`,
`GET /api/v1/appointments?sort=appointmentDate,desc&status=scheduled` — confirm sorted,
filtered, paginated results; try a bogus `status` value and confirm a clean `400` instead
of a DB error.
