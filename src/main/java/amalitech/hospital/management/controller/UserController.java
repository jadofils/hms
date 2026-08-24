package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.user.AdminCreateUserRequest;
import amalitech.hospital.management.dto.user.PatchUserRequest;
import amalitech.hospital.management.dto.user.UserRequest;
import amalitech.hospital.management.dto.user.UserResponse;
import amalitech.hospital.management.dto.user.role.RoleResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.UserService;
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
 * User management — backed by {@link UserService}. See that class for caching/
 * exception/transaction behavior; this layer only maps HTTP <-> DTOs.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User account management")
@Timed(value = "hms.rest.requests", extraTags = {"layer", "rest"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "List users (paginated, sortable)",
            description = "Standard `?sort=property,direction` query param (e.g. "
                    + "`sort=username,desc`); only the first sort property is honored. "
                    + "Sortable columns: `userId`, `username`, `email`, `isActive`. An "
                    + "omitted or unrecognized column never errors — it falls back to `userId` "
                    + "ascending.")
    @ApiResponse(responseCode = "200", description = "Users returned")
    @Parameter(name = "sort", in = ParameterIn.QUERY,
            description = "Sort by property,direction. Possible properties: userId, username, email, isActive.",
            array = @ArraySchema(schema = @Schema(type = "string")), example = "username,desc")
    @RequirePermission(resource = Resource.USERS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PagedModel<UserResponse>>> getUsers(Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Users retrieved", userService.getUsers(pageable)));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get a user by id")
    @ApiResponse(responseCode = "200", description = "User found")
    @ApiResponse(responseCode = "404", description = "User not found")
    @RequirePermission(resource = Resource.USERS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<UserResponse>> getUser(
            @Parameter(description = "User UUID") @PathVariable String userId) {
        return ResponseEntity.ok(ApiResult.of("User retrieved", userService.getUser(userId)));
    }

    @PostMapping
    @Operation(summary = "Create a user",
            description = "The password is never supplied by the caller — a strong one is generated and "
                    + "emailed to the address given here.")
    @ApiResponse(responseCode = "201", description = "User created")
    @ApiResponse(responseCode = "409", description = "Username or email already taken")
    @RequirePermission(resource = Resource.USERS, action = PermissionAction.CREATE)
    public ResponseEntity<ApiResult<UserResponse>> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("User created", userService.createUserByAdmin(request)));
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Update a user")
    @ApiResponse(responseCode = "200", description = "User updated")
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "409", description = "Username or email already taken")
    @RequirePermission(resource = Resource.USERS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<UserResponse>> updateUser(
            @Parameter(description = "User UUID") @PathVariable String userId,
            @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(ApiResult.of("User updated", userService.updateUser(userId, request)));
    }

    @PatchMapping("/{userId}")
    @Operation(summary = "Partially update a user",
            description = "Unlike PUT — which overwrites every field with whatever the request carries — "
                    + "only the fields actually present in the request body are changed here; omitted "
                    + "fields are left exactly as they were. Has no `password` field at all — change a "
                    + "password exclusively through `/api/v1/auth/change-password` or the forgot/reset-"
                    + "password flow.")
    @ApiResponse(responseCode = "200", description = "User updated")
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "409", description = "Username or email already taken")
    @RequirePermission(resource = Resource.USERS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<UserResponse>> patchUser(
            @Parameter(description = "User UUID") @PathVariable String userId,
            @Valid @RequestBody PatchUserRequest request) {
        return ResponseEntity.ok(ApiResult.of("User updated", userService.patchUser(userId, request)));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Delete a user")
    @ApiResponse(responseCode = "204", description = "User deleted")
    @ApiResponse(responseCode = "404", description = "User not found")
    @RequirePermission(resource = Resource.USERS, action = PermissionAction.DELETE)
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "User UUID") @PathVariable String userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    // ── Role assignment ──────────────────────────────────────────────────────

    @GetMapping("/{userId}/roles")
    @Operation(summary = "List roles held by a user")
    @ApiResponse(responseCode = "200", description = "Roles returned")
    @RequirePermission(resource = Resource.USERS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<List<RoleResponse>>> getUserRoles(
            @Parameter(description = "User UUID") @PathVariable String userId) {
        return ResponseEntity.ok(ApiResult.of("Roles retrieved", userService.getUserRoles(userId)));
    }

    @PostMapping("/{userId}/roles/{roleId}")
    @Operation(summary = "Assign a role to a user")
    @ApiResponse(responseCode = "204", description = "Role assigned")
    @ApiResponse(responseCode = "404", description = "User or role not found")
    @ApiResponse(responseCode = "409", description = "User already holds this role")
    @RequirePermission(resource = Resource.USERS, action = PermissionAction.UPDATE)
    public ResponseEntity<Void> assignRole(
            @Parameter(description = "User UUID") @PathVariable String userId,
            @Parameter(description = "Role UUID") @PathVariable String roleId) {
        userService.assignRole(userId, roleId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}/roles/{roleId}")
    @Operation(summary = "Revoke a role from a user")
    @ApiResponse(responseCode = "204", description = "Role revoked")
    @ApiResponse(responseCode = "404", description = "User does not hold this role")
    @RequirePermission(resource = Resource.USERS, action = PermissionAction.UPDATE)
    public ResponseEntity<Void> revokeRole(
            @Parameter(description = "User UUID") @PathVariable String userId,
            @Parameter(description = "Role UUID") @PathVariable String roleId) {
        userService.revokeRole(userId, roleId);
        return ResponseEntity.noContent().build();
    }
}
