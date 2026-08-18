package amalitech.hospital.management.aop;

import amalitech.hospital.management.exception.checked.PermissionCheckedException;
import amalitech.hospital.management.exception.checked.RoleCheckedException;
import amalitech.hospital.management.exception.checked.UserCheckedException;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.InternalServerException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.exception.runtime.UnauthorizedException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GraphQL's equivalent of {@link GlobalExceptionHandler} — without this, any exception
 * that escapes a resolver (ours or the framework's) falls through to graphql-java's own
 * default masking: a bare {@code "INTERNAL_ERROR for <uuid>"} message, with the real
 * cause visible only in the server log and nowhere in the response. That's the same
 * "must never reach the framework's own generic error handling" problem
 * {@link GlobalExceptionHandler}'s own Javadoc calls out for REST — this maps the exact
 * same exception hierarchy onto a proper {@link GraphQLError} instead, so a GraphQL
 * caller gets as readable a message as a REST caller does for the equivalent failure.
 *
 * <p>Every error's {@code extensions} carry {@code status}/{@code error} alongside
 * GraphQL's own {@code classification}, mirroring REST's {@code ErrorResponse} shape
 * ({@code status}/{@code error}/{@code message}) — a client already parsing one
 * transport's error shape recognizes the other's.
 *
 * <p>Registered as a plain {@code @Component}: Spring for GraphQL's autoconfiguration
 * collects every {@link org.springframework.graphql.execution.DataFetcherExceptionResolver}
 * bean in the context automatically (see {@code GraphQlSource.Builder}) — no explicit
 * wiring needed, the same "just declare it, the framework finds it" pattern
 * {@code @RestControllerAdvice} follows for REST. {@code DataFetcherExceptionResolverAdapter}
 * (rather than implementing the raw interface) is what lets this return one
 * {@link GraphQLError} per exception via {@link #resolveToSingleError} instead of the
 * interface's own {@code Mono<List<GraphQLError>>}-returning method.
 */
@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    private static final Logger log = LoggerFactory.getLogger(GraphQlExceptionResolver.class);

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        // ── Our own exceptions — same classification GlobalExceptionHandler gives them ──
        if (ex instanceof NotFoundException notFound) {
            return build(env, HttpStatus.NOT_FOUND, ErrorType.NOT_FOUND, notFound.getMessage());
        }
        if (ex instanceof BadRequestException badRequest) {
            return build(env, HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST, badRequest.getMessage());
        }
        if (ex instanceof UnauthorizedException unauthorized) {
            return build(env, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED, unauthorized.getMessage());
        }
        if (ex instanceof ConflictException conflict) {
            // GraphQL's ErrorType has no dedicated "conflict" classification — BAD_REQUEST
            // is the closest fit; the real HTTP-equivalent status still rides in extensions.
            return build(env, HttpStatus.CONFLICT, ErrorType.BAD_REQUEST, conflict.getMessage());
        }
        if (ex instanceof InternalServerException internal) {
            log.error("Internal server exception", ex);
            return build(env, HttpStatus.INTERNAL_SERVER_ERROR, ErrorType.INTERNAL_ERROR, internal.getMessage());
        }
        if (ex instanceof UserCheckedException || ex instanceof RoleCheckedException
                || ex instanceof PermissionCheckedException) {
            return build(env, HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST, ex.getMessage());
        }

        // ── Framework exceptions that would otherwise fall through to a bare INTERNAL_ERROR ──

        /** {@code @Valid} failures on a resolver's {@code @Argument} input — GraphQL has no
         *  {@code @RequestBody}, so these surface as bean-validation's own
         *  {@code ConstraintViolationException} via {@code @Validated}'s method-validation
         *  interceptor, not {@code MethodArgumentNotValidException} (that one's MVC-only). */
        if (ex instanceof ConstraintViolationException validation) {
            String message = validation.getConstraintViolations().stream()
                    .map(v -> lastPathSegment(v.getPropertyPath().toString()) + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            return build(env, HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST,
                    message.isBlank() ? "Validation failed" : message);
        }

        /** Thrown by {@code AuthorizationAspect} as well as Spring Security itself — same
         *  message preference as {@code GlobalExceptionHandler.handleAccessDenied}. */
        if (ex instanceof AccessDeniedException accessDenied) {
            String message = accessDenied.getMessage();
            return build(env, HttpStatus.FORBIDDEN, ErrorType.FORBIDDEN,
                    message != null && !message.isBlank() ? message : "You do not have permission to perform this action");
        }

        /** Unique-constraint/not-null violations surfaced at the DB layer. */
        if (ex instanceof DataIntegrityViolationException) {
            log.warn("Data integrity violation: {}", ex.getMessage());
            return build(env, HttpStatus.CONFLICT, ErrorType.BAD_REQUEST, "The request conflicts with existing data");
        }

        /** A row was concurrently modified/deleted between load and save. */
        if (ex instanceof ObjectOptimisticLockingFailureException) {
            log.warn("Optimistic locking failure: {}", ex.getMessage());
            return build(env, HttpStatus.CONFLICT, ErrorType.BAD_REQUEST,
                    "This record was modified by another request — please retry");
        }

        /** An unrecognized {@code sort} column reaching a {@code findAll(Pageable)}-backed
         *  listing (roles/permissions) — the same bad-input case
         *  {@code GlobalExceptionHandler.handleInvalidQueryUsage} covers for REST's own
         *  {@code ?sort=} query param. */
        if (ex instanceof PropertyReferenceException || ex instanceof InvalidDataAccessApiUsageException) {
            log.warn("Invalid query usage: {}", ex.getMessage());
            return build(env, HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST,
                    "Invalid request parameter — check your sort/filter values");
        }

        // ── Last resort — logs the real exception server-side but never exposes it ──
        log.error("Unhandled exception in GraphQL resolver", ex);
        return build(env, HttpStatus.INTERNAL_SERVER_ERROR, ErrorType.INTERNAL_ERROR, "An unexpected error occurred");
    }

    private static String lastPathSegment(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot >= 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
    }

    private GraphQLError build(DataFetchingEnvironment env, HttpStatus status, ErrorType errorType, String message) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("status", status.value());
        extensions.put("error", status.getReasonPhrase());
        return GraphqlErrorBuilder.newError(env)
                .message(message)
                .errorType(errorType)
                .extensions(extensions)
                .build();
    }
}
