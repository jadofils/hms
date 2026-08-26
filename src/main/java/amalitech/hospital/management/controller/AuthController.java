package amalitech.hospital.management.controller;

import amalitech.hospital.management.config.security.AuthenticatedUser;
import amalitech.hospital.management.dto.auth.ChangePasswordRequest;
import amalitech.hospital.management.dto.auth.ForgotPasswordRequest;
import amalitech.hospital.management.dto.auth.LoginRequest;
import amalitech.hospital.management.dto.auth.LoginResponse;
import amalitech.hospital.management.dto.auth.MeResponse;
import amalitech.hospital.management.dto.auth.ResetPasswordRequest;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.user.UserRequest;
import amalitech.hospital.management.dto.user.UserResponse;
import amalitech.hospital.management.exception.runtime.UnauthorizedException;
import amalitech.hospital.management.service.AuthService;
import amalitech.hospital.management.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.micrometer.core.annotation.Timed;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Registration, login/logout, password reset, and current-session identity")
@Timed(value = "hms.rest.requests", extraTags = {"layer", "rest"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "Self-service account registration",
            description = "The account is granted a role immediately (HMS v5): a live admin invite "
                    + "(POST /api/v1/invites) for this exact email wins outright, otherwise it gets "
                    + "the generic read-only Guest role automatically. Either way, the email-"
                    + "verification link still has to be clicked before the account can log in.")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "409", description = "Username or email already taken")
    public ResponseEntity<ApiResult<UserResponse>> register(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Account created", userService.createUser(request)));
    }

    @GetMapping("/verify-email")
    @Operation(summary = "Confirm a self-registered account's email address",
            description = "Consumes the single-use link sent by POST /register. Does not itself log the "
                    + "caller in — a subsequent POST /login still needs the actual credentials — but the "
                    + "account already has a role (Guest or an invited one, see POST /register) by this point.")
    @ApiResponse(responseCode = "200", description = "Email verified")
    @ApiResponse(responseCode = "400", description = "Invalid or expired verification token")
    public ResponseEntity<ApiResult<Void>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResult.of("Email verified", null));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with username-or-email + password and receive a JWT",
            description = "The `username` field accepts either the account's username or its email.")
    @ApiResponse(responseCode = "200", description = "Authenticated")
    @ApiResponse(responseCode = "401", description = "Invalid credentials, disabled account, or no role assigned")
    public ResponseEntity<ApiResult<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResult.of("Authenticated", authService.login(request, httpRequest)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate the current request's Bearer token",
            description = "Blocklists the token's jti in Redis (self-expiring, same TTL as the token) "
                    + "and marks its backing session revoked.")
    @ApiResponse(responseCode = "204", description = "Logged out")
    @ApiResponse(responseCode = "401", description = "No token, or token invalid/expired")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("No token provided");
        }
        authService.logout(authHeader.substring(7));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset token by email",
            description = "Checks the email against the database first and reports outright if it isn't "
                    + "on file, rather than the more common anti-enumeration pattern of a generic response "
                    + "either way. The token itself is emailed to the address, not returned in this response.")
    @ApiResponse(responseCode = "200", description = "A reset token was sent to that email")
    @ApiResponse(responseCode = "404", description = "No account found with that email")
    public ResponseEntity<ApiResult<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResult.of("A reset token was sent to that email", null));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using the token from /forgot-password",
            description = "Single-use, 30-minute-lived token. Also revokes every currently active session "
                    + "for the account.")
    @ApiResponse(responseCode = "200", description = "Password reset")
    @ApiResponse(responseCode = "400", description = "Invalid or expired token")
    public ResponseEntity<ApiResult<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResult.of("Password reset", null));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change the current user's password (requires a valid Bearer token)")
    @ApiResponse(responseCode = "200", description = "Password changed")
    @ApiResponse(responseCode = "401", description = "Not authenticated, or current password incorrect")
    public ResponseEntity<ApiResult<Void>> changePassword(
            Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new UnauthorizedException("No token provided");
        }
        authService.changePassword(user.userId(), request);
        return ResponseEntity.ok(ApiResult.of("Password changed", null));
    }

    @GetMapping("/me")
    @Operation(summary = "The current caller's live profile",
            description = "username/email/isActive are a live, cache-backed lookup (see UserService.getUser) "
                    + "so they reflect a profile update (PUT /api/v1/users/{userId}) immediately — only "
                    + "`role` is the claim embedded in this request's own Bearer token at login time.")
    @ApiResponse(responseCode = "200", description = "Token present and valid")
    @ApiResponse(responseCode = "401", description = "No token, or token invalid/expired")
    public ResponseEntity<ApiResult<MeResponse>> me(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedUser(String userId, String username, String role))) {
            throw new UnauthorizedException("No token provided");
        }
        UserResponse user = userService.getUser(userId);
        return ResponseEntity.ok(ApiResult.of("Current session identity",
                new MeResponse(user.getUserId(), user.getUsername(), user.getEmail(), user.getIsActive(), role)));
    }
}
