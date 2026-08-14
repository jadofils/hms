package amalitech.hospital.management.dto.user.role.permission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PermissionRequest {
    @NotBlank(message = "Resource is required")
    @Size(max = 50, message = "Resource must be at most 50 characters")
    private String resource;

    @NotBlank(message = "Action is required")
    @Size(max = 50, message = "Action must be at most 50 characters")
    private String action;
}