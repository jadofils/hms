package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.config.graphql.GraphQlConfig;
import amalitech.hospital.management.dto.user.role.RoleResponse;
import amalitech.hospital.management.dto.user.role.permission.PermissionResponse;
import amalitech.hospital.management.service.RoleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Slice test for {@link RoleResolver} — see {@code UserResolverTest}'s Javadoc for the
 *  shared reasoning. */
@GraphQlTest(RoleResolver.class)
@Import(GraphQlConfig.class)
class RoleResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private RoleService roleService;

    private RoleResponse existingRole() {
        RoleResponse role = new RoleResponse();
        role.setRoleId("role-1");
        role.setRoleName("Admin");
        role.setDescription("Administrator");
        return role;
    }

    @Test
    void role_returnsMappedResponse() {
        when(roleService.getRole("role-1")).thenReturn(existingRole());

        graphQlTester.document("{ role(roleId: \"role-1\") { roleId roleName } }")
                .execute()
                .path("role.roleName").entity(String.class).isEqualTo("Admin");
    }

    @Test
    void role_permissions_delegatesToGetRolePermissions() {
        when(roleService.getRole("role-1")).thenReturn(existingRole());
        PermissionResponse permission = new PermissionResponse();
        permission.setPermissionId("perm-1");
        permission.setResource("users");
        permission.setAction("read");
        when(roleService.getRolePermissions("role-1")).thenReturn(List.of(permission));

        graphQlTester.document("{ role(roleId: \"role-1\") { permissions { resource action } } }")
                .execute()
                .path("role.permissions[0].resource").entity(String.class).isEqualTo("users");
    }

    @Test
    void createRole_passesPermissionIdsThrough() {
        when(roleService.createRole(any())).thenReturn(existingRole());

        graphQlTester.document(
                        "mutation { createRole(input: { roleName: \"Admin\", permissionIds: [\"perm-1\", \"perm-2\"] }) { roleId } }")
                .execute()
                .path("createRole.roleId").entity(String.class).isEqualTo("role-1");

        verify(roleService).createRole(argThat(request -> request.getPermissionIds().equals(List.of("perm-1", "perm-2"))));
    }

    @Test
    void grantPermission_callsServiceThenReturnsRefreshedRole() {
        when(roleService.getRole("role-1")).thenReturn(existingRole());

        graphQlTester.document("mutation { grantPermission(roleId: \"role-1\", permissionId: \"perm-1\") { roleId } }")
                .execute()
                .path("grantPermission.roleId").entity(String.class).isEqualTo("role-1");

        verify(roleService).grantPermission("role-1", "perm-1");
    }

    @Test
    void deleteRole_returnsTrue() {
        graphQlTester.document("mutation { deleteRole(roleId: \"role-1\") }")
                .execute()
                .path("deleteRole").entity(Boolean.class).isEqualTo(true);

        verify(roleService).deleteRole("role-1");
    }
}
