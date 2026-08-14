package amalitech.hospital.management.controller;

import amalitech.hospital.management.dto.user.UserRequest;
import amalitech.hospital.management.dto.user.UserResponse;
import amalitech.hospital.management.dto.user.role.RoleResponse;
import amalitech.hospital.management.service.UserService;
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
 * User management — backed by {@link UserService}. See that class for caching/
 * exception/transaction behavior; this layer only maps HTTP <-> DTOs.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User account management")
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
    public ResponseEntity<PagedModel<UserResponse>> getUsers(Pageable pageable) {

        return ResponseEntity.ok(userService.getUsers(pageable));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get a user by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserResponse> getUser(
            @Parameter(description = "User UUID") @PathVariable String userId) {
        return ResponseEntity.ok(userService.getUser(userId));
    }

    @PostMapping
    @Operation(summary = "Create a user")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created"),
            @ApiResponse(responseCode = "409", description = "Username or email already taken")
    })
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Update a user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "409", description = "Username or email already taken")
    })
    public ResponseEntity<UserResponse> updateUser(
            @Parameter(description = "User UUID") @PathVariable String userId,
            @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.updateUser(userId, request));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Delete a user")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User deleted"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "User UUID") @PathVariable String userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    // ── Role assignment ──────────────────────────────────────────────────────

    @GetMapping("/{userId}/roles")
    @Operation(summary = "List roles held by a user")
    @ApiResponse(responseCode = "200", description = "Roles returned")
    public ResponseEntity<List<RoleResponse>> getUserRoles(
            @Parameter(description = "User UUID") @PathVariable String userId) {
        return ResponseEntity.ok(userService.getUserRoles(userId));
    }

    @PostMapping("/{userId}/roles/{roleId}")
    @Operation(summary = "Assign a role to a user")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Role assigned"),
            @ApiResponse(responseCode = "404", description = "User or role not found"),
            @ApiResponse(responseCode = "409", description = "User already holds this role")
    })
    public ResponseEntity<Void> assignRole(
            @Parameter(description = "User UUID") @PathVariable String userId,
            @Parameter(description = "Role UUID") @PathVariable String roleId) {
        userService.assignRole(userId, roleId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}/roles/{roleId}")
    @Operation(summary = "Revoke a role from a user")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Role revoked"),
            @ApiResponse(responseCode = "404", description = "User does not hold this role")
    })
    public ResponseEntity<Void> revokeRole(
            @Parameter(description = "User UUID") @PathVariable String userId,
            @Parameter(description = "Role UUID") @PathVariable String roleId) {
        userService.revokeRole(userId, roleId);
        return ResponseEntity.noContent().build();
    }
}
