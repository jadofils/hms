package amalitech.hospital.management.controller;

import amalitech.hospital.management.dto.user.role.RoleRequest;
import amalitech.hospital.management.dto.user.role.RoleResponse;
import amalitech.hospital.management.dto.user.role.permission.PermissionResponse;
import amalitech.hospital.management.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Role management — backed by {@link RoleService}. See that class for caching/
 * exception/transaction behavior; this layer only maps HTTP <-> DTOs.
 */
@RestController
@RequestMapping("/api/v1/roles")
@Tag(name = "Roles", description = "RBAC role management")
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
    public ResponseEntity<PagedModel<RoleResponse>> getRoles(Pageable pageable) {
        return ResponseEntity.ok(roleService.getRoles(pageable));
    }

    @GetMapping("/{roleId}")
    @Operation(summary = "Get a role by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role found"),
            @ApiResponse(responseCode = "404", description = "Role not found")
    })
    public ResponseEntity<RoleResponse> getRole(
            @Parameter(description = "Role UUID") @PathVariable String roleId) {
        return ResponseEntity.ok(roleService.getRole(roleId));
    }

    @PostMapping
    @Operation(summary = "Create a role")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Role created"),
            @ApiResponse(responseCode = "409", description = "Role name already exists")
    })
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.createRole(request));
    }

    @PutMapping("/{roleId}")
    @Operation(summary = "Update a role",
            description = "Blocked while the role is still actively held by any user — "
                    + "revoke it from every holder first (see the role-assignment endpoints "
                    + "under Users).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role updated"),
            @ApiResponse(responseCode = "404", description = "Role not found"),
            @ApiResponse(responseCode = "409",
                    description = "Role name already exists, or the role is still assigned to one or more users")
    })
    public ResponseEntity<RoleResponse> updateRole(
            @Parameter(description = "Role UUID") @PathVariable String roleId,
            @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(roleService.updateRole(roleId, request));
    }

    @DeleteMapping("/{roleId}")
    @Operation(summary = "Delete a role",
            description = "Blocked while the role is still actively held by any user — "
                    + "revoke it from every holder first (see the role-assignment endpoints "
                    + "under Users).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Role deleted"),
            @ApiResponse(responseCode = "404", description = "Role not found"),
            @ApiResponse(responseCode = "409", description = "Role is still assigned to one or more users")
    })
    public ResponseEntity<Void> deleteRole(
            @Parameter(description = "Role UUID") @PathVariable String roleId) {
        roleService.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }

    // ── Permission assignment ─────────────────────────────────────────────────

    @GetMapping("/{roleId}/permissions")
    @Operation(summary = "List permissions granted to a role")
    @ApiResponse(responseCode = "200", description = "Permissions returned")
    public ResponseEntity<List<PermissionResponse>> getRolePermissions(
            @Parameter(description = "Role UUID") @PathVariable String roleId) {
        return ResponseEntity.ok(roleService.getRolePermissions(roleId));
    }

    @PostMapping("/{roleId}/permissions/{permissionId}")
    @Operation(summary = "Grant a permission to a role")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Permission granted"),
            @ApiResponse(responseCode = "404", description = "Role or permission not found"),
            @ApiResponse(responseCode = "409", description = "Role already has this permission")
    })
    public ResponseEntity<Void> grantPermission(
            @Parameter(description = "Role UUID") @PathVariable String roleId,
            @Parameter(description = "Permission UUID") @PathVariable String permissionId) {
        roleService.grantPermission(roleId, permissionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    @Operation(summary = "Revoke a permission from a role")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Permission revoked"),
            @ApiResponse(responseCode = "404", description = "Role does not have this permission")
    })
    public ResponseEntity<Void> revokePermission(
            @Parameter(description = "Role UUID") @PathVariable String roleId,
            @Parameter(description = "Permission UUID") @PathVariable String permissionId) {
        roleService.revokePermission(roleId, permissionId);
        return ResponseEntity.noContent().build();
    }
}
