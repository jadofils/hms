# Transactions & Queries Report

Two things in one doc, as asked: (1) which transaction propagation this codebase
actually uses and why, plus all 7 propagation types Spring defines so the two in real
use here sit against the full picture; (2) every derived/JPQL/native query, organized by
type, noting which existed before this pass and which were added in it.

---

## Part 1: Transaction Propagation

### 1.1 What's actually used here — 2 of the 7 types

**`Propagation.REQUIRED` (the default — no attribute needed) — 63 of this codebase's 64
`@Transactional` methods.** Every `create*`/`update*`/`delete*` across every service:
`DoctorService.createDoctor`, `AppointmentService.createAppointment`,
`RoleService.grantPermission`, `AuthService.changePassword`, and so on. Plain
`@Transactional` with nothing else specified *is* `REQUIRED` — Spring's own default.

```java
@Transactional
public DoctorResponse createDoctor(DoctorRequest request) {
    ...
    Doctor saved = doctorRepository.save(doctor);
    for (String departmentId : request.getDepartmentIds()) {
        self.assignDepartment(saved.getDoctorId(), departmentId);   // joins THIS transaction
    }
    return toResponse(saved);
}
```

**Definition:** join the caller's transaction if one is already active; otherwise start
a new one. **Why it's the right default for almost everything here:** most of this
codebase's writes are genuinely multi-step and need all-or-nothing atomicity —
`createDoctor` saves a doctor row, then assigns it to one or more departments in the same
method. If department assignment fails partway through (an unknown department id), the
already-saved doctor row must roll back too, not be left behind half-created. `REQUIRED`
is what makes "the whole method is one unit" true without any extra annotation
attributes. `TransactionRollbackTest` (added this pass) is what actually proves this
holds against a real database, rather than just trusting the annotation.

**`Propagation.REQUIRES_NEW` — exactly one deliberate exception, `SystemLogWriter.record`:**

```java
// SystemLogWriter.java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void record(String logLevel, String source, String message) {
    SystemLog entry = new SystemLog();
    ...
    systemLogRepository.save(entry);
}
```

**Definition:** always suspend whatever transaction is currently active and start a
brand-new, fully independent one — its own commit/rollback, unaffected by the caller's
outcome.

**Why this one specifically needs it:** `SystemLogWriter.record` is called from
`LoggingAspect`'s failure-handling branch — i.e. it runs *while* a real business
transaction is already failing and about to roll back. If it joined that transaction
(`REQUIRED`, the default), the failure-log row it's trying to write would roll back along
with everything else the failing method did — the one moment you most want a record of
what happened would be exactly the one moment nothing gets persisted. `REQUIRES_NEW`
gives the log write its own transaction that commits independently, so it survives even
though the transaction it's reporting on doesn't.

**The pattern in one sentence:** `REQUIRED` for "this must be atomic *with* its caller"
(the normal case, 63 of 64 methods); `REQUIRES_NEW` for the one case that's the
opposite — "this must survive *regardless* of what its caller does."

### 1.2 All 7 propagation types Spring defines (`org.springframework.transaction.annotation.Propagation`)

| # | Type | Definition | Used here? |
|---|---|---|---|
| 1 | **`REQUIRED`** (default) | Join the current transaction if one exists; create a new one if not. | ✅ 63 methods — see 1.1 |
| 2 | **`SUPPORTS`** | Join the current transaction if one exists; run *without* a transaction at all if not (rather than creating one). | ❌ Not used |
| 3 | **`MANDATORY`** | Join the current transaction if one exists; **throw `IllegalTransactionStateException`** if there isn't one. | ❌ Not used |
| 4 | **`REQUIRES_NEW`** | Always suspend any current transaction and start a new, independent one. | ✅ `SystemLogWriter.record` — see 1.1 |
| 5 | **`NOT_SUPPORTED`** | Suspend any current transaction and run with **no transaction** for the duration of this method. | ❌ Not used |
| 6 | **`NEVER`** | Run with no transaction; **throw `IllegalTransactionStateException`** if a transaction is already active. | ❌ Not used |
| 7 | **`NESTED`** | Run within a nested transaction (a JDBC savepoint) inside the current one, if one exists — a failure here can roll back to the savepoint without rolling back the whole outer transaction. Falls back to `REQUIRED` behavior if there's no current transaction. Requires JDBC savepoint support from the driver. | ❌ Not used |

**Why the other 5 aren't used, honestly — not because they're wrong, just because
nothing in this codebase's actual shape calls for them:**

- **`SUPPORTS`** fits a method that's *fine* running non-transactionally (e.g. a pure
  read with no consistency requirement) but should still participate if a caller happens
  to already be inside one. Every read path here (`get*`/`findAll`) is either
  `@Cacheable` (single-item) or a plain unmanaged query — none of them need this
  "opportunistic join" behavior specifically.
- **`MANDATORY`** fits an internal helper that must never be called except from inside an
  already-open transaction — a way to make "this can only run as part of something
  bigger" fail loudly instead of silently running standalone. Nothing here has that
  specific "must be a sub-step, never a starting point" shape; every private helper this
  codebase has is called from a `REQUIRED` method anyway, so `REQUIRED` already gets the
  same practical effect.
- **`NOT_SUPPORTED`** fits work that must run *outside* any surrounding transaction on
  purpose — e.g. an external API call or a long-running report query you don't want
  holding a DB connection/lock for. Nothing in this codebase's transactional methods
  calls out to something like that.
- **`NEVER`** is the strict form of the above — asserting "this must never be called
  transactionally," useful for guarding against accidentally wrapping something (like a
  long batch job) inside a caller's transaction. No such guard has been needed here.
- **`NESTED`** fits "try this sub-step, and if it fails, roll back just that sub-step
  without losing everything else the outer transaction already did" — useful for
  optional, best-effort side steps within a larger unit of work. `SystemLogWriter.record`
  looks superficially similar to this shape (a side effect during a larger operation),
  but it needs full independence — including surviving the *outer* transaction's own
  rollback — which `NESTED` doesn't give (a `NESTED` savepoint still rolls back if the
  outer transaction as a whole rolls back). That's exactly why `REQUIRES_NEW`, not
  `NESTED`, was the right fit there.

### 1.3 Isolation level — a related but distinct concern

Propagation controls transaction *boundaries* (who joins/starts/suspends what);
isolation controls what one transaction can *see* of another's uncommitted/concurrent
work. This pass added one explicit isolation level, on top of the propagation story
above:

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public AppointmentResponse createAppointment(AppointmentRequest request) {
    ...
    throwIfDoctorDoubleBooked(doctor.getDoctorId(), request.getAppointmentDate(), null);
    ...
}
```

`REPEATABLE_READ` (Postgres' own implementation is snapshot isolation) gives the whole
transaction — the double-booking read *and* the appointment insert that follows it — a
single consistent snapshot, the right level for a "check, then act on what I just
checked" method. Documented honestly in that method's own Javadoc as not a complete fix
on its own: two transactions starting near-simultaneously can each take their own
snapshot before either commits and both insert — full prevention needs a DB-level unique
constraint or `SERIALIZABLE` plus a commit-retry loop, neither added this pass. Every
other transactional method in the codebase runs at Postgres' default,
`READ_COMMITTED` (also unset/implicit, the same way `REQUIRED` is for propagation).

---

## Part 2: Queries — derived, JPQL, and native

### 2.1 Derived queries (Spring Data method-name-derived)

The overwhelming majority of this codebase's query needs — every "find related rows by
one foreign key," every soft-delete-aware lookup, every uniqueness check. Representative
list (not exhaustive — every repository under `repository/` has at least one):

| Repository | Method | Added when | Real caller |
|---|---|---|---|
| `AppointmentRepository` | `findByPatient_PatientIdAndDeletedAtIsNull` | v1 | `PatientService.getPatient`'s eager-loaded appointments |
| `AppointmentRepository` | `findByDoctor_DoctorIdAndDeletedAtIsNull` | this session (pre-v2) | `AppointmentService.throwIfDoctorDoubleBooked` |
| `InvoiceRepository` | `findByPaymentStatus` | this session (pre-v2) | `InvoiceService.getInvoices`' billing filter |
| `LabOrderRepository` | `findByStatus` | this session (pre-v2) | `LabOrderService.getLabOrders`' worklist filter |
| `NotificationRepository` | `findByReadAtIsNull`/`findByReadAtIsNotNull` | this session (pre-v2) | `NotificationService.getNotifications`' unread filter |
| `UserRoleRepository` | `findByIdUserIdInAndRevokedAtIsNull` | this session (pre-v2) | `UserService.attachRolesAndDoctors` — batched role eager-loading for a whole page of users |
| `SystemLogRepository` | `findByLogLevel`, `findBySourceContainingIgnoreCase`, `findByLogLevelAndSourceContainingIgnoreCase` | this session (pre-v2) | `SystemLogService.getSystemLogs`' filters |
| `MedicalInventoryRepository` | `findByMedication_MedicationIdAndDeletedAtIsNull` | **v2** | `MedicalInventoryService.getInventoryRecords`' `medicationId` filter |
| `PrescriptionRepository` | `findByAppointment_Patient_PatientIdAndDeletedAtIsNull` | **v2** | `PrescriptionService.getPrescriptions`' `patientId` filter — traverses `Prescription → Appointment → Patient` since `Prescription` has no direct patient FK |

The v2 pass specifically closed two repositories that had **zero** custom methods before
it (`MedicalInventoryRepository`, `PrescriptionRepository`) — the v2 README named both
explicitly as needing derived queries.

### 2.2 Custom JPQL (`@Query`, no `nativeQuery` flag) — 4 total

| Repository.method | Query | Added when | Why JPQL (not derived) |
|---|---|---|---|
| `RolePermissionRepository.hasGrantedPermission` | `SELECT COUNT(rp) > 0 FROM RolePermission rp WHERE rp.role.roleName = :roleName AND rp.permission.resource = :resource AND rp.permission.action = :action AND ...` | v1 | One round trip for `AuthorizationAspect`'s permission check, instead of separately resolving role name → role id → permission id → grant row |
| `UserRepository.findActiveUsersIdleSince` | `SELECT u FROM User u WHERE u.isActive = true AND ... AND u.userId NOT IN (SELECT s.user.userId FROM UserSession s WHERE s.loginAt > :cutoff)` | v1 | A `NOT IN` subquery against a different entity — not expressible as a derived method name |
| `MedicalInventoryRepository.findLowStock` | `SELECT i FROM MedicalInventory i WHERE i.quantityInStock <= i.reorderLevel AND i.deletedAt IS NULL` | **v2** | Compares two columns of the *same row* (`quantityInStock` vs. `reorderLevel`) — derived-query naming has no syntax for "one property against another property," only "property against a supplied value" |
| `MedicationRepository.findLowStock` | `SELECT DISTINCT m FROM Medication m, MedicalInventory i WHERE i.medication = m AND i.quantityInStock <= i.reorderLevel AND i.deletedAt IS NULL AND m.deletedAt IS NULL` | **v2** | Same column-vs-column comparison as above, *plus* a cross-entity join `Medication` has no mapped relationship for at all — the FK only exists on `MedicalInventory`'s side |

### 2.3 Native SQL (`@Query(..., nativeQuery = true)`) — 1 literal example, plus the project's own broader native-SQL mechanism

**The literal, textbook form — added in v2, `PatientRepository.findByMinAgeNative`:**

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

**Why native SQL, not JPQL, here specifically:** Postgres' `AGE()` function (chosen over
a flat `365`-day approximation so leap years don't skew "years old") has no portable JPQL
equivalent — JPQL's function set is deliberately database-agnostic and has no
date-of-birth-to-age computation at all. This is a case where native SQL is the *only*
option, not a stylistic pick. A native query mapped to an entity type still needs an
explicit `countQuery` for `Page<T>` — Spring Data can't always derive one from an
arbitrary native SQL string the way it can from JPQL. Wired into
`PatientService.getPatients`' `minAge` filter, verified live against the real dev
database.

**The project's own, more sophisticated native-SQL mechanism — pre-existing, not part of
this pass:** `@FindUserData`/`@SqlQueryBuilder` (see `docs/annotations-reference.md`), a
fluent `QueryBuilder` that assembles real native SQL and runs it via
`EntityManager.createNativeQuery`. This is the *same* underlying JDBC-level native SQL
execution `nativeQuery = true` uses, just built programmatically and reused across many
listings (every paginated listing's column-sort whitelisting) and cross-table analytics
queries (`findRolesWithPermissionCount`, `findDoctorsByDepartment`,
`findDepartmentsWithDoctors`) instead of hand-written once per repository method. Between
the two, this codebase's native-SQL story is broader than a single `@Query(nativeQuery =
true)` method — `findByMinAgeNative` exists specifically to also have the plain,
textbook form on record, since it's a genuinely different mechanism (a repository
interface method vs. an AOP-driven `EntityManager` call) even though both end up
executing real native SQL against Postgres.

---

## Summary

| Concern | What's used | Where |
|---|---|---|
| Transaction propagation (normal case) | `REQUIRED` (default) | 63 of 64 `@Transactional` methods, every service |
| Transaction propagation (exception) | `REQUIRES_NEW` | `SystemLogWriter.record` only |
| Transaction isolation | `REPEATABLE_READ` (explicit); `READ_COMMITTED` (Postgres default, everywhere else) | `AppointmentService.createAppointment`/`updateAppointment` |
| Derived queries | Dozens, across every repository | Every "find by FK"/"exists by unique column" need |
| Custom JPQL | 4 | `RolePermissionRepository`, `UserRepository` (v1); `MedicalInventoryRepository`, `MedicationRepository` (v2) |
| Native SQL, literal `@Query(nativeQuery = true)` | 1 | `PatientRepository.findByMinAgeNative` (v2) |
| Native SQL, project's own mechanism | `@FindUserData`/`@SqlQueryBuilder` | Every default paginated listing + 3 analytics queries (v1) |
