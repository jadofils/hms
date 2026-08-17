package amalitech.hospital.management.dto.user.role;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RoleRequest {
    @Schema(description = "Role name, up to 50 characters, starting with a letter and containing only letters, digits, spaces, hyphens and apostrophes.",
            example = "Ward Nurse",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Role name is required")
    @Size(max = 50, message = "Role name must be at most 50 characters")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9' -]*$",
            message = "Role name must start with a letter and can only contain letters, digits, spaces, hyphens and apostrophes")
    private String roleName;

    /** Optional. */
    @Schema(description = "Optional. Free-text description of the role, up to 255 characters.",
            example = "Handles day-to-day ward nursing duties.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;

    /** Optional — grants each of these permissions to the role immediately at creation
     *  (same effect as calling POST /api/v1/roles/{roleId}/permissions/{permissionId}
     *  once per id afterward), inside the same transaction: an unknown permission id
     *  fails the whole request, so the role is never left partially configured.
     *  Ignored by {@code updateRole} — permission grants are managed separately once a
     *  role already exists (see {@code RoleController}'s grant/revoke endpoints). */
    @Schema(description = "Optional. List of permission ids to grant to the role immediately and atomically at creation "
            + "(same effect as calling the grant-permission endpoint once per id afterward) — an unknown permission id "
            + "fails the whole request, so the role is never left partially configured. Ignored entirely by update, since "
            + "permissions are managed by the separate grant/revoke endpoints there.",
            example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<@NotBlank(message = "Permission id cannot be blank") String> permissionIds;
}


