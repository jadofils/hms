package amalitech.hospital.management.aop;

import amalitech.hospital.management.dto.error.ErrorResponse;
import amalitech.hospital.management.exception.checked.PermissionCheckedException;
import amalitech.hospital.management.exception.checked.RoleCheckedException;
import amalitech.hospital.management.exception.checked.UserCheckedException;
import amalitech.hospital.management.exception.runtime.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests — {@link GlobalExceptionHandler} has no Spring-context dependency
 * of its own (a {@code @RestControllerAdvice} is just a bean whose methods Spring MVC's
 * exception resolver dispatches to), so every handler method is called directly here.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private void dummyMethod(String arg) {
        // used only to obtain a real MethodParameter for MethodArgumentNotValidException
    }

    private ErrorResponse bodyOf(ResponseEntity<ErrorResponse> response) {
        return response.getBody();
    }

    @Test
    void handleConflict_returns409() {
        var response = handler.handleConflict(new ConflictException("already exists"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bodyOf(response).getMessage()).isEqualTo("already exists");
    }

    @Test
    void handleInternal_returns500_andLogs() {
        var response = handler.handleInternal(new InternalServerException("boom"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(bodyOf(response).getMessage()).isEqualTo("boom");
    }

    @Test
    void handleUserChecked_returns400() {
        var response = handler.handleUserChecked(new UserCheckedException("bad user request"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(response).getMessage()).isEqualTo("bad user request");
    }

    @Test
    void handleRoleChecked_returns400() {
        var response = handler.handleRoleChecked(new RoleCheckedException("bad role request"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(response).getMessage()).isEqualTo("bad role request");
    }

    @Test
    void handlePermissionChecked_returns400() {
        var response = handler.handlePermissionChecked(new PermissionCheckedException("bad permission request"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(response).getMessage()).isEqualTo("bad permission request");
    }

    @Test
    void handleDataIntegrityViolation_returns409_andLogs() {
        var response = handler.handleDataIntegrityViolation(new DataIntegrityViolationException("unique violation"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bodyOf(response).getMessage()).isEqualTo("The request conflicts with existing data");
    }

    @Test
    void handleOptimisticLock_returns409_andLogs() {
        var response = handler.handleOptimisticLock(new ObjectOptimisticLockingFailureException("Role", "id-1"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bodyOf(response).getMessage()).contains("modified by another request");
    }

    @Test
    void handleInvalidQueryUsage_returns400_forPropertyReferenceException() {
        var response = handler.handleInvalidQueryUsage(
                new InvalidDataAccessApiUsageException("bogus sort column"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(response).getMessage()).contains("sort/filter");
    }

    @Test
    void handleValidation_joinsEveryFieldError() throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("dummyMethod", String.class);
        MethodParameter methodParameter = new MethodParameter(method, 0);
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "username", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        var response = handler.handleValidation(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(response).getMessage()).isEqualTo("username: must not be blank");
    }

    @Test
    void handleValidation_fallsBackToGenericMessage_whenNoFieldErrors() throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("dummyMethod", String.class);
        MethodParameter methodParameter = new MethodParameter(method, 0);
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        var response = handler.handleValidation(ex);
        assertThat(bodyOf(response).getMessage()).isEqualTo("Validation failed");
    }

    @Test
    void handleAccessDenied_prefersExceptionMessage() {
        var response = handler.handleAccessDenied(new AccessDeniedException("Role 'Doctor' lacks permission patients:delete"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(bodyOf(response).getMessage()).isEqualTo("Role 'Doctor' lacks permission patients:delete");
    }

    @Test
    void handleAccessDenied_fallsBackToGenericMessage_whenExceptionMessageBlank() {
        var response = handler.handleAccessDenied(new AccessDeniedException(""));
        assertThat(bodyOf(response).getMessage()).isEqualTo("You do not have permission to perform this action");
    }

    @Test
    void handleUnexpected_honorsErrorResponseDetail_whenPresent() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "custom detail");
        var response = handler.handleUnexpected(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(bodyOf(response).getMessage()).isEqualTo("custom detail");
    }

    @Test
    void handleUnexpected_fallsBackToReasonPhrase_whenErrorResponseDetailIsNull() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND);
        var response = handler.handleUnexpected(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(bodyOf(response).getMessage()).isEqualTo(HttpStatus.NOT_FOUND.getReasonPhrase());
    }

    @Test
    void handleUnexpected_returns500_forAnyOtherException() {
        var response = handler.handleUnexpected(new RuntimeException("totally unmapped"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(bodyOf(response).getMessage()).isEqualTo("An unexpected error occurred");
    }
}
