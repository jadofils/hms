package amalitech.hospital.management.config;

import amalitech.hospital.management.dto.user.UserRequest;
import amalitech.hospital.management.dto.user.UserResponse;
import amalitech.hospital.management.dto.user.role.RoleRequest;
import amalitech.hospital.management.dto.user.role.RoleResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.RoleName;
import amalitech.hospital.management.model.user.role.Permission;
import amalitech.hospital.management.repository.user.UserRepository;
import amalitech.hospital.management.repository.user.role.PermissionRepository;
import amalitech.hospital.management.repository.user.role.RoleRepository;
import amalitech.hospital.management.service.RoleService;
import amalitech.hospital.management.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every application-context test run in this suite (any {@code @SpringBootTest}) exercises
 * {@link DataSeeder#run} against an already-seeded database, so only its idempotent
 * "already exists, skip" branches ever run there. The "create it now" branches — the whole
 * point of the class on a genuinely empty database — never fire in that setup and can't be
 * forced through it. Mocked here instead, per CLAUDE.md's Testing convention (manually
 * constructed, no Spring context): every lookup reports "not found", so every create path runs.
 */
@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleService roleService;
    @Mock private UserService userService;

    private DataSeeder dataSeeder;

    @BeforeEach
    void setUp() {
        dataSeeder = new DataSeeder(roleRepository, permissionRepository, userRepository,
                roleService, userService);
    }

    @Test
    void run_createsEveryPermissionRoleAndSeedUser_onAFreshDatabase() {
        when(permissionRepository.findByResourceAndAction(anyString(), anyString())).thenReturn(Optional.empty());
        when(permissionRepository.save(any(Permission.class))).thenAnswer(inv -> {
            Permission saved = inv.getArgument(0);
            saved.setPermissionId("perm-id");
            return saved;
        });

        when(roleRepository.findByRoleName(anyString())).thenReturn(Optional.empty());
        RoleResponse roleResponse = new RoleResponse();
        roleResponse.setRoleId("role-id");
        when(roleService.createRole(any())).thenReturn(roleResponse);

        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        UserResponse userResponse = new UserResponse();
        userResponse.setUserId("user-id");
        when(userService.createUser(any())).thenReturn(userResponse);

        dataSeeder.run();

        int resourceCount = amalitech.hospital.management.enums.Resource.values().length;
        verify(permissionRepository, times(resourceCount * PermissionAction.values().length))
                .save(any(Permission.class));
        verify(roleService, times(RoleName.values().length)).createRole(any(RoleRequest.class));
        verify(userService, times(5)).createUser(any(UserRequest.class));
        verify(roleService, atLeastOnce()).grantPermission(eq("role-id"), eq("perm-id"));
        verify(userService, times(5)).assignRole(eq("user-id"), eq("role-id"));
    }
}
