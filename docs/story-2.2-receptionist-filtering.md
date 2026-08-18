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
sort/search over an already-loaded collection. Patient/Doctor/Appointment *listing* uses
the DB-level `@FindUserData` path rather than `@ApplyAlgorithm`, since the whole point of
paginating at the database is to avoid loading more rows into memory than the current
page needs — `@ApplyAlgorithm` is for the opposite situation (a collection you've already
loaded and now need to sort/search without another round-trip). See the dedicated
section below for exactly which real callers use each algorithm and why a custom
implementation was written instead of `Collections.sort`/`Arrays.binarySearch`.

## Algorithms used — sort & search, in detail

`utils/AlgorithmUtils.java` hand-implements both algorithms rather than delegating to
`Collections.sort`/`Arrays.binarySearch` — the README's own Technical Requirements table
names "DSA Integration: Sorting, searching, pagination algorithms" as a first-class
deliverable, not just a means to an end, so the implementation itself (not just the
outcome) is what's being graded here. Each is `O(n)`-space, deterministic, and generic
(`<T>`), reused across every domain that needs it rather than copy-pasted per service.

### Merge sort — `O(n log n)`, stable, real callers

```java
public static <T> void mergeSort(List<T> list, Comparator<T> comparator) {
    if (list == null || list.size() <= 1) return;
    List<T> temp = new ArrayList<>(list);
    mergeSortHelper(list, temp, 0, list.size() - 1, comparator);
}
```
Classic divide-and-conquer: split in half recursively down to single-element sublists
(`O(log n)` levels), then merge each pair of already-sorted halves back together in one
linear `O(n)` pass per level — `O(n log n)` total, the same asymptotic bound as
`Collections.sort` (which is itself a variant of merge sort, TimSort, under the hood).
**Stable** — two elements that compare equal keep their original relative order, which
matters here because both real callers sort by a *partial* key
(`resource:action`/`appointmentDate`) that multiple rows can legitimately share.

Two real callers, each with its own small `@ApplyAlgorithm("mergeSort")` entry point
(never a shared one — see `@ApplyAlgorithm`'s section in
[`annotations-reference.md`](annotations-reference.md) for why):

| Caller | Sorts | Why in memory, not `ORDER BY` |
|---|---|---|
| `RoleService.getRolePermissions` → `self.sort` | A role's granted permissions, by `resource:action` | Already loaded via `rolePermissionRepository.findByIdRoleIdAndDeletedAtIsNull` for the same call — no reason to pay for a second DB round trip just to add ordering to a handful of rows already in hand |
| `AppointmentService.throwIfDoctorDoubleBooked` → `self.sort` | One doctor's own active appointments, by `appointmentDate` | Already loaded to run the double-booking check below — sorting is a prerequisite for the binary search that follows, not an end in itself |

### Binary search — `O(log n)`, precondition-sensitive, real caller

```java
public static <T> int binarySearch(List<T> list, Object targetKey, Function<T, ?> keyExtractor) {
    if (list == null || list.isEmpty() || targetKey == null) return -1;
    int lo = 0, hi = list.size() - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        Comparable<Object> midKey = (Comparable<Object>) keyExtractor.apply(list.get(mid));
        int cmp = midKey.compareTo(targetKey);
        if (cmp == 0) return mid;
        else if (cmp < 0) lo = mid + 1;
        else hi = mid - 1;
    }
    return -1;
}
```
Standard iterative binary search — halves the search space every comparison, `O(log n)`
instead of a linear `O(n)` scan. **Its precondition is the whole reason it's paired with
`mergeSort` above**: the list *must* already be sorted by the same key `keyExtractor`
produces, or the result is meaningless (not an error — it'll return a wrong answer
silently, which is worse). That's why every real caller sorts via `self.sort` immediately
before searching, never searches a list it hasn't just sorted itself.

Real caller — `AppointmentService`'s double-booking guard, which runs before every
`createAppointment`/`updateAppointment`:
```java
private void throwIfDoctorDoubleBooked(String doctorId, LocalDateTime requestedDate, String excludeAppointmentId) {
    List<Appointment> doctorAppointments = new ArrayList<>(
            appointmentRepository.findByDoctor_DoctorIdAndDeletedAtIsNull(doctorId));
    if (excludeAppointmentId != null) {
        doctorAppointments.removeIf(a -> a.getAppointmentId().equals(excludeAppointmentId));
    }
    List<Appointment> sorted = self.sort(doctorAppointments, Comparator.comparing(Appointment::getAppointmentDate));
    if (self.search(sorted, requestedDate, Appointment::getAppointmentDate) != -1) {
        throw new ConflictException("Doctor already has an appointment scheduled at this date and time");
    }
}
```
Given the same doctor's own already-loaded appointment list, checking "is this exact
date/time already taken?" via binary search (`O(log n)`) rather than a linear
`.stream().anyMatch(...)` scan (`O(n)`) or a second, date-filtered SQL query (a network +
DB round trip) is the efficiency win this story's acceptance criterion asks for, applied
to a real business rule (preventing a doctor from being double-booked) rather than a
synthetic example. `excludeAppointmentId` — the appointment being updated, `null` when
creating — is filtered out *before* sorting/searching, so an appointment's own unchanged
slot never counts as a conflict against itself.

**Why not just `Collections.sort`/`Arrays.binarySearch`?** Both exist in the JDK and
would work — this project's own `AlgorithmUtils` deliberately reimplements them anyway,
because the assignment's DSA-integration requirement is explicitly about *applying* a
sorting/searching algorithm as a demonstrated skill, not about picking whichever call is
shortest. In a codebase without that requirement, reaching for `Collections.sort(list,
comparator)` and `Collections.binarySearch(list, key, comparator)` directly would be the
right, boring choice — same asymptotic complexity, battle-tested, zero maintenance
burden. `AlgorithmUtils`'s hand-rolled versions exist *specifically* so the algorithm
itself is visible and testable (`AlgorithmUtilsTest`) as its own unit, independent of
whichever service ends up calling it.

**"Performance documented and analyzed" — see [`performance-report.md`](performance-report.md).**
The README's own Deliverables table names this "REST vs GraphQL analysis" — it couldn't
be written before GraphQL (Epic 4) existed to compare against; now that it does, both
styles are benchmarked head-to-head (real HTTP round trips, same service layer, same
PostgreSQL data) via `RestVsGraphQlBenchmarkTest`.

## Pagination strategy: offset (page-number) based, not cursor-based

**What's actually used, everywhere in this project**: offset/limit, a.k.a. page-number
pagination — Spring Data's `Pageable`/`PagedModel` abstraction on top of, depending on the
listing, either `JpaRepository.findAll(pageable)` (Hibernate translates this to
`LIMIT ? OFFSET ?` itself) or `QueryBuilder.limit(size).offset(page * size)` for the
`@FindUserData`-driven native-query listings (`UserService.getUsers`,
`PatientService.getPatients`, `DoctorService.getDoctors`,
`AppointmentService.getAppointments`, `RoleService.getAssignedRoles`,
`PermissionService.getGrantedPermissions`). A caller asks for "page 3, size 20"; the
server runs `... ORDER BY <col> LIMIT 20 OFFSET 60` and returns that page plus a
separately-queried `total` (via `COUNT(*)`/`COUNT(DISTINCT ...)`) so the client can render
page numbers and a "Page 3 of 12" indicator. This is **not** cursor (keyset) pagination —
worth being explicit about, since the two are easy to conflate and only one of them is
what's actually implemented here.

**Why offset, for this project specifically:**
- **The acceptance criterion itself asks for it.** "As a receptionist, I want to view,
  sort, and filter patients and appointments" implies a receptionist-facing UI with page
  numbers to click through (`1 2 3 ... 12`) and the ability to jump to an arbitrary page
  — offset pagination supports "give me page 7 directly" natively; cursor pagination
  fundamentally can't (see below).
- **One shared abstraction, every domain.** `Pageable`/`PagedModel` is Spring Data's own
  built-in type — every listing endpoint across `User`/`Role`/`Permission`/`Patient`/
  `Doctor`/`Appointment` accepts the exact same `?page=&size=&sort=` query params with no
  per-domain bespoke pagination logic to maintain. A cursor scheme has no equivalent
  built-in support in Spring Data JPA for an arbitrary `sort` column — it would need to be
  hand-built per domain (see below), multiplying this project's own maintenance surface
  six-fold for a benefit this app's data doesn't need yet.
- **Table sizes and access pattern don't demand better than `O(offset)`.** Offset
  pagination's one real weakness is that skipping to a deep page costs `O(offset)` work
  server-side (the DB still has to walk past every skipped row) — a real problem at, say,
  page 50,000 of a billion-row feed. This project's tables (patients, doctors,
  appointments, roles, permissions — all clinic/admin-scale, not social-media-scale) and
  its actual callers (an admin/receptionist paging through a few hundred rows, a handful
  of pages deep at most) never come close to where that cost matters in practice.

**The alternative — cursor (keyset) pagination — and why it wasn't used here:**
Instead of `OFFSET n`, a cursor scheme carries the *last row seen* (its sort key + a
tie-breaking unique id) as an opaque token, and the next page's query becomes
`WHERE (sort_col, id) > (:lastSortVal, :lastId) ORDER BY sort_col, id LIMIT n` — no
`OFFSET` at all, so the DB seeks directly to where the last page left off instead of
walking past every earlier row.

| | Offset (used here) | Cursor/keyset (the alternative) |
|---|---|---|
| Jump to an arbitrary page number | ✅ Native (`page=50`) | ❌ Can only go next/previous from a cursor — no "jump to page 50" without walking there page by page |
| Cost of a deep page | `O(offset)` — degrades as pages get deep | `O(log n)` (an index seek) — flat regardless of depth |
| Stability under concurrent writes | A row inserted/deleted between two page requests can shift every later page by one, causing a skipped or duplicated row | Immune to that — a cursor anchors to a specific row's key, not a row *count* |
| "Total pages" / "Page X of Y" UI | Free — one extra `COUNT(*)` | Awkward — a stable total either needs a separate, possibly-expensive count query anyway, or gets dropped from the UI entirely |
| Implementation complexity here | Already built into Spring Data (`Pageable`) for the `findAll` paths, and a couple of `LIMIT`/`OFFSET` calls on `QueryBuilder` for the AOP-driven paths | Would need a hand-built keyset condition and cursor-encoding/decoding per domain — no Spring Data JPA built-in equivalent for an arbitrary sortable column |

Cursor pagination is the better choice for a feed a user scrolls through indefinitely (a
social timeline, an activity log growing unbounded) where nobody ever asks for "page 400"
by number and rows are inserted constantly while someone's mid-scroll — neither
describes this project's admin/receptionist listings. If a specific listing here ever
needs to support genuinely deep, high-churn pagination (an audit log, say, once
`AuditLog`/`SystemLog` get a real listing endpoint), that would be the moment to revisit
this and add a keyset-based path for that one endpoint specifically, rather than
converting every listing wholesale.

## Where in the codebase

- `aop/FindUserDataAspect.java` — the aspect itself; `"patient"`/`"doctor"`/
  `"appointment"`/`"role"`/`"permission"` cases.
- `annotation/FindUserData.java` — the annotation.
- `utils/filters/QueryBuilder.java`, `utils/filters/PagedRawResult.java`.
- `service/PatientService.java` (`getPatients`/`findPatientsPage`),
  `service/DoctorService.java` (`getDoctors`/`findDoctorsPage`),
  `service/AppointmentService.java` (`getAppointments`/`findAppointmentsPage`),
  `service/RoleService.java` (`getAssignedRoles`/`findAssignedRolesPage`),
  `service/PermissionService.java` (`getGrantedPermissions`/`findGrantedPermissionsPage`).
- `service/RoleService.java` (`sort`, `@ApplyAlgorithm("mergeSort")`),
  `service/AppointmentService.java` (`sort`/`search`,
  `@ApplyAlgorithm("mergeSort"|"binarySearch")`, `throwIfDoctorDoubleBooked`) and
  `aop/AlgorithmAspect.java`, `utils/AlgorithmUtils.java` for the in-memory case.
- `repository/patient/AppointmentRepository.java` —
  `findByDoctor_DoctorIdAndDeletedAtIsNull`, backing the double-booking check above.

## Verification

```bash
./mvnw test -Dtest=PatientServiceTest,DoctorServiceTest,AppointmentServiceTest,RoleServiceTest,PermissionServiceTest,AlgorithmUtilsTest
```
Manually: `GET /api/v1/patients?sort=lastName,desc&status=active`,
`GET /api/v1/appointments?sort=appointmentDate,desc&status=scheduled` — confirm sorted,
filtered, paginated results; try a bogus `status` value and confirm a clean `400` instead
of a DB error. `GET /api/v1/roles/assigned`/`GET /api/v1/permissions/granted` — confirm
each row appears once even when held by more than one user (the `.distinct()` fix).
`POST /api/v1/appointments` twice with the same `doctorId`+`appointmentDate` — confirm the
second attempt returns `409 Conflict`, not a silently-accepted double booking.
