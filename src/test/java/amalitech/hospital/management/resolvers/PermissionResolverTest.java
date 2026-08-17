package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.config.graphql.GraphQlConfig;
import amalitech.hospital.management.dto.user.role.permission.PermissionResponse;
import amalitech.hospital.management.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Slice test for {@link PermissionResolver} — see {@code UserResolverTest}'s Javadoc for
 *  the shared reasoning. Read-only, matching REST's {@code PermissionController}: no
 *  create/update/delete mutation exists (see {@code PermissionService}'s Javadoc). */
@GraphQlTest(PermissionResolver.class)
@Import(GraphQlConfig.class)
class PermissionResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private PermissionService permissionService;

    private PermissionResponse existingPermission() {
        PermissionResponse response = new PermissionResponse();
        response.setPermissionId("perm-1");
        response.setResource("users");
        response.setAction("read");
        return response;
    }

    @Test
    void permission_returnsMappedResponse() {
        when(permissionService.getPermission("perm-1")).thenReturn(existingPermission());

        graphQlTester.document("{ permission(permissionId: \"perm-1\") { resource action } }")
                .execute()
                .path("permission.resource").entity(String.class).isEqualTo("users")
                .path("permission.action").entity(String.class).isEqualTo("read");
    }

    @Test
    void permissions_returnsPagedContent() {
        when(permissionService.getPermissions(any()))
                .thenReturn(new PagedModel<>(new PageImpl<>(List.of(existingPermission()), PageRequest.of(0, 20), 1)));

        graphQlTester.document("{ permissions(page: 0, size: 20) { permissionId } }")
                .execute()
                .path("permissions[0].permissionId").entity(String.class).isEqualTo("perm-1");
    }
}
