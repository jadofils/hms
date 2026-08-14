package amalitech.hospital.management.dto.error;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Uniform error body for every failure response — see {@code GlobalExceptionHandler}.
 * Immutable by design: an error response is a fact about what already happened, not
 * something a caller should be able to mutate after construction. Deliberately carries
 * no stack trace or internal detail — only a safe, user-facing {@link #getMessage()}.
 */
@Getter
public class ErrorResponse {

    private final LocalDateTime timestamp = LocalDateTime.now();
    private final int status;
    private final String error;
    private final String message;

    public ErrorResponse(int status, String error, String message) {
        this.status = status;
        this.error = error;
        this.message = message;
    }
}
