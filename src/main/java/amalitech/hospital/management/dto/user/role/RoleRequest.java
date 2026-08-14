package amalitech.hospital.management.dto.user.role;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleRequest {
    @NotBlank(message = "Role name is required")
    @Size(max = 50, message = "Role name must be at most 50 characters")
    private String roleName;

    /** Optional. */
    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;
}


