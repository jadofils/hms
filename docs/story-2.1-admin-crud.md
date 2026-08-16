# Story 2.1 — RESTful API Development (Admin CRUD)

> As an administrator, I want to manage patients, doctors, and departments through REST
> endpoints.

## Acceptance criteria

- CRUD APIs implemented following REST conventions
- Responses structured with status, message, and data
- Controller → Service → Repository architecture applied

## How it was achieved

**Layered architecture.** Every domain follows the same strict layering: `model/`
(JPA `@Entity`) → `repository/` (`JpaRepository<Entity, String>`, DB-generated UUID
`String` PKs) → `service/` (business logic, transactions, `@Cacheable`/`@CachePut`/
`@CacheEvict`) → `controller/` (`@RestController`, DTO mapping only — no business logic).
`dto/` request/response classes stay separate from entities so validation annotations
never leak onto persistence code.

**Full CRUD, every domain.** `User`/`Role`/`Permission` (pre-existing), then `Patient`,
`Doctor`, `Department`, `DoctorSchedule`, and `Appointment` (all added this project) —
each gets `GET` (list, paginated), `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}`
(soft delete via a `deletedAt` timestamp, never a hard delete). Relationship endpoints
follow the same REST shape: `POST/DELETE /doctors/{id}/departments/{deptId}`,
`GET/POST /doctors/{id}/schedules`, `GET /departments/{id}/doctors`, etc.

**Responses structured with status/message/data.** This was a real gap found during the
Epic-by-Epic audit — every controller originally returned the raw response DTO directly
(`ResponseEntity<PatientResponse>`), with no consistent envelope. Added
`dto/common/ApiResult.java`:
```java
public class ApiResult<T> {
    private String status;   // "success"
    private String message;  // human-readable, e.g. "Patient created"
    private T data;
}
```
Every controller's success-path return type changed from `ResponseEntity<X>` to
`ResponseEntity<ApiResult<X>>`, wrapping the service call:
```java
return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResult.of("Patient created", patientService.createPatient(request)));
```
Two deliberate exceptions to that wrapping:
- **Errors** already have their own shape (`dto/error/ErrorResponse` — status/error/
  message, built by `aop/GlobalExceptionHandler`) — this envelope only applies to the
  success path this story is actually about.
- **`204 No Content`** delete/unassign endpoints keep returning an empty body. Per HTTP
  semantics a 204 response's whole point is not having a body — wrapping "nothing" in an
  envelope would just be a body on a response that shouldn't have one.

The class is named `ApiResult`, not `ApiResponse`, specifically because every controller
already imports `io.swagger.v3.oas.annotations.responses.ApiResponse`/`ApiResponses` for
Swagger documentation — reusing that name would have collided with a class already used
dozens of times per file.

## Where in the codebase

- `dto/common/ApiResult.java` — the envelope.
- Every class under `controller/` — `AuthController`, `UserController`,
  `RoleController`, `PermissionController`, `PatientController`, `DoctorController`,
  `DepartmentController`, `DoctorScheduleController`, `AppointmentController`.
- `service/PatientService.java`, `DoctorService.java`, `DepartmentService.java`,
  `DoctorScheduleService.java`, `AppointmentService.java` — the new CRUD services this
  story added, following `UserService`/`RoleService`'s existing shape (soft delete,
  Redis caching, conflict checks before insert/update).
- `repository/patient/PatientRepository.java`, `repository/doctor/DoctorRepository.java`,
  `DepartmentRepository.java`, `DoctorScheduleRepository.java`,
  `repository/patient/AppointmentRepository.java`.

## Verification

```bash
./mvnw test
```
Swagger UI at `http://localhost:8080/` (once `spring-boot:run` is up) — every endpoint's
`200`/`201` response body now shows `{"status":"success","message":"...","data":{...}}`;
`204` endpoints show an empty body as before.
