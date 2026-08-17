package amalitech.hospital.management.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** {@code username} accepts either the account's username or its email — see
 *  {@code AuthService.login}, which tries a username lookup first and falls back to
 *  an email lookup if that misses. */
@Data
public class LoginRequest {
    @Schema(description = "The account's username or its email — a username lookup is tried first, falling back to an email lookup if that misses.", example = "jdoe", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Username or email is required")
    private String username;

    @Schema(description = "The account's password.", example = "P@ssw0rd1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Password is required")
    private String password;
}