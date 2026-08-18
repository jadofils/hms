package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.user.role.RolePermissionCountResponse;
import amalitech.hospital.management.dto.user.role.RoleRequest;
import amalitech.hospital.management.dto.user.role.RoleResponse;
import amalitech.hospital.management.dto.user.role.permission.PermissionResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.micrometer.core.annotation.Timed;

/**
 * Role management — backed by {@link RoleService}. See that class for caching/
 * exception/transaction behavior; this layer only maps HTTP <-> DTOs.
 */
@RestController
@RequestMapping("/api/v1/roles")
@Tag(name = "Roles", description = "RBAC role management")
@Timed(value = "hms.rest.requests", extraTags = {"layer", "rest"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @Operation(summary = "List roles (paginated, sortable)",
            description = "Standard `?sort=property,direction` query param (e.g. "
                    + "`sort=roleName,asc`) — backed directly by Spring Data JPA, so any "
                    + "`Role` field is sortable: `roleId`, `roleName`, `description`, "
                    + "`createdAt`, `updatedAt`. Unlike `/api/v1/users`, an unrecognized "
                    + "property is not validated ahead of time and currently surfaces as a "
                    + "400 rather than silently falling back.")
    @ApiResponse(responseCode = "200", description = "Roles returned")
    @Parameter(name = "sort", in = ParameterIn.QUERY,
            description = "Sort by property,direction. Possible properties: roleId, roleName, "
                    + "description, createdAt, updatedAt.",
            array = @ArraySchema(schema = @Schema(type = "string")), example = "roleName,asc")
    @RequirePermission(resource = Resource.ROLES, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PagedModel<RoleResponse>>> getRoles(Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Roles retrieved", roleService.getRoles(pageable)));
    }

    @GetMapping("/{roleId}")
    @Operation(summary = "Get a role by id")
    @ApiResponse(responseCode = "200", description = "Role found")
    @ApiResponse(responseCode = "404", description = "Role not found")
    @RequirePermission(resource = Resource.ROLES, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<RoleResponse>> getRole(
            @Parameter(description = "Role UUID") @PathVariable String roleId) {
        return ResponseEntity.ok(ApiResult.of("Role retrieved", roleService.getRole(roleId)));
    }

    @PostMapping
    @Operation(summary = "Create a role",
            description = "Optionally grants a list of permissionIds to the new role in the same request, "
                    + "instead of a separate POST /api/v1/roles/{roleId}/permissions/{permissionId} call per "
                    + "permission afterward.")
    @ApiResponse(responseCode = "201", description = "Role created")
    @ApiResponse(responseCode = "404", description = "A given permission id does not exist")
    @ApiResponse(responseCode = "409", description = "Role name already exists")
    @RequirePermission(resource = Resource.ROLES, action = PermissionAction.CREATE)
    public ResponseEntity<ApiResult<RoleResponse>> createRole(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Role created", roleService.createRole(request)));
    }

    @PutMapping("/{roleId}")
    @Operation(summary = "Update a role",
            description = "Blocked while the role is still actively held by any user — "
                    + "revoke it from every holder first (see the role-assignment endpoints "
                    + "under Users).")
    @ApiResponse(responseCode = "200", description = "Role updated")
    @ApiResponse(responseCode = "404", description = "Role not found")
    @ApiResponse(responseCode = "409",
            description = "Role name already exists, or the role is still assigned to one or more users")
    @RequirePermission(resource = Resource.ROLES, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<RoleResponse>> updateRole(
            @Parameter(description = "Role UUID") @PathVariable String roleId,
            @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(ApiResult.of("Role updated", roleService.updateRole(roleId, request)));
    }

    @DeleteMapping("/{roleId}")
    @Operation(summary = "Delete a role",
            description = "Blocked while the role is still actively held by any user — "
                    + "revoke it from every holder first (see the role-assignment endpoints "
                    + "under Users).")
    @ApiResponse(responseCode = "204", description = "Role deleted")
    @ApiResponse(responseCode = "404", description = "Role not found")
    @ApiResponse(responseCode = "409", description = "Role is still assigned to one or more users")
    @RequirePermission(resource = Resource.ROLES, action = PermissionAction.DELETE)
    public ResponseEntity<Void> deleteRole(
            @Parameter(description = "Role UUID") @PathVariable String roleId) {
        roleService.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }

    // ── Analytics ────────────────────────────────────────────────────────────
    // Static segments ("summary"/"assigned") — Spring's PathPattern matcher always
    // prefers a literal segment over "{roleId}" for the same request, regardless of
    // declaration order, so these can't be shadowed by (or shadow) the lookup above.

    @GetMapping("/summary")
    @Operation(summary = "Role summary: permission count per role",
            description = "Every active role plus how many permissions it currently holds — "
                    + "an at-a-glance admin view backed by a `GROUP BY` aggregate query "
                    + "(`@SqlQueryBuilder(\"findRolesWithPermissionCount\")`), instead of paging "
                    + "through `GET /api/v1/roles` and calling "
                    + "`GET /api/v1/roles/{roleId}/permissions` once per role.")
    @ApiResponse(responseCode = "200", description = "Summary returned")
    @RequirePermission(resource = Resource.ROLES, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<List<RolePermissionCountResponse>>> getRolePermissionSummary() {
        return ResponseEntity.ok(ApiResult.of("Role permission summary retrieved", roleService.getRolePermissionSummary()));
    }

    @GetMapping("/assigned")
    @Operation(summary = "List roles currently assigned to at least one active user (paginated, sortable)",
            description = "Distinct from `GET /api/v1/roles` (every role in the catalog, assigned "
                    + "or not) — useful for an admin audit/cleanup view, or a \"currently in-use\" "
                    + "dropdown, without pulling every unassigned role along with it. Standard "
                    + "`?sort=property,direction` query param; sortable columns: `roleId`, `roleName`.")
    @ApiResponse(responseCode = "200", description = "Assigned roles returned")
    @Parameter(name = "sort", in = ParameterIn.QUERY,
            description = "Sort by property,direction. Possible properties: roleId, roleName.",
            array = @ArraySchema(schema = @Schema(type = "string")), example = "roleName,asc")
    @RequirePermission(resource = Resource.ROLES, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PagedModel<RoleResponse>>> getAssignedRoles(Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Assigned roles retrieved", roleService.getAssignedRoles(pageable)));
    }

    // ── Permission assignment ─────────────────────────────────────────────────

    @GetMapping("/{roleId}/permissions")
    @Operation(summary = "List permissions granted to a role")
    @ApiResponse(responseCode = "200", description = "Permissions returned")
    @RequirePermission(resource = Resource.ROLES, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<List<PermissionResponse>>> getRolePermissions(
            @Parameter(description = "Role UUID") @PathVariable String roleId) {
        return ResponseEntity.ok(ApiResult.of("Permissions retrieved", roleService.getRolePermissions(roleId)));
    }

    @PostMapping("/{roleId}/permissions/{permissionId}")
    @Operation(summary = "Grant a permission to a role")
    @ApiResponse(responseCode = "204", description = "Permission granted")
    @ApiResponse(responseCode = "404", description = "Role or permission not found")
    @ApiResponse(responseCode = "409", description = "Role already has this permission")
    @RequirePermission(resource = Resource.ROLES, action = PermissionAction.UPDATE)
    public ResponseEntity<Void> grantPermission(
            @Parameter(description = "Role UUID") @PathVariable String roleId,
            @Parameter(description = "Permission UUID") @PathVariable String permissionId) {
        roleService.grantPermission(roleId, permissionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    @Operation(summary = "Revoke a permission from a role")
    @ApiResponse(responseCode = "204", description = "Permission revoked")
    @ApiResponse(responseCode = "404", description = "Role does not have this permission")
    @RequirePermission(resource = Resource.ROLES, action = PermissionAction.UPDATE)
    public ResponseEntity<Void> revokePermission(
            @Parameter(description = "Role UUID") @PathVariable String roleId,
            @Parameter(description = "Permission UUID") @PathVariable String permissionId) {
        roleService.revokePermission(roleId, permissionId);
        return ResponseEntity.noContent().build();
    }
}
