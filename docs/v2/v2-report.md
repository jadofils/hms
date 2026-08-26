# HMS v2 Report — Spring Data, Transactions, Optimization & Caching

This maps every Epic/User Story in [`Readme-v2.md`](Readme-v2.md) to what's actually
implemented in this codebase, where, and — for each acceptance criterion that wasn't
already covered by the v1 build — what was added this pass to close it. Written the same
way [`README.md`](../README.md)'s own v1 evaluation was: evidence-first, real file/line
references, no "should already work" hand-waving.

Most of v2's ask was already satisfied by v1's own build (Spring Data JPA, `Pageable`
pagination, `@Transactional`, Spring Cache were all foundational to this project from the
start, not new). What genuinely didn't exist yet — isolation-level usage, index
validation, a measured rollback test, and a measured cache-performance report — is called
out explicitly below and was added as part of this pass.

---

## Epic 1: Spring Data Integration

**User Story 1.1** — *As a developer, I want to integrate Spring Data JPA for consistent
database access.*

| Acceptance criterion | Status | Evidence |
|---|---|---|
| Spring Data JPA dependency configured | ✅ Already done | `pom.xml` — `spring-boot-starter-data-jpa` |
| Entities annotated (`@Entity`, `@Id`, relationships) | ✅ Already done | Every class under `model/` — `@Entity`, `@Id @GeneratedValue(strategy = GenerationType.UUID)`, `@ManyToOne`/`@ManyToMany`/`@OneToMany` throughout |
| Repositories extend `JpaRepository`/`CrudRepository` | ✅ Already done | All 25 repositories under `repository/` extend `JpaRepository<Entity, String>` |
| Application connects to existing database | ✅ Already done | Real PostgreSQL via `compose.yaml` (port 5433), `spring.datasource.url` in `application.yaml` |

Nothing to add here — this has been true since the project's v1 foundation.

---

## Epic 2: Repository and Query Development

**User Story 2.1** — *As an administrator, I want to manage hospital data using
repositories.*

| Acceptance criterion | Status | Evidence |
|---|---|---|
| Repositories for Patient, Doctor, Department, Appointment, Prescription, PrescriptionItem, PatientFeedback, MedicalInventory | ✅ Already done | `PatientRepository`, `DoctorRepository`, `DepartmentRepository`, `AppointmentRepository`, `PrescriptionRepository`, `PrescriptionItemRepository`, `PatientFeedbackRepository`, `MedicalInventoryRepository` all exist |
| Derived query methods implemented | ⚠️ Two gaps closed this pass | See below |
| Custom queries using `@Query` (JPQL and native SQL) | ✅ Already done, expanded | See below |

**The real gap:** `MedicalInventoryRepository` and `PrescriptionRepository` — the two
entities the README specifically names — had **zero** custom methods before this pass;
each was a bare `extends JpaRepository<X, String>` with nothing beyond the inherited
CRUD. Closed by:

- **`PrescriptionRepository.findByAppointment_Patient_PatientIdAndDeletedAtIsNull`** —
  a derived query traversing `Prescription → Appointment → Patient` (Prescription has no
  direct patient FK — it's keyed by `appointment_id`, same shape as
  `MedicalRecordRepository`'s existing patient traversal). Wired into
  `PrescriptionService.getPrescriptions(pageable, patientId)`, `GET
  /api/v1/prescriptions?patientId=...`, and GraphQL's `prescriptions(patientId: ...)` —
  a patient's own prescription history.
- **`MedicalInventoryRepository.findByMedication_MedicationIdAndDeletedAtIsNull`** — a
  derived query (every batch on hand for one medication) and
  **`MedicalInventoryRepository.findLowStock`** — a genuine custom JPQL query,
  `WHERE i.quantityInStock <= i.reorderLevel`, comparing two columns of the *same row*,
  which a derived-query method name literally cannot express (Spring Data's naming
  convention has no way to reference one property against another). Both wired into
  `MedicalInventoryService.getInventoryRecords(pageable, medicationId, lowStock)`, `GET
  /api/v1/medical-inventory?lowStock=true`, and GraphQL's `inventoryRecords(lowStock:
  true)` — a real pharmacy restock-alert worklist, not a demo query.

**JPQL `@Query` — already real, now with two more examples.** Two pre-existing cases:
`RolePermissionRepository.hasPermission` (checks a role→permission grant in one round
trip for `AuthorizationAspect`) and `UserRepository.findActiveUsersIdleSince`
(`MaintenanceService`'s idle-user deactivation). `MedicalInventoryRepository.findLowStock`
above is the third.

**Fourth — `MedicationRepository.findLowStock`**, added this pass. Distinct from
`MedicalInventoryRepository.findLowStock` (which lists individual low-stock *batches*),
this answers "which *medications* need reordering at all" — one row per medication no
matter how many of its own batches are low. `Medication` has no mapped JPA relationship
to `MedicalInventory` at all (the FK only exists on the inventory side), so this is an
explicit cross-entity join filtered by `WHERE`, with `DISTINCT` since one medication can
have several low-stock batches:
```java
@Query("""
        SELECT DISTINCT m FROM Medication m, MedicalInventory i
        WHERE i.medication = m AND i.quantityInStock <= i.reorderLevel
          AND i.deletedAt IS NULL AND m.deletedAt IS NULL
        """)
Page<Medication> findLowStock(Pageable pageable);
```
Wired into `MedicationService.getMedications(pageable, lowStock)`, `GET
/api/v1/medications?lowStock=true`, and GraphQL's `medications(lowStock: true)` — a
pharmacist gets two complementary, genuinely different views from the two `lowStock`
filters: "which batches" vs. "which medications."

**Native SQL — both the project's own sophisticated mechanism, and now a literal
`@Query(nativeQuery = true)` example too.** The bulk of this project's native-SQL story
is `@FindUserData`/`@SqlQueryBuilder` (see `docs/annotations-reference.md`) — a fluent
`QueryBuilder` assembling real native SQL, executed via `EntityManager.createNativeQuery`,
driving every paginated listing's column-sort whitelisting and every cross-table
analytics query (`findRolesWithPermissionCount`, `findDoctorsByDepartment`,
`findDepartmentsWithDoctors`). That's the same underlying JDBC-level native SQL execution
a plain `@Query(nativeQuery = true)` method would use, just built programmatically and
reused across many listings instead of hand-written per method.

Alongside it, **`PatientRepository.findByMinAgeNative`** is the literal, textbook form:
```java
@Query(value = """
        SELECT * FROM patients
        WHERE EXTRACT(YEAR FROM AGE(CURRENT_DATE, dob)) >= :age AND deleted_at IS NULL
        """,
        countQuery = """
        SELECT COUNT(*) FROM patients
        WHERE EXTRACT(YEAR FROM AGE(CURRENT_DATE, dob)) >= :age AND deleted_at IS NULL
        """,
        nativeQuery = true)
Page<Patient> findByMinAgeNative(@Param("age") int age, Pageable pageable);
```
Chosen because it's a genuine case where native SQL is the *only* option, not a
stylistic pick — Postgres' `AGE()` function (used here specifically instead of a flat
`365`-day approximation, so leap years don't skew the result) has no portable JPQL
equivalent at all; JPQL's function set is deliberately database-agnostic. A native query
mapped to an entity type still needs an explicit `countQuery` for `Page<T>` — Spring Data
can't always derive one from an arbitrary native SQL string the way it can from JPQL.
Wired into `PatientService.getPatients`' `minAge` filter (`GET
/api/v1/patients?minAge=65`, GraphQL `patients(minAge: 65)`) — verified live against the
real dev database, returning real patients by computed age. `minAge` wins over
`status`/`gender` and bypasses the `@FindUserData` path entirely when given, the same
"filters aren't always combinable in one call" precedent `MedicalInventoryService`'s
`lowStock`/`medicationId` already set.

**User Story 2.2** — *As a receptionist, I want to browse data using pagination and
sorting.* Already fully covered by v1's build — every listing endpoint takes a `Pageable`
and returns a `PagedModel`, sortable by any whitelisted column (REST `?sort=`, GraphQL
`sort` argument). See [`story-2.2-receptionist-filtering.md`](../story-2.2-receptionist-filtering.md)
and [`performance-report.md`](../performance-report.md)'s "Pagination, Sorting & Filtering"
section — including the explicit offset-vs-cursor-pagination comparison this v2 README's
own Technical Requirements table doesn't ask for directly but the original v1 README did.

---

## Epic 3: Transaction Management and Optimization

**User Story 3.1** — *As a developer, I want transactional integrity for booking and
prescriptions.*

| Acceptance criterion | Status | Evidence |
|---|---|---|
| `@Transactional` applied correctly | ✅ Already done | Every create/update/delete service method — `AppointmentService`, `PrescriptionService`, `DoctorService`, etc. |
| Propagation and isolation levels demonstrated | ⚠️ Propagation already done; isolation added this pass | See below |
| Rollback verified on failure scenarios | ⚠️ Gap closed this pass | See below |

**Propagation** — already real, not new: `SystemLogWriter.record`'s
`@Transactional(propagation = Propagation.REQUIRES_NEW)`. The failing call
`LoggingAspect` is reporting on is very often itself `@Transactional` and about to roll
back; without a genuinely *new* transaction, the failure-log row would be written inside
a transaction that's already marked for rollback and vanish along with it — see that
class's own Javadoc.

**Isolation — genuinely new, added on `AppointmentService.createAppointment`/
`updateAppointment`** (`Isolation.REPEATABLE_READ`). This is deliberately real, not
decorative: `throwIfDoctorDoubleBooked` reads a doctor's appointments, then the same
method writes a new one — a stable snapshot for the whole transaction is the correct
level for that "check, then act on what I just checked" shape. Documented honestly in
that method's own Javadoc as *not* a complete fix on its own: two transactions starting
near-simultaneously can each take their own snapshot before either commits, each see "no
conflict," and both insert. Full prevention needs either a DB-level unique constraint on
`(doctor_id, appointment_date)` or `SERIALIZABLE` plus a commit-retry loop — deliberately
not added this pass, flagged as a real follow-up rather than oversold as already fixed.

**Rollback verification — genuinely new.** Every existing service test in this codebase
mocks its repository (per `CLAUDE.md`'s Testing section) — a mock can't observe a real
rollback, since there's no real transaction to roll back. `TransactionRollbackTest`
(`src/test/java/.../service/TransactionRollbackTest.java`) is the first test that proves
it against a real database: it calls `DoctorService.createDoctor` with one real department
id and one fake one, so `assignDepartment` succeeds once, then throws `NotFoundException`
on the second — *after* the doctor row itself was already saved earlier in the same
`@Transactional` method. The assertion that matters isn't "the right exception was
thrown" (every other test already covers that) — it's `doctorRepository.existsByEmail(email)`
returning `false` afterward, proving the doctor row that already made it to the database
before the failure was rolled back along with everything else. Passed against the real
dev Postgres instance (confirmed this pass, not assumed).

**User Story 3.2** — *As an analyst, I want optimized queries for better performance.*

| Acceptance criterion | Status | Evidence |
|---|---|---|
| Complex JPQL queries optimized | ✅ Already reasonable | `RolePermissionRepository`/`UserRepository`'s existing `@Query`s are single-round-trip by design (see their own Javadoc) |
| Index usage validated | ⚠️ Genuinely new | See below |
| Query performance measured | ⚠️ Genuinely new | See below |

**Indexes — genuinely new**, added to the columns actually filtered/sorted/joined on
in real code, not a blanket "index everything":

| Table | Column(s) | Real caller |
|---|---|---|
| `appointments` | `doctor_id` | `AppointmentService.throwIfDoctorDoubleBooked` — every single create/update runs this; without an index it's a full table scan of every appointment ever booked, for every doctor, on every request |
| `appointments` | `appointment_date` | `AppointmentService.getAppointments`' sort/filter, both REST and GraphQL |
| `notifications` | `read_at` | `NotificationRepository.findByReadAtIsNull`/`findByReadAtIsNotNull` — `NotificationService.getNotifications`' unread filter |
| `invoices` | `payment_status` | `InvoiceRepository.findByPaymentStatus` — `InvoiceService.getInvoices`' billing-worklist filter |
| `system_logs` | `created_at`, `log_level` | `MaintenanceService`'s cleanup query, `SystemLogService.getSystemLogs`' filters |

Declared via `@Table(indexes = {...})` on each entity — Hibernate creates them
automatically in `dev` (`ddl-auto: update`; confirmed live via `psql`'s `pg_indexes` after
a restart). `test`/`prod` use `ddl-auto: validate` (the existing schema is assumed
correct — Hibernate never creates or checks for indexes in that mode), and this project
has no Flyway/Liquibase migration tool to apply schema changes automatically outside
`dev`. [`docs/v2/sql/v2-indexes.sql`](sql/v2-indexes.sql) is the same six `CREATE INDEX IF
NOT EXISTS` statements, meant to be run by hand against any database that wasn't
bootstrapped with `ddl-auto=update` after this change existed.

**Validated, not just declared** — real `EXPLAIN ANALYZE` output, `idx_appointments_doctor_id`:

```
-- With the index (actual production plan):
Index Scan using idx_appointments_doctor_id on appointments
  (cost=0.31..8.33 rows=1 width=161) (actual time=0.024..0.025 rows=1.00 loops=1)

-- Forced sequential scan (enable_indexscan/enable_bitmapscan off), same query:
Seq Scan on appointments
  (cost=0.04..14.58 rows=1 width=161) (actual time=0.014..0.053 rows=1.00 loops=1)
  Rows Removed by Filter: 373
```

Honest reading of this: at today's table size (374 rows), the *absolute* time
difference is small — Postgres can sequentially scan 374 rows in under a millisecond
either way. What the plan shows is the *cost model's* own preference (8.33 vs 14.58) and
the mechanism (`Index Scan`, zero rows discarded, vs `Seq Scan`, 373 of 374 rows read and
thrown away by the filter) — the gap that will actually matter is at 10x/100x this row
count, not today. This is presented as real, current evidence of the right query plan
existing, not an inflated performance claim.

**Query performance measured** — see the pre-existing
[`performance-report.md`](../performance-report.md) (REST vs GraphQL, 8 operations, real
`System.nanoTime()` timings) and the new
[`cache-performance-report.md`](cache-performance-report.md) below.

---

## Epic 4: Caching and Performance Enhancement

**User Story 4.1** — *As a receptionist, I want faster access to frequently used data.*

| Acceptance criterion | Status | Evidence |
|---|---|---|
| Spring Cache implemented (`@EnableCaching`) | ✅ Already done | `CacheConfig` |
| `@Cacheable`/`@CacheEvict` used appropriately | ✅ Already done | Every single-item `get*ById` across every service — `UserService.getUser`, `DoctorService.getDoctor`, `InvoiceService.getInvoice`, `SystemLogService.getSystemLog`, etc. |
| Cache invalidation handled correctly | ✅ Already done | Every `@Cacheable` single-item lookup is paired with a `@CachePut`/`@CacheEvict` on that same entity's update/delete |
| Performance improvements measured | ⚠️ Genuinely new | See below |

**Measured, not just implemented — genuinely new.** No prior report measured what
caching actually buys. [`cache-performance-report.md`](cache-performance-report.md),
generated by `CachePerformanceBenchmarkTest` (real HTTP round trips, real Postgres, real
Redis — same rigor as `RestVsGraphQlBenchmarkTest`), shows a real run:

| Measurement | Latency (ms) |
|---|---|
| Cache miss (1st call — Postgres + Redis write) | 77.784 |
| Cache hit, avg (Redis read only) | 17.369 |
| Cache hit, p95 (Redis read only) | 23.128 |
| **Speedup** | **4.5x** |

See that report's own "Analysis" section for the honest caveats (single-key measurement,
not a load test; why this project caches only single-item lookups, never a paginated
listing — the same "whole-table cache doesn't fit a write-heavy, filterable/sortable
access pattern" reasoning `docs/performance-report.md`'s own "What `@Timed` maps onto this
report" section gives).

---

## Epic 5: Reporting and Documentation

**User Story 5.1** — *As a contributor, I want clear documentation of repositories and
optimizations.*

| Acceptance criterion | Status | Evidence |
|---|---|---|
| Repository and query logic documented | ✅ | Every repository method's own Javadoc names its real caller; this report's Epic 2 section above |
| Transaction strategies explained | ✅ | This report's Epic 3 section; `AppointmentService.createAppointment`'s own isolation-level Javadoc; `SystemLogWriter`'s propagation Javadoc |
| README updated with caching and setup instructions | ✅ | This report; [`cache-performance-report.md`](cache-performance-report.md); [`sql/v2-indexes.sql`](sql/v2-indexes.sql)'s own header comment for the one manual setup step this pass introduces |

---

## What changed this pass — file index

| File | What |
|---|---|
| `service/AppointmentService.java` | `Isolation.REPEATABLE_READ` on `createAppointment`/`updateAppointment` |
| `model/patient/Appointment.java` | `@Index` on `doctor_id`, `appointment_date` |
| `model/notification/Notification.java` | `@Index` on `read_at` |
| `model/finance/Invoice.java` | `@Index` on `payment_status` |
| `model/user/logs/SystemLog.java` | `@Index` on `created_at`, `log_level` |
| `docs/v2/sql/v2-indexes.sql` | Manual DDL for non-`dev` databases (no migration tool exists) |
| `test/.../service/TransactionRollbackTest.java` | Real-DB rollback verification |
| `test/.../benchmark/CachePerformanceBenchmarkTest.java` | Generates `cache-performance-report.md` |
| `repository/pharmacy/MedicalInventoryRepository.java` | Derived query + custom JPQL `findLowStock` |
| `repository/pharmacy/PrescriptionRepository.java` | Derived query, patient-traversal |
| `service/MedicalInventoryService.java` | `lowStock`/`medicationId` filters |
| `service/PrescriptionService.java` | `patientId` filter |
| `controller/MedicalInventoryController.java`, `controller/PrescriptionController.java` | REST query params for the above |
| `resolvers/MedicalInventoryResolver.java`, `resolvers/PrescriptionResolver.java` | GraphQL arguments for the above |
| `resources/graphql/pharmacy.graphqls` | Schema updates for the above |
| `test/.../service/MedicalInventoryServiceTest.java`, `PrescriptionServiceTest.java` | Coverage for the new filters |

**Full suite after every change in this pass: see the commit history for exact pass/fail
counts at each step** — every change above was compiled, tested, and (for the two
benchmarks and the index creation) verified live before being folded in, the same
verify-before-claim discipline this whole project has followed since v1.
