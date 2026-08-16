package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.user.role.permission.PermissionRequest;
import amalitech.hospital.management.dto.user.role.permission.PermissionResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Permission management — backed by {@link PermissionService}. See that class for
 * caching/exception/transaction behavior; this layer only maps HTTP <-> DTOs.
 */
@RestController
@RequestMapping("/api/v1/permissions")
@Tag(name = "Permissions", description = "RBAC permission definitions")
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

    @PostMapping
    @Operation(summary = "Create a permission")
    @ApiResponse(responseCode = "201", description = "Permission created")
    @ApiResponse(responseCode = "409", description = "This resource:action already exists")
    @RequirePermission(resource = Resource.PERMISSIONS, action = PermissionAction.CREATE)
    public ResponseEntity<ApiResult<PermissionResponse>> createPermission(@Valid @RequestBody PermissionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Permission created", permissionService.createPermission(request)));
    }

    @PutMapping("/{permissionId}")
    @Operation(summary = "Update a permission")
    @ApiResponse(responseCode = "200", description = "Permission updated")
    @ApiResponse(responseCode = "404", description = "Permission not found")
    @ApiResponse(responseCode = "409", description = "This resource:action already exists")
    @RequirePermission(resource = Resource.PERMISSIONS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<PermissionResponse>> updatePermission(
            @Parameter(description = "Permission UUID") @PathVariable String permissionId,
            @Valid @RequestBody PermissionRequest request) {
        return ResponseEntity.ok(ApiResult.of("Permission updated", permissionService.updatePermission(permissionId, request)));
    }

    @DeleteMapping("/{permissionId}")
    @Operation(summary = "Delete a permission")
    @ApiResponse(responseCode = "204", description = "Permission deleted")
    @ApiResponse(responseCode = "404", description = "Permission not found")
    @RequirePermission(resource = Resource.PERMISSIONS, action = PermissionAction.DELETE)
    public ResponseEntity<Void> deletePermission(
            @Parameter(description = "Permission UUID") @PathVariable String permissionId) {
        permissionService.deletePermission(permissionId);
        return ResponseEntity.noContent().build();
    }
}
