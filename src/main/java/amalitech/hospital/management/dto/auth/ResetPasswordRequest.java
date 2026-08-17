package amalitech.hospital.management.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @Schema(description = "The password-reset token previously emailed to the account holder.", example = "b3f1c2a4-9d7e-4e2a-8c1a-5f6d7e8a9b0c", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Reset token is required")
    private String token;

    @Schema(description = "The new password to set. Must be 8-64 characters and contain at least one uppercase letter, one lowercase letter, one number, and one special character.", example = "NewP@ssw0rd1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character"
    )
    private String newPassword;
}
