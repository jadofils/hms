package amalitech.hospital.management.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Partial-update counterpart to {@link UserRequest} — every field optional; only the
 * ones actually present in the request body get changed. See
 * {@code UserService.patchUser}.
 *
 * <p>Deliberately has no {@code password} field — password changes go exclusively
 * through {@code AuthService.changePassword}/{@code resetPassword}, which apply their
 * own current-password/reset-token checks. A general partial-update endpoint must
 * never be able to silently overwrite {@code passwordHash}.
 */
@Data
public class PatchUserRequest {

    @Schema(description = "Optional. Username, between 3 and 50 characters, containing only letters and numbers.",
            example = "jdoe123", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9]+$", message = "Username can only contain letters and numbers")
    private String username;

    @Schema(description = "Optional. A valid email address, up to 100 characters.",
            example = "jdoe@example.com", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must be at most 100 characters")
    private String email;
}
