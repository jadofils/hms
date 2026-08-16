# Story 3.1 — Validation, Exception Handling, and Documentation

> As a developer, I want to validate and document APIs.

## Acceptance criteria

- Bean Validation applied to DTOs
- Custom validation rules implemented
- OpenAPI/Swagger documentation generated automatically

## How it was achieved

**Bean Validation, every request DTO.** Every `dto/**/*Request.java` uses
`jakarta.validation` constraints matched to what the underlying entity column actually
allows: `@NotBlank`/`@Email` for required-ness and format, `@Size(max = N)` mirroring the
entity's `@Column(length = N)` exactly (so a too-long value gets a clean `400` from
`MethodArgumentNotValidException` instead of failing at the DB). This convention already
existed for `UserRequest`/`RoleRequest`/`PermissionRequest`; every new domain's request
DTO follows it exactly — e.g. `PatientRequest.firstName`: `@NotBlank`,
`@Size(max = 50)` mirroring `Patient.firstName`'s `length = 50` column.

**Custom validation rules — explicit-message regex, not framework defaults.** Every
constraint carries its own human-readable `message = "..."` (no reliance on jakarta's
default messages), and every field with an actual *shape* (not free text) gets a
`@Pattern` regex rather than just accepting any string:
- Names (`firstName`/`lastName`/`specialization`): `^[A-Za-z' -]+$` — letters, spaces,
  hyphens, apostrophes only.
- Phone: `^\+?[0-9]{7,15}$` — 7–15 digits, optional leading `+`.
- Enum-backed fields sent as plain strings (`gender`, `status`, `dayOfWeek`): a `@Pattern`
  whitelisting exactly that enum's allowed `dbValue`s, e.g.
  `(?i)^(M|F|Other)$` for `Patient.gender`, `(?i)^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)$` for
  `DoctorSchedule.dayOfWeek`.
- Free-text fields (`address`, `location`, `reason`, `description`) intentionally get
  **no** `@Pattern` — only `@Size`. There's no "shape" to regex-validate on an address or
  a reason string; forcing one would just reject legitimate input.

**Enum wiring + service-layer conversion.** Several enums (`Gender`, `PatientStatus`,
`ScheduleDay`, `AppointmentStatus`) existed in the codebase already but were never
actually attached to their entity — the entity fields were raw `String` with an inline
comment. Fixed as part of this project: each entity field now uses
`@Convert(converter = XConverter.class)` typed as the actual enum (see
`enums/converter/*.java`), **not** `@Enumerated(EnumType.STRING)` — that would persist
`Enum.name()` (e.g. `"OTHER"`) instead of the enum's own `dbValue` (e.g. `"Other"`),
silently violating the DB's `CHECK` constraint the moment a constant's name and dbValue
differ in case, which several of them do (`PatientStatus.ACTIVE` → `"active"`,
`ScheduleDay.MON` → `"Mon"`, etc.). The DTO still takes the value as a validated `String`
(regex above); the service converts `String → enum` via the enum's own
`fromDbValue(...)`, wrapped so an (in practice unreachable, since the regex already
guards it) `IllegalArgumentException` becomes a clean `400 BadRequestException` instead
of a `500`.

**Cross-field validation done in the service, not the DTO.** e.g.
`DoctorScheduleService.validateTimeRange` checks `endTime` is after `startTime` —
`jakarta.validation` doesn't have a clean single-annotation way to compare two fields on
the same DTO, so this stays as an explicit service-layer check that throws
`BadRequestException` (already routed to a clean `400` by the existing exception
handler — no new exception type needed).

**Exception handling.** `aop/GlobalExceptionHandler.java` (a `@RestControllerAdvice`) is
the single place every exception becomes an HTTP response, so nothing reaches Spring
Boot's own error page. Every new exception type this project introduced
(`ConflictException`/`NotFoundException`/`BadRequestException` — all pre-existing) was
already handled; no new handler was needed since no new *exception type* was added, only
new call sites for the existing ones.

**OpenAPI/Swagger.** springdoc-openapi was already configured
(`springdoc.swagger-ui.path: /`) — every new controller follows the exact same annotation
pattern as the pre-existing ones: `@Tag` at the class level, `@Operation(summary=...,
description=...)` + `@ApiResponses`/`@ApiResponse` per endpoint describing every status
code it can actually return. This requires no extra wiring; Springdoc picks up any
`@RestController` automatically.

## Where in the codebase

- `dto/patient/PatientRequest.java`, `dto/doctor/DoctorRequest.java`,
  `DepartmentRequest.java`, `DoctorScheduleRequest.java`,
  `dto/patient/AppointmentRequest.java` — the new validated DTOs.
- `enums/converter/GenderConverter.java`, `PatientStatusConverter.java`,
  `ScheduleDayConverter.java`, `AppointmentStatusConverter.java`.
- `model/patient/Patient.java`, `model/doctor/DoctorSchedule.java`,
  `model/patient/Appointment.java` — the `@Convert` wiring.
- `aop/GlobalExceptionHandler.java` — unchanged, already covers every exception type
  used.
- Every class under `controller/` — `@Tag`/`@Operation`/`@ApiResponses` annotations.

## Verification

```bash
./mvnw test
```
Swagger UI at `http://localhost:8080/` — confirm every new endpoint is documented with
its request/response schema and status codes. Manually `POST` a patient with an invalid
`gender`/`phone` and confirm the `field: message` validation response
(`GlobalExceptionHandler.handleValidation`).
