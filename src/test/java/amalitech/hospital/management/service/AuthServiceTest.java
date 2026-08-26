package amalitech.hospital.management.service;

import amalitech.hospital.management.aop.SystemLogWriter;
import amalitech.hospital.management.config.security.JwtService;
import amalitech.hospital.management.dto.auth.ChangePasswordRequest;
import amalitech.hospital.management.dto.auth.ForgotPasswordRequest;
import amalitech.hospital.management.dto.auth.LoginRequest;
import amalitech.hospital.management.dto.auth.LoginResponse;
import amalitech.hospital.management.dto.auth.ResetPasswordRequest;
import amalitech.hospital.management.event.PasswordChangedEvent;
import amalitech.hospital.management.event.PasswordResetRequestedEvent;
import amalitech.hospital.management.event.UserRoleMissingEvent;
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
import amalitech.hospital.management.repository.user.role.RoleRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SystemLogWriter systemLogWriter;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private HttpServletRequest httpServletRequest;
    @Mock private UserService userService;
    @Mock private InviteService inviteService;
    @Mock private RoleRepository roleRepository;
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
                passwordEncoder, jwtService, eventPublisher, systemLogWriter, redisTemplate,
                userService, inviteService, roleRepository, self, "http://localhost:3000");

        // HMS v5 — createGoogleProvisionedUser's role bootstrap runs on every
        // loginWithGoogle-creates-a-new-account call now; this lenient default makes it
        // a transparent no-op for tests that don't care about it specifically (see the
        // dedicated tests below for the ones that do).
        lenient().when(inviteService.consumeInviteIfAny(anyString())).thenReturn(Optional.empty());

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

    @Test
    void login_prefersEarliestNonGuestRole_evenWhenGuestWasAssignedFirst() {
        // The exact shape DataSeeder.seedPeople now produces: UserService.createUser
        // auto-grants Guest first, then the seeded caller's real role is assigned
        // afterward — the token must still say "Admin", not "Guest", purely because of
        // assignment order.
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("pw", "hashed-pw")).thenReturn(true);

        Role guestRole = new Role();
        guestRole.setRoleId("guest-role-id");
        guestRole.setRoleName("Guest");
        UserRole guestAssignment = assignment(guestRole);
        guestAssignment.setAssignedAt(LocalDateTime.now().minusMinutes(5));
        UserRole adminAssignment = assignment(adminRole);
        adminAssignment.setAssignedAt(LocalDateTime.now());
        when(userRoleRepository.findByIdUserId("user-1")).thenReturn(List.of(guestAssignment, adminAssignment));

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
    void login_fallsBackToGuest_whenGuestIsTheOnlyRoleHeld() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("pw", "hashed-pw")).thenReturn(true);
        Role guestRole = new Role();
        guestRole.setRoleId("guest-role-id");
        guestRole.setRoleName("Guest");
        when(userRoleRepository.findByIdUserId("user-1")).thenReturn(List.of(assignment(guestRole)));
        when(jwtService.getExpiryHours()).thenReturn(8L);
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> {
            UserSession session = inv.getArgument(0);
            session.setSessionId("session-1");
            return session;
        });
        when(jwtService.generateToken(anyString(), anyString(), eq("Guest"), anyString())).thenReturn("token");

        LoginResponse response = authService.login(loginRequest("alice", "pw"), httpServletRequest);

        assertThat(response.getRole()).isEqualTo("Guest");
    }

    @Test
    void login_notifiesEveryActiveAdmin_whenNoRoleAssignedAtAll() {
        // HMS v5 — a brand-new account always gets Guest automatically now, so reaching
        // "no assigned role" means every role (including Guest) was explicitly revoked
        // afterward — a genuine edge case worth an admin's attention.
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("pw", "hashed-pw")).thenReturn(true);
        when(userRoleRepository.findByIdUserId("user-1")).thenReturn(List.of());
        when(roleRepository.findByRoleName("Admin")).thenReturn(Optional.of(adminRole));

        User admin1 = new User();
        admin1.setUserId("admin-1");
        admin1.setUsername("admin1");
        admin1.setEmail("admin1@example.com");
        User admin2 = new User();
        admin2.setUserId("admin-2");
        admin2.setUsername("admin2");
        admin2.setEmail("admin2@example.com");
        UserRole adminAssignment1 = new UserRole();
        adminAssignment1.setUser(admin1);
        adminAssignment1.setRole(adminRole);
        UserRole adminAssignment2 = new UserRole();
        adminAssignment2.setUser(admin2);
        adminAssignment2.setRole(adminRole);
        UserRole revokedAdminAssignment = new UserRole();
        revokedAdminAssignment.setUser(admin2);
        revokedAdminAssignment.setRole(adminRole);
        revokedAdminAssignment.setRevokedAt(LocalDateTime.now());
        when(userRoleRepository.findByIdRoleId("role-1")).thenReturn(List.of(adminAssignment1, adminAssignment2));

        assertThatThrownBy(() -> authService.login(loginRequest("alice", "pw"), httpServletRequest))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("no assigned role");

        ArgumentCaptor<UserRoleMissingEvent> captor = ArgumentCaptor.forClass(UserRoleMissingEvent.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(captor.capture());
        List<String> notifiedAdminEmails = captor.getAllValues().stream()
                .map(UserRoleMissingEvent::getAdminEmail).toList();
        assertThat(notifiedAdminEmails).containsExactlyInAnyOrder("admin1@example.com", "admin2@example.com");
        assertThat(captor.getAllValues().get(0).getPendingUserEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void login_doesNotNotifyAnyone_whenNoAdminRoleSeededAtAll() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("pw", "hashed-pw")).thenReturn(true);
        when(userRoleRepository.findByIdUserId("user-1")).thenReturn(List.of());
        when(roleRepository.findByRoleName("Admin")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest("alice", "pw"), httpServletRequest))
                .isInstanceOf(UnauthorizedException.class);

        verify(eventPublisher, never()).publishEvent(any(UserRoleMissingEvent.class));
    }

    // ── loginWithGoogle ──────────────────────────────────────────────────────

    @Test
    void loginWithGoogle_createsANewUserAndAssignsTheDefaultGuestRole_whenNoAccountMatchesTheEmail() {
        when(userRepository.findByEmail("newperson@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("newperson")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setUserId("newperson-id"); // simulates @GeneratedValue on insert
            return saved;
        });
        when(passwordEncoder.encode(anyString())).thenReturn("random-bcrypt-hash");
        // Simulates what the (mocked) UserService.assignDefaultGuestRole would have
        // actually inserted in the real system — HMS v5, a brand-new Google login now
        // completes successfully instead of hitting "no assigned role".
        Role guestRole = new Role();
        guestRole.setRoleId("guest-role-id");
        guestRole.setRoleName("Guest");
        when(userRoleRepository.findByIdUserId(anyString())).thenReturn(List.of(assignment(guestRole)));
        when(jwtService.getExpiryHours()).thenReturn(8L);
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> {
            UserSession session = inv.getArgument(0);
            session.setSessionId("session-1");
            return session;
        });
        when(jwtService.generateToken(anyString(), anyString(), eq("Guest"), anyString())).thenReturn("signed-token");

        LoginResponse response = authService.loginWithGoogle(
                "newperson@example.com", "New Person", httpServletRequest);

        assertThat(response.getRole()).isEqualTo("Guest");
        verify(userService).assignDefaultGuestRole(any(User.class));
        verify(inviteService).consumeInviteIfAny("newperson@example.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User created = captor.getValue();
        assertThat(created.getEmail()).isEqualTo("newperson@example.com");
        assertThat(created.getUsername()).isEqualTo("newperson");
        assertThat(created.getEmailVerifiedAt()).isNotNull(); // Google already proved ownership
        assertThat(created.getPasswordHash()).isNotBlank(); // random, but never null (NOT NULL column)
    }

    @Test
    void loginWithGoogle_assignsTheInvitedRoleInstead_whenALiveInviteExistsForThisEmail() {
        when(userRepository.findByEmail("newperson@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("newperson")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setUserId("newperson-id"); // simulates @GeneratedValue on insert
            return saved;
        });
        when(passwordEncoder.encode(anyString())).thenReturn("random-bcrypt-hash");
        when(inviteService.consumeInviteIfAny("newperson@example.com")).thenReturn(Optional.of("doctor-role-id"));
        Role doctorRole = new Role();
        doctorRole.setRoleId("doctor-role-id");
        doctorRole.setRoleName("Doctor");
        when(userRoleRepository.findByIdUserId(anyString())).thenReturn(List.of(assignment(doctorRole)));
        when(jwtService.getExpiryHours()).thenReturn(8L);
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> {
            UserSession session = inv.getArgument(0);
            session.setSessionId("session-1");
            return session;
        });
        when(jwtService.generateToken(anyString(), anyString(), eq("Doctor"), anyString())).thenReturn("token");

        LoginResponse response = authService.loginWithGoogle(
                "newperson@example.com", "New Person", httpServletRequest);

        assertThat(response.getRole()).isEqualTo("Doctor");
        verify(userService).assignRoleToNewAccount(any(User.class), eq("doctor-role-id"));
        verify(userService, never()).assignDefaultGuestRole(any());
    }

    @Test
    void loginWithGoogle_appendsDigits_whenTheDerivedUsernameIsAlreadyTaken() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("alice")).thenReturn(true);
        when(userRepository.existsByUsername("alice2")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setUserId("alice2-id"); // simulates @GeneratedValue on insert
            return saved;
        });
        Role guestRole = new Role();
        guestRole.setRoleId("guest-role-id");
        guestRole.setRoleName("Guest");
        when(userRoleRepository.findByIdUserId(anyString())).thenReturn(List.of(assignment(guestRole)));
        when(jwtService.getExpiryHours()).thenReturn(8L);
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> {
            UserSession session = inv.getArgument(0);
            session.setSessionId("session-1");
            return session;
        });
        when(jwtService.generateToken(anyString(), anyString(), anyString(), anyString())).thenReturn("token");

        authService.loginWithGoogle("alice@example.com", "Alice", httpServletRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("alice2");
    }

    @Test
    void loginWithGoogle_logsInAnExistingAccount_withoutCreatingAnother() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser));
        when(userRoleRepository.findByIdUserId("user-1")).thenReturn(List.of(assignment(adminRole)));
        when(jwtService.getExpiryHours()).thenReturn(8L);
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> {
            UserSession session = inv.getArgument(0);
            session.setSessionId("session-1");
            return session;
        });
        when(jwtService.generateToken("user-1", "alice", "Admin", "session-1")).thenReturn("signed-token");

        LoginResponse response = authService.loginWithGoogle("alice@example.com", "Alice", httpServletRequest);

        assertThat(response.getToken()).isEqualTo("signed-token");
        assertThat(response.getUserId()).isEqualTo("user-1");
        verify(userRepository, never()).save(any(User.class)); // existing account, nothing new created
    }

    @Test
    void loginWithGoogle_retroactivelyVerifiesEmail_forAnExistingUnverifiedAccount() {
        existingUser.setEmailVerifiedAt(null);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser));
        when(userRoleRepository.findByIdUserId("user-1")).thenReturn(List.of(assignment(adminRole)));
        when(jwtService.getExpiryHours()).thenReturn(8L);
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> {
            UserSession session = inv.getArgument(0);
            session.setSessionId("session-1");
            return session;
        });
        when(jwtService.generateToken(anyString(), anyString(), anyString(), anyString())).thenReturn("token");

        authService.loginWithGoogle("alice@example.com", "Alice", httpServletRequest);

        assertThat(existingUser.getEmailVerifiedAt()).isNotNull();
        verify(userRepository).save(existingUser);
    }

    @Test
    void loginWithGoogle_throwsUnauthorized_forADeactivatedAccount() {
        existingUser.setIsActive(false);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> authService.loginWithGoogle("alice@example.com", "Alice", httpServletRequest))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("deactivated");
    }

    // ── security-event logging (HMS v4, Epic 5.2) ───────────────────────────

    @Test
    void login_logsASecurityEventOnFailure_namingTheAttemptedIdentifierButNeverThePassword() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrong-password", "hashed-pw")).thenReturn(false);
        when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.5");

        assertThatThrownBy(() -> authService.login(loginRequest("alice", "wrong-password"), httpServletRequest))
                .isInstanceOf(UnauthorizedException.class);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(systemLogWriter).record(eq("WARNING"), eq("AuthService.login.security-event"), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).contains("alice").contains("10.0.0.5")
                .doesNotContain("wrong-password");
    }

    @Test
    void login_logsASecurityEventOnSuccess() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("pw", "hashed-pw")).thenReturn(true);
        when(userRoleRepository.findByIdUserId("user-1")).thenReturn(List.of(assignment(adminRole)));
        when(jwtService.getExpiryHours()).thenReturn(8L);
        when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.5");
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> {
            UserSession session = inv.getArgument(0);
            session.setSessionId("session-1");
            return session;
        });
        when(jwtService.generateToken(anyString(), anyString(), anyString(), anyString())).thenReturn("token");

        authService.login(loginRequest("alice", "pw"), httpServletRequest);

        verify(systemLogWriter).record(eq("INFO"), eq("AuthService.login.security-event"), anyString());
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
        verify(eventPublisher, never()).publishEvent(any(PasswordResetRequestedEvent.class));
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

        verify(eventPublisher, never()).publishEvent(any(PasswordResetRequestedEvent.class));
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
        ArgumentCaptor<PasswordResetRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PasswordResetRequestedEvent event = eventCaptor.getValue();
        assertThat(event.getEmail()).isEqualTo("alice@example.com");
        assertThat(event.getRecipientName()).isEqualTo("alice");
        assertThat(event.getResetUrl()).startsWith("http://localhost:3000/reset-password?token=");
        assertThat(event.getExpiryMinutes()).isEqualTo(30);
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
        ArgumentCaptor<PasswordChangedEvent> eventCaptor = ArgumentCaptor.forClass(PasswordChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(eventCaptor.getValue().getRecipientName()).isEqualTo("alice");
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
        ArgumentCaptor<PasswordChangedEvent> eventCaptor = ArgumentCaptor.forClass(PasswordChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(eventCaptor.getValue().getRecipientName()).isEqualTo("alice");
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
