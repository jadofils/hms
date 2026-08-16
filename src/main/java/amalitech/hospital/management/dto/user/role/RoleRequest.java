package amalitech.hospital.management.dto.user.role;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleRequest {
    @NotBlank(message = "Role name is required")
    @Size(max = 50, message = "Role name must be at most 50 characters")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9' -]*$",
            message = "Role name must start with a letter and can only contain letters, digits, spaces, hyphens and apostrophes")
    private String roleName;

    /** Optional. */
    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;
}


