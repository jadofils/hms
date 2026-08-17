package amalitech.hospital.management.aop;

import amalitech.hospital.management.dto.error.ErrorResponse;
import amalitech.hospital.management.exception.runtime.*;
import amalitech.hospital.management.exception.checked.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Single place every exception becomes an HTTP response.
 *
 * {@code @RestControllerAdvice} ({@code @ControllerAdvice} + {@code @ResponseBody}) is
 * Spring's own cross-cutting mechanism for this — every {@code @RestController} is
 * covered automatically without each one declaring it, the same "don't repeat this in
 * every handler" goal the {@code @Aspect} classes in this package have, just via Spring
 * MVC's exception-resolution pipeline rather than an AspectJ proxy. Grouped alongside
 * them here for that reason, even though the underlying mechanism differs — see the
 * class-level distinction called out when this was first built.
 *
 * No exception — ours or the framework's — should ever reach Spring Boot's own error
 * page: with {@code spring-boot-devtools} active, that page's JSON body includes a full
 * stack trace, which must never go out over the wire. Every handler below returns the
 * same {@link ErrorResponse} shape, and the last one is a catch-all so nothing slips
 * through unmapped.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Our own exceptions ───────────────────────────────────────────────────

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InternalServerException.class)
    public ResponseEntity<ErrorResponse> handleInternal(InternalServerException ex) {
        log.error("Internal server exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(UserCheckedException.class)
    public ResponseEntity<ErrorResponse> handleUserChecked(UserCheckedException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(RoleCheckedException.class)
    public ResponseEntity<ErrorResponse> handleRoleChecked(RoleCheckedException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(PermissionCheckedException.class)
    public ResponseEntity<ErrorResponse> handlePermissionChecked(PermissionCheckedException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // ── Framework exceptions that would otherwise bypass this class entirely ───

    /** {@code @Valid} failures on {@code @RequestBody} DTOs. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return buildResponse(HttpStatus.BAD_REQUEST, message.isBlank() ? "Validation failed" : message);
    }

    /**
     * A malformed {@code @RequestBody} — invalid JSON syntax, or a value that can't
     * convert to its target field type (e.g. {@code "dob": "not-a-date"} against a
     * {@code LocalDate} field, or a request body that isn't valid JSON at all). This
     * doesn't implement {@code org.springframework.web.ErrorResponse} in this Spring
     * version — unlike most other framework exceptions this class special-cases via the
     * catch-all below — so without an explicit handler here it fell straight through to
     * the generic 500, hiding what was actually just a bad request.
     *
     * {@link HttpMessageNotReadableException#getMostSpecificCause()} gives the
     * underlying Jackson/JDK message (e.g. the {@code DateTimeParseException}'s own
     * text for a bad field value) rather than Jackson's own verbose wrapper message; a
     * pure JSON-syntax error (no field-conversion failure) still carries Jackson's own
     * "; at [Source: ...]" location suffix on that innermost cause, which is stripped
     * below to keep every case's message equally clean.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        String cause = ex.getMostSpecificCause().getMessage();
        if (cause != null) {
            cause = cause.split("\\n at \\[Source")[0].trim();
        }
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Request body could not be read: " + (cause != null && !cause.isBlank() ? cause : "invalid JSON"));
    }

    /** Unique-constraint/not-null violations surfaced at the DB layer — e.g. a race
     *  condition that slips past an application-level existsBy... check. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "The request conflicts with existing data");
    }

    /** A row was concurrently modified/deleted between load and save. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic locking failure: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "This record was modified by another request — please retry");
    }

    /**
     * Thrown by {@code AuthorizationAspect} (an authenticated caller whose role lacks
     * the required {@code resource:action}) as well as by Spring Security itself.
     * Prefers the exception's own message — naming which permission was missing is
     * normal REST API behavior, not a security leak, unlike login's deliberately vague
     * 401 (which exists to prevent account enumeration for an *unauthenticated* caller).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        String message = ex.getMessage();
        return buildResponse(HttpStatus.FORBIDDEN,
                message != null && !message.isBlank() ? message : "You do not have permission to perform this action");
    }

    /**
     * A {@code ?sort=} property that doesn't exist on the entity — e.g.
     * {@code GET /api/v1/roles?sort=bogusColumn}. Endpoints backed directly by
     * {@code JpaRepository.findAll(Pageable)} (roles, permissions) pass {@code Sort}
     * straight to Spring Data with no upfront validation, unlike
     * {@code UserService.findUsersPage}'s own whitelist-and-fall-back (see
     * {@code FindUserDataAspect}) — so this is what keeps a bad sort column here a clean
     * 400 instead of falling through to the 500 catch-all below.
     */
    @ExceptionHandler({PropertyReferenceException.class, InvalidDataAccessApiUsageException.class})
    public ResponseEntity<ErrorResponse> handleInvalidQueryUsage(Exception ex) {
        log.warn("Invalid query usage: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid request parameter — check your sort/filter values");
    }

    /**
     * Last resort — logs the real exception server-side but never exposes it to the
     * caller. {@code org.springframework.web.ErrorResponse} can't be used as an
     * {@code @ExceptionHandler} value directly (it's an interface, not a
     * {@code Throwable}), so it's checked here via {@code instanceof} instead: modern
     * Spring MVC exceptions (unmapped route, wrong HTTP method, unsupported media type,
     * missing request param, ...) implement it and already carry their own correct
     * status + detail — honoring that is what keeps e.g. a request to a nonexistent URL
     * a 404 instead of being steamrolled into a generic 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        if (ex instanceof org.springframework.web.ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            String detail = errorResponse.getBody().getDetail();
            log.warn("Framework exception: {} {}", status, detail);
            return buildResponse(status, detail != null ? detail : status.getReasonPhrase());
        }
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), status.getReasonPhrase(), message));
    }
}
