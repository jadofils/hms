package amalitech.hospital.management.dto.user.role;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * One row of {@code RoleController}'s {@code GET /api/v1/roles/summary} — a role's id/
 * name plus how many permissions it currently holds, without the caller having to open
 * each role individually via {@code GET /api/v1/roles/{roleId}/permissions}.
 */
@Data
public class RolePermissionCountResponse {

    @Schema(description = "Role UUID.", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private String roleId;

    @Schema(description = "Role name.", example = "Admin")
    private String roleName;

    @Schema(description = "Number of permissions currently granted to this role.", example = "12")
    private long permissionCount;
}
