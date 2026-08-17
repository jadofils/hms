package amalitech.hospital.management.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request shape for an administrator provisioning an account (as opposed to
 * self-registration, which stays on {@link UserRequest}) — deliberately has no
 * {@code password} field at all, rather than accepting and silently ignoring one:
 * {@code UserService.createUserByAdmin} always generates a strong password itself and
 * emails it, so there's nothing for a caller to set.
 */
@Data
public class AdminCreateUserRequest {

    @Schema(description = "The username for the new account. Must be 3-50 characters and contain only letters and numbers.", example = "jdoe123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9]+$", message = "Username can only contain letters and numbers")
    private String username;

    /** Required here (unlike {@code UserRequest}'s optional email) — there'd be no way
     *  to deliver the generated password otherwise. */
    @Schema(description = "The email address to deliver the admin-generated password to. Must be a valid email address, at most 100 characters.", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must be at most 100 characters")
    private String email;
}
