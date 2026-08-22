package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.user.AdminCreateUserRequest;
import amalitech.hospital.management.dto.user.UserRequest;
import amalitech.hospital.management.dto.user.UserResponse;
import amalitech.hospital.management.dto.user.role.RoleResponse;
import amalitech.hospital.management.event.AdminCreatedUserEvent;
import amalitech.hospital.management.event.UserRegisteredEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.web.PagedModel;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
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
    @Mock private RoleService roleService;
    @Mock private DoctorService doctorService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    // Stands in for the self-injected AOP proxy reference — findUsersPage is
    // @FindUserData-annotated and normally intercepted by FindUserDataAspect; mocked
    // here at the boundary rather than exercised for real (see CLAUDE.md's Testing section).
    @Mock private UserService self;

    private UserService userService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userRoleRepository, roleRepository, roleService,
                doctorService, passwordEncoder, eventPublisher, redisTemplate, "http://localhost:3000", 24L, self);

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

    @Test
    void getUsers_eagerLoadsActiveRolesAndLinkedDoctor_forEveryRowInThePage() {
        Object[] row = {"user-1", "alice", "alice@example.com", true};
        when(self.findUsersPage(0, 20, null, null)).thenReturn(new PagedRawResult(List.of((Object) row), 1L));

        Role adminRole = new Role();
        adminRole.setRoleId("role-1");
        adminRole.setRoleName("Admin");
        when(userRoleRepository.findByIdUserIdInAndRevokedAtIsNull(List.of("user-1")))
                .thenReturn(List.of(activeAssignment(adminRole)));

        amalitech.hospital.management.model.doctor.Doctor linkedDoctor =
                new amalitech.hospital.management.model.doctor.Doctor();
        linkedDoctor.setDoctorId("doctor-1");
        existingUser.setDoctor(linkedDoctor);
        when(userRepository.findAllById(List.of("user-1"))).thenReturn(List.of(existingUser));
        amalitech.hospital.management.dto.doctor.DoctorResponse doctorResponse =
                new amalitech.hospital.management.dto.doctor.DoctorResponse();
        doctorResponse.setDoctorId("doctor-1");
        when(doctorService.getDoctor("doctor-1")).thenReturn(doctorResponse);

        UserResponse response = userService.getUsers(PageRequest.of(0, 20)).getContent().get(0);

        assertThat(response.getRoles()).extracting(RoleResponse::getRoleName).containsExactly("Admin");
        assertThat(response.getDoctor()).isEqualTo(doctorResponse);
        // Listing shows role details, not each role's permissions (see
        // UserService.toShallowRoleResponse) — that stays single-item-only.
        assertThat(response.getRoles().get(0).getPermissions()).isNull();
        verify(roleService, never()).getRolePermissions(any());
    }

    @Test
    void getUsers_setsEmptyRolesAndNullDoctor_forAUserWithNeither() {
        Object[] row = {"user-1", "alice", "alice@example.com", true};
        when(self.findUsersPage(0, 20, null, null)).thenReturn(new PagedRawResult(List.of((Object) row), 1L));
        when(userRoleRepository.findByIdUserIdInAndRevokedAtIsNull(List.of("user-1"))).thenReturn(List.of());
        when(userRepository.findAllById(List.of("user-1"))).thenReturn(List.of(existingUser));

        UserResponse response = userService.getUsers(PageRequest.of(0, 20)).getContent().get(0);

        assertThat(response.getRoles()).isEmpty();
        assertThat(response.getDoctor()).isNull();
    }

    @Test
    void getUsers_resolvesADistinctRoleOrDoctorOnlyOnce_evenWhenSharedAcrossManyRowsOnThePage() {
        Object[] row1 = {"user-1", "alice", "alice@example.com", true};
        Object[] row2 = {"user-2", "bob", "bob@example.com", true};
        when(self.findUsersPage(0, 20, null, null))
                .thenReturn(new PagedRawResult(List.of((Object) row1, (Object) row2), 2L));

        Role sharedRole = new Role();
        sharedRole.setRoleId("role-1");
        sharedRole.setRoleName("Admin");
        UserRole assignment1 = activeAssignment(sharedRole);
        UserRole assignment2 = new UserRole();
        assignment2.setId(idFor("user-2", "role-1"));
        assignment2.setRole(sharedRole);
        assignment2.setAssignedAt(LocalDateTime.now());
        when(userRoleRepository.findByIdUserIdInAndRevokedAtIsNull(List.of("user-1", "user-2")))
                .thenReturn(List.of(assignment1, assignment2));

        User secondUser = new User();
        secondUser.setUserId("user-2");
        amalitech.hospital.management.model.doctor.Doctor sharedDoctor =
                new amalitech.hospital.management.model.doctor.Doctor();
        sharedDoctor.setDoctorId("doctor-1");
        existingUser.setDoctor(sharedDoctor);
        secondUser.setDoctor(sharedDoctor);
        when(userRepository.findAllById(List.of("user-1", "user-2")))
                .thenReturn(List.of(existingUser, secondUser));
        amalitech.hospital.management.dto.doctor.DoctorResponse doctorResponse =
                new amalitech.hospital.management.dto.doctor.DoctorResponse();
        doctorResponse.setDoctorId("doctor-1");
        when(doctorService.getDoctor("doctor-1")).thenReturn(doctorResponse);

        List<UserResponse> content = userService.getUsers(PageRequest.of(0, 20)).getContent();

        assertThat(content).extracting(UserResponse::getDoctor).containsOnly(doctorResponse);
        assertThat(content).flatExtracting(UserResponse::getRoles)
                .extracting(RoleResponse::getRoleName).containsExactly("Admin", "Admin");
        // One shared doctor across both rows, resolved exactly once, not once per row —
        // the whole point of the batching. Roles never call roleService at all in the
        // listing (see toShallowRoleResponse) — no permissions expansion to dedupe here.
        verify(roleService, never()).getRolePermissions(any());
        verify(doctorService, times(1)).getDoctor("doctor-1");
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
    void getUser_eagerLoadsActiveRoles() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));
        Role adminRole = new Role();
        adminRole.setRoleId("role-1");
        adminRole.setRoleName("Admin");
        when(userRoleRepository.findByIdUserId("user-1")).thenReturn(List.of(activeAssignment(adminRole)));

        UserResponse response = userService.getUser("user-1");

        assertThat(response.getRoles()).hasSize(1);
        assertThat(response.getRoles().get(0).getRoleName()).isEqualTo("Admin");
    }

    @Test
    void getUser_eagerLoadsEachRolesPermissions_unlikeAFlatIdNameRole() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));
        Role adminRole = new Role();
        adminRole.setRoleId("role-1");
        adminRole.setRoleName("Admin");
        when(userRoleRepository.findByIdUserId("user-1")).thenReturn(List.of(activeAssignment(adminRole)));
        amalitech.hospital.management.dto.user.role.permission.PermissionResponse permission =
                new amalitech.hospital.management.dto.user.role.permission.PermissionResponse();
        permission.setPermissionId("perm-1");
        when(roleService.getRolePermissions("role-1")).thenReturn(List.of(permission));

        UserResponse response = userService.getUser("user-1");

        assertThat(response.getRoles().get(0).getPermissions()).containsExactly(permission);
    }

    @Test
    void getUser_eagerLoadsLinkedDoctor_whenPresent() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));
        amalitech.hospital.management.model.doctor.Doctor linkedDoctor =
                new amalitech.hospital.management.model.doctor.Doctor();
        linkedDoctor.setDoctorId("doctor-1");
        existingUser.setDoctor(linkedDoctor);
        amalitech.hospital.management.dto.doctor.DoctorResponse doctorResponse =
                new amalitech.hospital.management.dto.doctor.DoctorResponse();
        doctorResponse.setDoctorId("doctor-1");
        when(doctorService.getDoctor("doctor-1")).thenReturn(doctorResponse);

        UserResponse response = userService.getUser("user-1");

        assertThat(response.getDoctor()).isEqualTo(doctorResponse);
    }

    @Test
    void getUser_leavesDoctorNull_whenNoneLinked() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));

        UserResponse response = userService.getUser("user-1");

        assertThat(response.getDoctor()).isNull();
        verify(doctorService, never()).getDoctor(anyString());
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
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        UserResponse response = userService.createUser(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-pw");
        assertThat(saved.getIsActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getEmailVerifiedAt()).isNull(); // not verified yet — the whole point of the new flow
        assertThat(response.getUsername()).isEqualTo("bob");
    }

    @Test
    void createUser_withEmail_generatesVerificationTokenAndSendsLink() {
        UserRequest request = requestFor("bob", "bob@example.com", "Passw0rd!");
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setUserId("user-generated-id"); // simulates @GeneratedValue on insert
            return saved;
        });
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        userService.createUser(request);

        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.startsWith("email-verify:"),
                eq("user-generated-id"),
                eq(Duration.ofHours(24)));
        ArgumentCaptor<UserRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        UserRegisteredEvent event = eventCaptor.getValue();
        assertThat(event.getEmail()).isEqualTo("bob@example.com");
        assertThat(event.getRecipientName()).isEqualTo("bob");
        assertThat(event.getVerifyUrl()).startsWith("http://localhost:3000/verify-email?token=");
        assertThat(event.getExpiryHours()).isEqualTo(24);
    }

    // ── createUserByAdmin ────────────────────────────────────────────────────

    @Test
    void createUserByAdmin_throwsConflict_whenUsernameTaken() {
        when(userRepository.existsByUsername("bob")).thenReturn(true);
        AdminCreateUserRequest request = adminRequestFor("bob", "bob@example.com");

        assertThatThrownBy(() -> userService.createUserByAdmin(request))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUserByAdmin_throwsConflict_whenEmailTaken() {
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(true);
        AdminCreateUserRequest request = adminRequestFor("bob", "bob@example.com");

        assertThatThrownBy(() -> userService.createUserByAdmin(request))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUserByAdmin_generatesPasswordThatSatisfiesTheComplexityPolicy_andMarksEmailVerified() {
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-generated-pw");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        AdminCreateUserRequest request = adminRequestFor("bob", "bob@example.com");

        UserResponse response = userService.createUserByAdmin(request);

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(passwordCaptor.capture());
        String generatedPassword = passwordCaptor.getValue();
        // Same complexity policy UserRequest.password's own @Pattern enforces.
        assertThat(generatedPassword)
                .hasSizeGreaterThanOrEqualTo(8)
                .matches("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmailVerifiedAt()).isNotNull();
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed-generated-pw");
        assertThat(response.getUsername()).isEqualTo("bob");

        ArgumentCaptor<AdminCreatedUserEvent> eventCaptor = ArgumentCaptor.forClass(AdminCreatedUserEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        AdminCreatedUserEvent event = eventCaptor.getValue();
        assertThat(event.getEmail()).isEqualTo("bob@example.com");
        assertThat(event.getRecipientName()).isEqualTo("bob");
        assertThat(event.getGeneratedPassword()).isEqualTo(generatedPassword);
    }

    @Test
    void createUserByAdmin_generatesADifferentPassword_everyCall() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.createUserByAdmin(adminRequestFor("bob1", "bob1@example.com"));
        userService.createUserByAdmin(adminRequestFor("bob2", "bob2@example.com"));

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder, times(2)).encode(passwordCaptor.capture());
        List<String> generated = passwordCaptor.getAllValues();
        assertThat(generated.get(0)).isNotEqualTo(generated.get(1));
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
    void updateUser_throwsConflict_whenNewEmailTaken() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);
        UserRequest request = requestFor("alice", "taken@example.com", "Passw0rd!");

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

    private static AdminCreateUserRequest adminRequestFor(String username, String email) {
        AdminCreateUserRequest request = new AdminCreateUserRequest();
        request.setUsername(username);
        request.setEmail(email);
        return request;
    }

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
