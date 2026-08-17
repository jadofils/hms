package amalitech.hospital.management.service;

import amalitech.hospital.management.config.security.JwtService;
import amalitech.hospital.management.dto.auth.ChangePasswordRequest;
import amalitech.hospital.management.dto.auth.ForgotPasswordRequest;
import amalitech.hospital.management.dto.auth.LoginRequest;
import amalitech.hospital.management.dto.auth.LoginResponse;
import amalitech.hospital.management.dto.auth.ResetPasswordRequest;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.exception.runtime.UnauthorizedException;
import amalitech.hospital.management.model.user.User;
import amalitech.hospital.management.model.user.UserRole;
import amalitech.hospital.management.model.user.UserSession;
import amalitech.hospital.management.model.user.role.Role;
import amalitech.hospital.management.repository.user.UserRepository;
import amalitech.hospital.management.repository.user.UserRoleRepository;
import amalitech.hospital.management.repository.user.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserSessionRepository userSessionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private MailService mailService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private HttpServletRequest httpServletRequest;
    // Stands in for the self-injected AOP proxy reference — findUserByEmail is
    // @FindUserData-annotated and normally intercepted by FindUserDataAspect; mocked
    // here at the boundary rather than exercised for real (see CLAUDE.md's Testing section).
    @Mock private AuthService self;

    private AuthService authService;

    private User existingUser;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, userRoleRepository, userSessionRepository,
                passwordEncoder, jwtService, mailService, redisTemplate, self, "http://localhost:3000");

        existingUser = new User();
        existingUser.setUserId("user-1");
        existingUser.setUsername("alice");
        existingUser.setEmail("alice@example.com");
        existingUser.setPasswordHash("hashed-pw");
        existingUser.setIsActive(true);
        existingUser.setEmailVerifiedAt(LocalDateTime.now()); // verified by default — see the dedicated gate tests below

        adminRole = new Role();
        adminRole.setRoleId("role-1");
        adminRole.setRoleName("Admin");
    }

    // ── login ────────────────────────────────────────────────────────────────

    @Test
    void login_throwsUnauthorized_whenUsernameNotFound() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        LoginRequest request = loginRequest("alice", "pw");

        assertThatThrownBy(() -> authService.login(request, httpServletRequest))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_throwsUnauthorized_whenPasswordWrong() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrong", "hashed-pw")).thenReturn(false);
        LoginRequest request = loginRequest("alice", "wrong");

        assertThatThrownBy(() -> authService.login(request, httpServletRequest))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_throwsUnauthorized_whenAccountSoftDeleted() {
        existingUser.setDeletedAt(LocalDateTime.now());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));
        LoginRequest request = loginRequest("alice", "pw");

        assertThatThrownBy(() -> authService.login(request, httpServletRequest))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_throwsUnauthorized_whenAccountInactive() {
        existingUser.setIsActive(false);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("pw", "hashed-pw")).thenReturn(true);
        LoginRequest request = loginRequest("alice", "pw");

        assertThatThrownBy(() -> authService.login(request, httpServletRequest))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("deactivated");
    }

    @Test
    void login_throwsUnauthorized_whenEmailNotVerified() {
        existingUser.setEmailVerifiedAt(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("pw", "hashed-pw")).thenReturn(true);
        LoginRequest request = loginRequest("alice", "pw");

        assertThatThrownBy(() -> authService.login(request, httpServletRequest))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("verify your email");
    }

    @Test
    void login_throwsUnauthorized_whenNoRoleAssigned() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("pw", "hashed-pw")).thenReturn(true);
        when(userRoleRepository.findByIdUserId("user-1")).thenReturn(List.of());
        LoginRequest request = loginRequest("alice", "pw");

        assertThatThrownBy(() -> authService.login(request, httpServletRequest))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("no assigned role");
    }

    @Test
    void login_success_persistsSessionAndIssuesTokenWithGeneratedSessionIdAsJti() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("pw", "hashed-pw")).thenReturn(true);
        when(userRoleRepository.findByIdUserId("user-1")).thenReturn(List.of(assignment(adminRole)));
        when(jwtService.getExpiryHours()).thenReturn(8L);
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("test-agent");
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> {
            UserSession session = inv.getArgument(0);
            session.setSessionId("session-1"); // simulates @GeneratedValue on insert
            return session;
        });
        when(jwtService.generateToken("user-1", "alice", "Admin", "session-1")).thenReturn("signed-token");

        LoginResponse response = authService.login(loginRequest("alice", "pw"), httpServletRequest);

        assertThat(response.getToken()).isEqualTo("signed-token");
        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getRole()).isEqualTo("Admin");
        verify(jwtService).generateToken("user-1", "alice", "Admin", "session-1");
    }

    @Test
    void login_fallsBackToEmailLookup_whenIdentifierIsNotAUsername() {
        when(userRepository.findByUsername("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("pw", "hashed-pw")).thenReturn(true);
        when(userRoleRepository.findByIdUserId("user-1")).thenReturn(List.of(assignment(adminRole)));
        when(jwtService.getExpiryHours()).thenReturn(8L);
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> {
            UserSession session = inv.getArgument(0);
            session.setSessionId("session-1");
            return session;
        });
        when(jwtService.generateToken(anyString(), anyString(), anyString(), anyString())).thenReturn("signed-token");

        LoginResponse response = authService.login(loginRequest("alice@example.com", "pw"), httpServletRequest);

        assertThat(response.getUserId()).isEqualTo("user-1");
        verify(userRepository).findByEmail("alice@example.com");
    }

    @Test
    void login_picksEarliestAssignedRole_whenUserHoldsMultipleRoles() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("pw", "hashed-pw")).thenReturn(true);

        Role laterRole = new Role();
        laterRole.setRoleId("role-2");
        laterRole.setRoleName("Receptionist");
        UserRole earlier = assignment(adminRole);
        earlier.setAssignedAt(LocalDateTime.now().minusDays(5));
        UserRole later = assignment(laterRole);
        later.setAssignedAt(LocalDateTime.now());
        when(userRoleRepository.findByIdUserId("user-1")).thenReturn(List.of(later, earlier));

        when(jwtService.getExpiryHours()).thenReturn(8L);
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> {
            UserSession session = inv.getArgument(0);
            session.setSessionId("session-1");
            return session;
        });
        when(jwtService.generateToken(anyString(), anyString(), anyString(), anyString())).thenReturn("token");

        LoginResponse response = authService.login(loginRequest("alice", "pw"), httpServletRequest);

        assertThat(response.getRole()).isEqualTo("Admin");
    }

    @Test
    void login_ignoresRevokedAssignments_whenPickingRole() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("pw", "hashed-pw")).thenReturn(true);
        UserRole revoked = assignment(adminRole);
        revoked.setRevokedAt(LocalDateTime.now());
        when(userRoleRepository.findByIdUserId("user-1")).thenReturn(List.of(revoked));
        LoginRequest request = loginRequest("alice", "pw");

        assertThatThrownBy(() -> authService.login(request, httpServletRequest))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ── logout ───────────────────────────────────────────────────────────────

    @Test
    void logout_blocklistsTokenAndRevokesSession() {
        Instant expiresAt = Instant.now().plusSeconds(60);
        JwtService.Identity identity = new JwtService.Identity("user-1", "alice", "Admin", "session-1", expiresAt);
        when(jwtService.verify("raw-token")).thenReturn(identity);

        UserSession session = new UserSession();
        session.setSessionId("session-1");
        session.setIsActive(true);
        when(userSessionRepository.findById("session-1")).thenReturn(Optional.of(session));

        authService.logout("raw-token");

        verify(jwtService).blocklist("session-1", expiresAt);
        assertThat(session.getIsActive()).isFalse();
        assertThat(session.getLogoutAt()).isNotNull();
        verify(userSessionRepository).save(session);
    }

    @Test
    void logout_stillBlocklists_evenWhenNoMatchingSessionRow() {
        Instant expiresAt = Instant.now().plusSeconds(60);
        JwtService.Identity identity = new JwtService.Identity("user-1", "alice", "Admin", "session-1", expiresAt);
        when(jwtService.verify("raw-token")).thenReturn(identity);
        when(userSessionRepository.findById("session-1")).thenReturn(Optional.empty());

        authService.logout("raw-token");

        verify(jwtService).blocklist("session-1", expiresAt);
        verify(userSessionRepository, never()).save(any());
    }

    // ── forgotPassword ───────────────────────────────────────────────────────

    @Test
    void forgotPassword_throwsNotFound_whenEmailNotOnFile() {
        org.mockito.Mockito.doReturn(List.of()).when(self).findUserByEmail("nobody@example.com");
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("nobody@example.com");

        assertThatThrownBy(() -> authService.forgotPassword(request))
                .isInstanceOf(NotFoundException.class);

        verify(redisTemplate, never()).opsForValue();
        verify(mailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void forgotPassword_throwsNotFound_whenUserSoftDeleted() {
        // The existence check itself already excludes soft-deleted rows (whereActive("u")
        // in FindUserDataAspect) — this covers the second guard in forgotPassword itself,
        // for the (should-never-happen-but-defense-in-depth) case where the two checks
        // disagree.
        existingUser.setDeletedAt(LocalDateTime.now());
        org.mockito.Mockito.doReturn(List.of()).when(self).findUserByEmail("alice@example.com");
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("alice@example.com");

        assertThatThrownBy(() -> authService.forgotPassword(request))
                .isInstanceOf(NotFoundException.class);

        verify(mailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void forgotPassword_storesTokenInRedisAndSendsEmail_whenUserFound() {
        org.mockito.Mockito.doReturn(List.of(new Object())).when(self).findUserByEmail("alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("alice@example.com");

        authService.forgotPassword(request);

        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.startsWith("password-reset:"),
                eq("user-1"),
                eq(Duration.ofMinutes(30)));
        verify(mailService).sendPasswordResetEmail(
                eq("alice@example.com"), eq("alice"), anyString(),
                org.mockito.ArgumentMatchers.startsWith("http://localhost:3000/reset-password?token="),
                eq(30));
    }

    // ── resetPassword ────────────────────────────────────────────────────────

    @Test
    void resetPassword_throwsBadRequest_whenTokenUnknown() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("password-reset:bogus")).thenReturn(null);
        ResetPasswordRequest request = resetRequest("bogus", "NewPassw0rd!");

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_updatesPasswordDeletesTokenAndRevokesActiveSessions() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("password-reset:good-token")).thenReturn("user-1");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.encode("NewPassw0rd!")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserSession activeSession = new UserSession();
        activeSession.setSessionId("session-1");
        activeSession.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(userSessionRepository.findByUser_UserIdAndIsActiveTrue("user-1")).thenReturn(List.of(activeSession));

        ResetPasswordRequest request = resetRequest("good-token", "NewPassw0rd!");

        authService.resetPassword(request);

        assertThat(existingUser.getPasswordHash()).isEqualTo("new-hash");
        verify(redisTemplate).delete("password-reset:good-token");
        verify(jwtService).blocklist(eq("session-1"), any(LocalDateTime.class));
        assertThat(activeSession.getIsActive()).isFalse();
        verify(userSessionRepository).save(activeSession);
        verify(mailService).sendPasswordChangedEmail(eq("alice@example.com"), eq("alice"), any(LocalDateTime.class));
    }

    @Test
    void resetPassword_throwsNotFound_whenUserBehindTokenNoLongerExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("password-reset:good-token")).thenReturn("ghost-user");
        when(userRepository.findById("ghost-user")).thenReturn(Optional.empty());
        ResetPasswordRequest request = resetRequest("good-token", "NewPassw0rd!");

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(NotFoundException.class);
    }

    // ── verifyEmail ──────────────────────────────────────────────────────────

    @Test
    void verifyEmail_throwsBadRequest_whenTokenUnknown() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("email-verify:bogus")).thenReturn(null);

        assertThatThrownBy(() -> authService.verifyEmail("bogus"))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmail_setsEmailVerifiedAtAndDeletesToken_whenTokenValid() {
        existingUser.setEmailVerifiedAt(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("email-verify:good-token")).thenReturn("user-1");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.verifyEmail("good-token");

        assertThat(existingUser.getEmailVerifiedAt()).isNotNull();
        verify(redisTemplate).delete("email-verify:good-token");
    }

    @Test
    void verifyEmail_throwsNotFound_whenUserBehindTokenNoLongerExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("email-verify:good-token")).thenReturn("ghost-user");
        when(userRepository.findById("ghost-user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("good-token"))
                .isInstanceOf(NotFoundException.class);
    }

    // ── changePassword ───────────────────────────────────────────────────────

    @Test
    void changePassword_throwsNotFound_whenUserAbsent() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());
        ChangePasswordRequest request = changeRequest("old", "NewPassw0rd!");

        assertThatThrownBy(() -> authService.changePassword("missing", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void changePassword_throwsUnauthorized_whenCurrentPasswordWrong() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrong", "hashed-pw")).thenReturn(false);
        ChangePasswordRequest request = changeRequest("wrong", "NewPassw0rd!");

        assertThatThrownBy(() -> authService.changePassword("user-1", request))
                .isInstanceOf(UnauthorizedException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_updatesHash_whenCurrentPasswordCorrect() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("old-pw", "hashed-pw")).thenReturn(true);
        when(passwordEncoder.encode("NewPassw0rd!")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        ChangePasswordRequest request = changeRequest("old-pw", "NewPassw0rd!");

        authService.changePassword("user-1", request);

        assertThat(existingUser.getPasswordHash()).isEqualTo("new-hash");
        verify(mailService).sendPasswordChangedEmail(eq("alice@example.com"), eq("alice"), any(LocalDateTime.class));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private static ResetPasswordRequest resetRequest(String token, String newPassword) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken(token);
        request.setNewPassword(newPassword);
        return request;
    }

    private static ChangePasswordRequest changeRequest(String current, String newPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(newPassword);
        return request;
    }

    private UserRole assignment(Role role) {
        UserRole userRole = new UserRole();
        userRole.setUser(existingUser);
        userRole.setRole(role);
        userRole.setAssignedAt(LocalDateTime.now());
        return userRole;
    }
}
