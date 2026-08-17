package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.user.role.permission.PermissionResponse;
import amalitech.hospital.management.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * GraphQL front door for {@link PermissionService} — read-only, matching REST's
 * {@code PermissionController}: permissions are a fixed, system-managed catalog with no
 * create/update/delete capability anywhere (see {@code PermissionService}'s Javadoc).
 */
@Controller
@Validated
@RequiredArgsConstructor
public class PermissionResolver {

    private final PermissionService permissionService;

    @QueryMapping
    public List<PermissionResponse> permissions(@Argument int page, @Argument int size) {
        return permissionService.getPermissions(PageRequest.of(page, size)).getContent();
    }

    @QueryMapping
    public PermissionResponse permission(@Argument String permissionId) {
        return permissionService.getPermission(permissionId);
    }
}
