package amalitech.hospital.management.dto.common;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Standard envelope for every successful REST response body: {@code status}, a
 * human-readable {@code message}, and the actual {@code data} payload.
 *
 * Named {@code ApiResult}, not {@code ApiResponse}, specifically to avoid colliding with
 * {@code io.swagger.v3.oas.annotations.responses.ApiResponse}/{@code ApiResponses}, which
 * every controller already imports for Swagger documentation.
 *
 * Only wraps success responses — failures already have their own shape
 * ({@link amalitech.hospital.management.dto.error.ErrorResponse}, built by
 * {@code GlobalExceptionHandler}), and a {@code 204 No Content} delete/unassign endpoint
 * keeps returning an empty body (wrapping "nothing" in an envelope would just be a body
 * on a response whose whole point, per HTTP semantics, is not having one).
 */
@Data
@AllArgsConstructor
public class ApiResult<T> {
    private String status;
    private String message;
    private T data;

    public static <T> ApiResult<T> of(String message, T data) {
        return new ApiResult<>("success", message, data);
    }
}
