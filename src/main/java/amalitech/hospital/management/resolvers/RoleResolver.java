package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.user.role.RoleRequest;
import amalitech.hospital.management.dto.user.role.RoleResponse;
import amalitech.hospital.management.dto.user.role.permission.PermissionResponse;
import amalitech.hospital.management.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import amalitech.hospital.management.utils.GraphQlPaging;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import io.micrometer.core.annotation.Timed;

/**
 * GraphQL front door for {@link RoleService} — see {@link UserResolver}'s Javadoc for the
 * shared reasoning (same service layer as REST, same request DTOs).
 */
@Controller
@Validated
@Timed(value = "hms.graphql.requests", extraTags = {"layer", "graphql"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class RoleResolver {

    private final RoleService roleService;

    @QueryMapping
    public List<RoleResponse> roles(@Argument int page, @Argument int size, @Argument String sort) {
        return roleService.getRoles(GraphQlPaging.of(page, size, sort)).getContent();
    }

    @QueryMapping
    public RoleResponse role(@Argument String roleId) {
        return roleService.getRole(roleId);
    }

    @MutationMapping
    public RoleResponse createRole(@Argument @Valid RoleRequest input) {
        return roleService.createRole(input);
    }

    @MutationMapping
    public RoleResponse updateRole(@Argument String roleId, @Argument @Valid RoleRequest input) {
        return roleService.updateRole(roleId, input);
    }

    @MutationMapping
    public boolean deleteRole(@Argument String roleId) {
        roleService.deleteRole(roleId);
        return true;
    }

    @MutationMapping
    public RoleResponse grantPermission(@Argument String roleId, @Argument String permissionId) {
        roleService.grantPermission(roleId, permissionId);
        return roleService.getRole(roleId);
    }

    @MutationMapping
    public RoleResponse revokePermission(@Argument String roleId, @Argument String permissionId) {
        roleService.revokePermission(roleId, permissionId);
        return roleService.getRole(roleId);
    }

    @SchemaMapping(typeName = "Role", field = "permissions")
    public List<PermissionResponse> permissions(RoleResponse role) {
        return roleService.getRolePermissions(role.getRoleId());
    }
}
