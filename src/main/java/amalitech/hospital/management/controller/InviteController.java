package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.config.security.AuthenticatedUser;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.invite.InviteRequest;
import amalitech.hospital.management.dto.invite.InviteResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.exception.runtime.UnauthorizedException;
import amalitech.hospital.management.service.InviteService;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.micrometer.core.annotation.Timed;

/**
 * Admin-only invitations that pre-authorize a role for an email address before that
 * person has ever registered (HMS v5) — backed by {@link InviteService}. See that class
 * for exception/transaction behavior; this layer only maps HTTP <-> DTOs.
 *
 * Every request handled here already carries a Bearer token belonging to whoever is
 * creating the invite — {@code createInvite} reads its {@code userId} straight off the
 * {@link AuthenticatedUser} principal rather than taking it as a request field, the
 * same pattern {@code AuthController.changePassword} already uses for "act as the
 * caller, not as whoever the request body claims to be."
 */
@RestController
@RequestMapping("/api/v1/invites")
@Tag(name = "Invites", description = "Admin-issued invitations pre-authorizing a role for an email")
@Timed(value = "hms.rest.requests", extraTags = {"layer", "rest"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    @PostMapping
    @Operation(summary = "Invite someone by email to register with a pre-chosen role",
            description = "No account is created here — nothing changes until this exact email "
                    + "address completes self-registration (POST /api/v1/auth/register) or logs in "
                    + "via Google for the first time, at which point the chosen role is assigned "
                    + "automatically instead of the generic Guest fallback.")
    @ApiResponse(responseCode = "201", description = "Invite created")
    @ApiResponse(responseCode = "404", description = "Role or inviting user not found")
    @ApiResponse(responseCode = "409", description = "That email already has an account, or an invite is already pending")
    @RequirePermission(resource = Resource.INVITES, action = PermissionAction.CREATE)
    public ResponseEntity<ApiResult<InviteResponse>> createInvite(
            Authentication authentication, @Valid @RequestBody InviteRequest request) {
        if (!(authentication.getPrincipal() instanceof AuthenticatedUser caller)) {
            throw new UnauthorizedException("No token provided");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Invite created", inviteService.createInvite(caller.userId(), request)));
    }

    @GetMapping
    @Operation(summary = "List pending invites (paginated)",
            description = "Only invites neither accepted nor revoked yet.")
    @ApiResponse(responseCode = "200", description = "Pending invites returned")
    @RequirePermission(resource = Resource.INVITES, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PagedModel<InviteResponse>>> getPendingInvites(Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Pending invites retrieved", inviteService.getPendingInvites(pageable)));
    }

    @DeleteMapping("/{inviteId}")
    @Operation(summary = "Revoke a pending invite",
            description = "Cancels an invite before it's ever consumed. A no-op safeguard, not an "
                    + "undo — an invite already accepted (the person already registered under it) "
                    + "can't be revoked after the fact.")
    @ApiResponse(responseCode = "204", description = "Invite revoked")
    @ApiResponse(responseCode = "404", description = "Invite not found")
    @ApiResponse(responseCode = "409", description = "Invite already accepted or already revoked")
    @RequirePermission(resource = Resource.INVITES, action = PermissionAction.DELETE)
    public ResponseEntity<Void> revokeInvite(
            @Parameter(description = "Invite UUID") @PathVariable String inviteId) {
        inviteService.revokeInvite(inviteId);
        return ResponseEntity.noContent().build();
    }
}
