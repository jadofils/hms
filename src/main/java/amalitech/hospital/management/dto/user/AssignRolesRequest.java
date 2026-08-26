package amalitech.hospital.management.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Bulk counterpart to {@code POST /api/v1/users/{userId}/roles/{roleId}} — a user can
 * hold many roles at once (see {@code UserService}'s own Javadoc), so this grants every
 * id in one call instead of one request per role. See
 * {@code UserService.assignRoles} for the atomicity this implies: if any single id
 * doesn't exist or is already actively held, the whole call fails and nothing in the
 * list is assigned — the same all-or-nothing behavior {@code RoleRequest.permissionIds}
 * already has at role-creation time.
 */
@Data
public class AssignRolesRequest {

    @Schema(description = "IDs of every role to assign. Required, at least one.",
            example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "At least one role id is required")
    private List<@NotBlank(message = "Role id cannot be blank") String> roleIds;
}
