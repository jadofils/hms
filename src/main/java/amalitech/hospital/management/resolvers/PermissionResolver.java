package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.user.role.permission.PermissionResponse;
import amalitech.hospital.management.service.PermissionService;
import lombok.RequiredArgsConstructor;
import amalitech.hospital.management.utils.GraphQlPaging;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import io.micrometer.core.annotation.Timed;

/**
 * GraphQL front door for {@link PermissionService} — read-only, matching REST's
 * {@code PermissionController}: permissions are a fixed, system-managed catalog with no
 * create/update/delete capability anywhere (see {@code PermissionService}'s Javadoc).
 */
@Controller
@Validated
@Timed(value = "hms.graphql.requests", extraTags = {"layer", "graphql"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class PermissionResolver {

    private final PermissionService permissionService;

    @QueryMapping
    public List<PermissionResponse> permissions(@Argument int page, @Argument int size, @Argument String sort) {
        return permissionService.getPermissions(GraphQlPaging.of(page, size, sort)).getContent();
    }

    @QueryMapping
    public PermissionResponse permission(@Argument String permissionId) {
        return permissionService.getPermission(permissionId);
    }
}
