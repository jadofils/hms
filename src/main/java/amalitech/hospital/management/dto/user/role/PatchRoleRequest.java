package amalitech.hospital.management.dto.user.role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Partial-update counterpart to {@link RoleRequest} — every field is optional, and
 * {@code RoleService.patchRole} only touches the ones actually present in the request
 * body; a field left out (rather than sent as an empty string) leaves that column
 * exactly as it was. Deliberately omits {@code permissionIds} entirely: that field is
 * create-only on {@link RoleRequest} too (see its own Javadoc — {@code updateRole}
 * already ignores it, since permissions are managed by the separate grant/revoke
 * endpoints), so there's nothing for a patch to touch there either.
 */
@Data
public class PatchRoleRequest {

    @Schema(description = "Optional. New role name, up to 50 characters, starting with a letter and containing "
            + "only letters, digits, spaces, hyphens and apostrophes. Omit to leave the role name unchanged.",
            example = "Ward Nurse",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 50, message = "Role name must be at most 50 characters")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9' -]*$",
            message = "Role name must start with a letter and can only contain letters, digits, spaces, hyphens and apostrophes")
    private String roleName;

    @Schema(description = "Optional. New free-text description, up to 255 characters. Omit to leave the "
            + "description unchanged.",
            example = "Handles day-to-day ward nursing duties.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;
}
