package amalitech.hospital.management.dto.user.role;

import amalitech.hospital.management.dto.user.role.permission.PermissionResponse;
import lombok.Data;

import java.util.List;

@Data
public class RoleResponse {
    private String roleId;
    private String roleName;
    private String description;
    /** Not populated by the paginated listing or by create/update — only by the
     *  single-item lookup ({@code RoleService.getRole}), same convention as
     *  {@code DoctorResponse.departments}/{@code UserResponse.roles}. */
    private List<PermissionResponse> permissions;
}
