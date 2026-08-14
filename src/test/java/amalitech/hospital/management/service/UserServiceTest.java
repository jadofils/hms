package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.user.UserRequest;
import amalitech.hospital.management.dto.user.UserResponse;
import amalitech.hospital.management.dto.user.role.RoleResponse;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.user.User;
import amalitech.hospital.management.model.user.UserRole;
import amalitech.hospital.management.model.user.UserRoleId;
import amalitech.hospital.management.model.user.role.Role;
import amalitech.hospital.management.repository.user.UserRepository;
import amalitech.hospital.management.repository.user.UserRoleRepository;
import amalitech.hospital.management.repository.user.role.RoleRepository;
import amalitech.hospital.management.utils.filters.PagedRawResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private MailService mailService;
    // Stands in for the self-injected AOP proxy reference — findUsersPage is
    // @FindUserData-annotated and normally intercepted by FindUserDataAspect; mocked
    // here at the boundary rather than exercised for real (see CLAUDE.md's Testing section).
    @Mock private UserService self;

    private UserService userService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userRoleRepository, roleRepository, passwordEncoder,
                mailService, "http://localhost:3000", self);

        existingUser = new User();
        existingUser.setUserId("user-1");
        existingUser.setUsername("alice");
        existingUser.setEmail("alice@example.com");
        existingUser.setPasswordHash("hashed");
        existingUser.setIsActive(true);
    }

    // ── getUsers (AOP-driven pagination) ────────────────────────────────────────

    @Test
    void getUsers_mapsRawRowsAndTotalIntoPagedModel() {
        Object[] row = {"user-1", "alice", "alice@example.com", true};
        // List.of((Object) row), not List.of(row) — the latter varargs-expands the
        // array into 4 elements instead of wrapping it as a single Object[] element.
        when(self.findUsersPage(0, 20, null, null)).thenReturn(new PagedRawResult(List.of((Object) row), 1L));

        PagedModel<UserResponse> result = userService.getUsers(PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        UserResponse response = result.getContent().get(0);
        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getIsActive()).isTrue();
        assertThat(result.getMetadata().totalElements()).isEqualTo(1);
    }

    @Test
    void getUsers_passesRequestedSortColumnAndDirectionThrough() {
        when(self.findUsersPage(0, 20, "username", "DESC")).thenReturn(new PagedRawResult(List.of(), 0L));
        Pageable sorted = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "username"));

        userService.getUsers(sorted);

        verify(self).findUsersPage(0, 20, "username", "DESC");
    }

    @Test
    void getUsers_returnsEmptyPage_whenNoRows() {
        when(self.findUsersPage(0, 20, null, null)).thenReturn(new PagedRawResult(List.of(), 0L));

        PagedModel<UserResponse> result = userService.getUsers(PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
    }

    // ── getUser ──────────────────────────────────────────────────────────────

    @Test
    void getUser_returnsMappedResponse_whenFoundAndActive() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));

        UserResponse response = userService.getUser("user-1");

        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getUsername()).isEqualTo("alice");
    }

    @Test
    void getUser_throwsNotFound_whenAbsent() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getUser_throwsNotFound_whenSoftDeleted() {
        existingUser.setDeletedAt(LocalDateTime.now());
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> userService.getUser("user-1"))
                .isInstanceOf(NotFoundException.class);
    }

    // ── createUser ───────────────────────────────────────────────────────────

    @Test
    void createUser_throwsConflict_whenUsernameTaken() {
        UserRequest request = requestFor("alice", "new@example.com", "Passw0rd!");
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_throwsConflict_whenEmailTaken() {
        UserRequest request = requestFor("bob", "alice@example.com", "Passw0rd!");
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_hashesPasswordAndSaves() {
        UserRequest request = requestFor("bob", "bob@example.com", "Passw0rd!");
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("hashed-pw");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.createUser(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-pw");
        assertThat(saved.getIsActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(response.getUsername()).isEqualTo("bob");
        verify(mailService).sendNotificationEmail(eq("bob@example.com"), eq("bob"), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void createUser_allowsNullEmail() {
        UserRequest request = requestFor("bob", null, "Passw0rd!");
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.createUser(request);

        verify(userRepository, never()).existsByEmail(anyString());
        verify(mailService, never()).sendNotificationEmail(any(), any(), any(), any(), any(), any(), any());
    }

    // ── updateUser ───────────────────────────────────────────────────────────

    @Test
    void updateUser_throwsNotFound_whenAbsent() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());
        UserRequest request = requestFor("alice", "alice@example.com", "Passw0rd!");

        assertThatThrownBy(() -> userService.updateUser("missing", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateUser_doesNotConflictCheck_whenUsernameUnchanged() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-pw");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        UserRequest request = requestFor("alice", "alice@example.com", "NewPassw0rd!");

        userService.updateUser("user-1", request);

        verify(userRepository, never()).existsByUsername(anyString());
    }

    @Test
    void updateUser_throwsConflict_whenNewUsernameTaken() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByUsername("carol")).thenReturn(true);
        UserRequest request = requestFor("carol", "alice@example.com", "Passw0rd!");

        assertThatThrownBy(() -> userService.updateUser("user-1", request))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_updatesFields() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByUsername("carol")).thenReturn(false);
        when(passwordEncoder.encode("NewPassw0rd!")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        UserRequest request = requestFor("carol", "alice@example.com", "NewPassw0rd!");

        UserResponse response = userService.updateUser("user-1", request);

        assertThat(response.getUsername()).isEqualTo("carol");
        assertThat(existingUser.getPasswordHash()).isEqualTo("new-hash");
    }

    // ── deleteUser ───────────────────────────────────────────────────────────

    @Test
    void deleteUser_setsDeletedAt() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.deleteUser("user-1");

        assertThat(existingUser.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteUser_throwsNotFound_whenAbsent() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    // ── role assignment ──────────────────────────────────────────────────────

    @Test
    void getUserRoles_returnsOnlyActiveAssignments() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));

        Role adminRole = new Role();
        adminRole.setRoleId("role-1");
        adminRole.setRoleName("Admin");
        UserRole active = activeAssignment(adminRole);

        Role revokedRole = new Role();
        revokedRole.setRoleId("role-2");
        revokedRole.setRoleName("Guest");
        UserRole revoked = activeAssignment(revokedRole);
        revoked.setRevokedAt(LocalDateTime.now());

        when(userRoleRepository.findByIdUserId("user-1")).thenReturn(List.of(active, revoked));

        List<RoleResponse> roles = userService.getUserRoles("user-1");

        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).getRoleName()).isEqualTo("Admin");
    }

    @Test
    void assignRole_throwsNotFound_whenRoleAbsent() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));
        when(roleRepository.findById("role-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.assignRole("user-1", "role-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void assignRole_throwsConflict_whenAlreadyActivelyAssigned() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));
        Role role = new Role();
        role.setRoleId("role-1");
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(role));
        UserRole existing = activeAssignment(role);
        when(userRoleRepository.findByIdUserIdAndIdRoleId("user-1", "role-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.assignRole("user-1", "role-1"))
                .isInstanceOf(ConflictException.class);
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void assignRole_reactivatesPreviouslyRevokedAssignment_insteadOfInsertingNewRow() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));
        Role role = new Role();
        role.setRoleId("role-1");
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(role));
        UserRole revoked = activeAssignment(role);
        revoked.setRevokedAt(LocalDateTime.now());
        when(userRoleRepository.findByIdUserIdAndIdRoleId("user-1", "role-1")).thenReturn(Optional.of(revoked));

        userService.assignRole("user-1", "role-1");

        assertThat(revoked.getRevokedAt()).isNull();
        verify(userRoleRepository).save(revoked);
    }

    @Test
    void assignRole_createsNewAssignment_whenNoneExists() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));
        Role role = new Role();
        role.setRoleId("role-1");
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(role));
        when(userRoleRepository.findByIdUserIdAndIdRoleId("user-1", "role-1")).thenReturn(Optional.empty());

        userService.assignRole("user-1", "role-1");

        ArgumentCaptor<UserRole> captor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(captor.capture());
        UserRole saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(idFor("user-1", "role-1"));
        assertThat(saved.getRevokedAt()).isNull();
    }

    @Test
    void revokeRole_throwsNotFound_whenNoActiveAssignment() {
        when(userRoleRepository.findByIdUserIdAndIdRoleId("user-1", "role-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.revokeRole("user-1", "role-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void revokeRole_setsRevokedAt() {
        Role role = new Role();
        role.setRoleId("role-1");
        UserRole assignment = activeAssignment(role);
        when(userRoleRepository.findByIdUserIdAndIdRoleId("user-1", "role-1")).thenReturn(Optional.of(assignment));

        userService.revokeRole("user-1", "role-1");

        assertThat(assignment.getRevokedAt()).isNotNull();
        verify(userRoleRepository, times(1)).save(assignment);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static UserRequest requestFor(String username, String email, String password) {
        UserRequest request = new UserRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private UserRole activeAssignment(Role role) {
        UserRole userRole = new UserRole();
        userRole.setId(idFor("user-1", role.getRoleId()));
        userRole.setUser(existingUser);
        userRole.setRole(role);
        userRole.setAssignedAt(LocalDateTime.now());
        return userRole;
    }

    private static UserRoleId idFor(String userId, String roleId) {
        UserRoleId id = new UserRoleId();
        id.setUserId(userId);
        id.setRoleId(roleId);
        return id;
    }
}
