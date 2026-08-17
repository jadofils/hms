package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.user.role.permission.PermissionResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Permission lookups — backed by {@link PermissionService}. Read-only: permissions are a
 * fixed, system-managed catalog (see that class's Javadoc) with no create/update/delete
 * endpoint here — grant/revoke a permission to a role via {@code RoleController}'s
 * {@code POST}/{@code DELETE /api/v1/roles/{roleId}/permissions/{permissionId}}, or grant
 * permissions at role-creation time via {@code POST /api/v1/roles}'s {@code permissionIds}.
 */
@RestController
@RequestMapping("/api/v1/permissions")
@Tag(name = "Permissions", description = "RBAC permission definitions (read-only; see class Javadoc)")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @Operation(summary = "List permissions (paginated, sortable)",
            description = "Standard `?sort=property,direction` query param (e.g. "
                    + "`sort=resource,asc`) — backed directly by Spring Data JPA, so any "
                    + "`Permission` field is sortable: `permissionId`, `resource`, `action`, "
                    + "`createdAt`, `updatedAt`. Unlike `/api/v1/users`, an unrecognized "
                    + "property is not validated ahead of time and currently surfaces as a "
                    + "400 rather than silently falling back.")
    @ApiResponse(responseCode = "200", description = "Permissions returned")
    @Parameter(name = "sort", in = ParameterIn.QUERY,
            description = "Sort by property,direction. Possible properties: permissionId, resource, "
                    + "action, createdAt, updatedAt.",
            array = @ArraySchema(schema = @Schema(type = "string")), example = "resource,asc")
    @RequirePermission(resource = Resource.PERMISSIONS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PagedModel<PermissionResponse>>> getPermissions(Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Permissions retrieved", permissionService.getPermissions(pageable)));
    }

    @GetMapping("/{permissionId}")
    @Operation(summary = "Get a permission by id")
    @ApiResponse(responseCode = "200", description = "Permission found")
    @ApiResponse(responseCode = "404", description = "Permission not found")
    @RequirePermission(resource = Resource.PERMISSIONS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PermissionResponse>> getPermission(
            @Parameter(description = "Permission UUID") @PathVariable String permissionId) {
        return ResponseEntity.ok(ApiResult.of("Permission retrieved", permissionService.getPermission(permissionId)));
    }
}
