package amalitech.hospital.management.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** {@code username} accepts either the account's username or its email — see
 *  {@code AuthService.login}, which tries a username lookup first and falls back to
 *  an email lookup if that misses. */
@Data
public class LoginRequest {
    @NotBlank(message = "Username or email is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}