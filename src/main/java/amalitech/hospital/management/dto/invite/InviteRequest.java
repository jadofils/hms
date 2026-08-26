package amalitech.hospital.management.dto.invite;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InviteRequest {

    @Schema(description = "Email address to invite. Required, must be a valid email format, up to 100 characters.",
            example = "new.doctor@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must be at most 100 characters")
    private String email;

    @Schema(description = "ID of the role to grant the moment this email completes registration "
            + "(self-registration or a first Google OAuth2 login). Required.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Role id is required")
    private String roleId;
}
