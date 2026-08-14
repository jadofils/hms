package amalitech.hospital.management.controller;

import amalitech.hospital.management.config.security.AuthenticatedUser;
import amalitech.hospital.management.dto.auth.ChangePasswordRequest;
import amalitech.hospital.management.dto.auth.ForgotPasswordRequest;
import amalitech.hospital.management.dto.auth.LoginRequest;
import amalitech.hospital.management.dto.auth.LoginResponse;
import amalitech.hospital.management.dto.auth.MeResponse;
import amalitech.hospital.management.dto.auth.ResetPasswordRequest;
import amalitech.hospital.management.dto.user.UserRequest;
import amalitech.hospital.management.dto.user.UserResponse;
import amalitech.hospital.management.exception.runtime.UnauthorizedException;
import amalitech.hospital.management.service.AuthService;
import amalitech.hospital.management.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Registration, login/logout, password reset, and current-session identity")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "Self-service account registration",
            description = "Creates the account only — it has no role yet, so it can't log in until an "
                    + "administrator assigns one via POST /api/v1/users/{userId}/roles/{roleId}.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "409", description = "Username or email already taken")
    })
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with username-or-email + password and receive a JWT",
            description = "The `username` field accepts either the account's username or its email.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials, disabled account, or no role assigned")
    })
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, httpRequest));
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate the current request's Bearer token",
            description = "Blocklists the token's jti in Redis (self-expiring, same TTL as the token) "
                    + "and marks its backing session revoked.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logged out"),
            @ApiResponse(responseCode = "401", description = "No token, or token invalid/expired")
    })
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("No token provided");
        }
        authService.logout(authHeader.substring(7));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset token by email",
            description = "Always returns 200 whether or not the email exists, to avoid account enumeration. "
                    + "The token is emailed to the address, not returned in this response.")
    @ApiResponse(responseCode = "200", description = "If that email exists, a reset token was sent")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using the token from /forgot-password",
            description = "Single-use, 30-minute-lived token. Also revokes every currently active session "
                    + "for the account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired token")
    })
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change the current user's password (requires a valid Bearer token)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed"),
            @ApiResponse(responseCode = "401", description = "Not authenticated, or current password incorrect")
    })
    public ResponseEntity<Void> changePassword(
            Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new UnauthorizedException("No token provided");
        }
        authService.changePassword(user.userId(), request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Identity carried by the current request's Bearer token, if any")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token present and valid"),
            @ApiResponse(responseCode = "401", description = "No token, or token invalid/expired")
    })
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new UnauthorizedException("No token provided");
        }
        return ResponseEntity.ok(new MeResponse(user.userId(), user.username(), user.role()));
    }
}
