package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.user.role.RolePermissionCountResponse;
import amalitech.hospital.management.dto.user.role.RoleRequest;
import amalitech.hospital.management.dto.user.role.RoleResponse;
import amalitech.hospital.management.dto.user.role.permission.PermissionResponse;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.user.role.Permission;
import amalitech.hospital.management.model.user.role.Role;
import amalitech.hospital.management.model.user.role.RolePermission;
import amalitech.hospital.management.model.user.role.RolePermissionId;
import amalitech.hospital.management.repository.user.UserRoleRepository;
import amalitech.hospital.management.repository.user.role.PermissionRepository;
import amalitech.hospital.management.repository.user.role.RolePermissionRepository;
import amalitech.hospital.management.repository.user.role.RoleRepository;
import amalitech.hospital.management.utils.filters.PagedRawResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private UserRoleRepository userRoleRepository;
    // Stands in for the self-injected AOP proxy reference — sort is annotation-driven
    // and normally intercepted by AlgorithmAspect; mocked here at the boundary rather
    // than exercised for real (see CLAUDE.md's Testing section).
    @Mock private RoleService self;

    private RoleService roleService;

    private Role existingRole;

    @BeforeEach
    void setUp() {
        roleService = new RoleService(roleRepository, permissionRepository, rolePermissionRepository,
                userRoleRepository, self);

        existingRole = new Role();
        existingRole.setRoleId("role-1");
        existingRole.setRoleName("Admin");
        existingRole.setDescription("Full access");
    }

    /** Simulates AlgorithmAspect's real mergeSort behavior for tests that go through
     *  getRolePermissions (which mergeSorts via the mocked CollectionAlgorithmService). */
    @SuppressWarnings("unchecked")
    private void stubSortToActuallySort() {
        when(self.sort(any(), any())).thenAnswer(inv -> {
            List<Object> list = inv.getArgument(0);
            list.sort((Comparator<Object>) inv.getArgument(1));
            return list;
        });
    }

    // ── getRoles ─────────────────────────────────────────────────────────────

    @Test
    void getRoles_mapsPageOfEntitiesToResponses() {
        Page<Role> page = new PageImpl<>(List.of(existingRole), PageRequest.of(0, 20), 1);
        when(roleRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

        PagedModel<RoleResponse> result = roleService.getRoles(PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRoleName()).isEqualTo("Admin");
        assertThat(result.getContent().get(0).getDescription()).isEqualTo("Full access");
    }

    // ── getRole ──────────────────────────────────────────────────────────────

    @Test
    void getRole_returnsMappedResponse() {
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(existingRole));

        RoleResponse response = roleService.getRole("role-1");

        assertThat(response.getRoleId()).isEqualTo("role-1");
        assertThat(response.getRoleName()).isEqualTo("Admin");
    }

    @Test
    void getRole_eagerLoadsGrantedPermissions_unlikeThePaginatedListing() {
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(existingRole));
        Permission permission = new Permission();
        permission.setPermissionId("perm-1");
        permission.setResource("users");
        permission.setAction("read");
        RolePermission grant = new RolePermission();
        grant.setPermission(permission);
        when(rolePermissionRepository.findByIdRoleIdAndDeletedAtIsNull("role-1")).thenReturn(List.of(grant));
        stubSortToActuallySort();

        RoleResponse response = roleService.getRole("role-1");

        assertThat(response.getPermissions()).hasSize(1);
        assertThat(response.getPermissions().get(0).getResource()).isEqualTo("users");
    }

    @Test
    void getRole_throwsNotFound_whenAbsent() {
        when(roleRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.getRole("missing")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getRole_throwsNotFound_whenSoftDeleted() {
        existingRole.setDeletedAt(LocalDateTime.now());
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(existingRole));

        assertThatThrownBy(() -> roleService.getRole("role-1")).isInstanceOf(NotFoundException.class);
    }

    // ── createRole ───────────────────────────────────────────────────────────

    @Test
    void createRole_throwsConflict_whenNameTaken() {
        when(roleRepository.existsByRoleName("Admin")).thenReturn(true);
        RoleRequest request = requestFor("Admin", "desc");

        assertThatThrownBy(() -> roleService.createRole(request)).isInstanceOf(ConflictException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test
    void createRole_savesWithOptionalDescription() {
        when(roleRepository.existsByRoleName("Nurse")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));
        RoleRequest request = requestFor("Nurse", null);

        RoleResponse response = roleService.createRole(request);

        assertThat(response.getRoleName()).isEqualTo("Nurse");
        assertThat(response.getDescription()).isNull();
    }

    @Test
    void createRole_grantsEachPermission_whenPermissionIdsProvided() {
        // grantPermission is called as self.grantPermission(...) (see createRole's own
        // comment on why — not just this.grantPermission(...)), so its own cascade
        // (permissionRepository/rolePermissionRepository interactions) is exercised by
        // grantPermission's own dedicated tests below, not re-verified here; this only
        // checks createRole correctly delegates once per requested permission id.
        when(roleRepository.existsByRoleName("Nurse")).thenReturn(false);
        Role newRole = new Role();
        newRole.setRoleId("role-2");
        when(roleRepository.save(any(Role.class))).thenReturn(newRole);
        RoleRequest request = requestFor("Nurse", null);
        request.setPermissionIds(List.of("perm-1", "perm-2"));

        RoleResponse response = roleService.createRole(request);

        assertThat(response.getRoleId()).isEqualTo("role-2");
        verify(self).grantPermission("role-2", "perm-1");
        verify(self).grantPermission("role-2", "perm-2");
    }

    @Test
    void createRole_throwsNotFound_whenAPermissionIdDoesNotExist() {
        when(roleRepository.existsByRoleName("Nurse")).thenReturn(false);
        Role newRole = new Role();
        newRole.setRoleId("role-2");
        when(roleRepository.save(any(Role.class))).thenReturn(newRole);
        doThrow(new NotFoundException("Permission not found: bogus-perm"))
                .when(self).grantPermission("role-2", "bogus-perm");
        RoleRequest request = requestFor("Nurse", null);
        request.setPermissionIds(List.of("bogus-perm"));

        assertThatThrownBy(() -> roleService.createRole(request)).isInstanceOf(NotFoundException.class);
    }

    // ── updateRole ───────────────────────────────────────────────────────────

    @Test
    void updateRole_doesNotConflictCheck_whenNameUnchanged() {
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(existingRole));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));
        RoleRequest request = requestFor("Admin", "Updated description");

        RoleResponse response = roleService.updateRole("role-1", request);

        verify(roleRepository, never()).existsByRoleName(any());
        assertThat(response.getDescription()).isEqualTo("Updated description");
    }

    @Test
    void updateRole_throwsConflict_whenNewNameTaken() {
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(existingRole));
        when(roleRepository.existsByRoleName("SuperAdmin")).thenReturn(true);
        RoleRequest request = requestFor("SuperAdmin", "desc");

        assertThatThrownBy(() -> roleService.updateRole("role-1", request)).isInstanceOf(ConflictException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test
    void updateRole_throwsConflict_whenRoleIsAssignedToAUser() {
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(existingRole));
        when(userRoleRepository.existsByIdRoleIdAndRevokedAtIsNull("role-1")).thenReturn(true);
        RoleRequest request = requestFor("Admin", "Updated description");

        assertThatThrownBy(() -> roleService.updateRole("role-1", request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("still assigned");
        verify(roleRepository, never()).save(any());
    }

    // ── deleteRole ───────────────────────────────────────────────────────────

    @Test
    void deleteRole_setsDeletedAt() {
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(existingRole));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        roleService.deleteRole("role-1");

        assertThat(existingRole.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteRole_throwsConflict_whenRoleIsAssignedToAUser() {
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(existingRole));
        when(userRoleRepository.existsByIdRoleIdAndRevokedAtIsNull("role-1")).thenReturn(true);

        assertThatThrownBy(() -> roleService.deleteRole("role-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("still assigned");
        verify(roleRepository, never()).save(any());
    }

    // ── getAssignedRoles (AOP-driven pagination) ────────────────────────────

    @Test
    void getAssignedRoles_mapsRawRowsAndTotalIntoPagedModel() {
        Object[] row = {"role-1", "Admin"};
        when(self.findAssignedRolesPage(0, 20, null, null))
                .thenReturn(new PagedRawResult(List.of((Object) row), 1L));

        PagedModel<RoleResponse> result = roleService.getAssignedRoles(PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRoleId()).isEqualTo("role-1");
        assertThat(result.getContent().get(0).getRoleName()).isEqualTo("Admin");
        assertThat(result.getMetadata().totalElements()).isEqualTo(1);
    }

    @Test
    void getAssignedRoles_passesRequestedSortColumnAndDirectionThrough() {
        when(self.findAssignedRolesPage(0, 20, "roleName", "DESC"))
                .thenReturn(new PagedRawResult(List.of(), 0L));

        roleService.getAssignedRoles(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "roleName")));

        verify(self).findAssignedRolesPage(0, 20, "roleName", "DESC");
    }

    // ── getRolePermissionSummary (AOP-driven native query) ──────────────────

    @Test
    void getRolePermissionSummary_mapsRawRowsIntoResponses() {
        Object[] row = {"role-1", "Admin", 12L};
        when(self.findRolesWithPermissionCount()).thenReturn(List.<Object[]>of(row));

        List<RolePermissionCountResponse> result = roleService.getRolePermissionSummary();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRoleId()).isEqualTo("role-1");
        assertThat(result.get(0).getRoleName()).isEqualTo("Admin");
        assertThat(result.get(0).getPermissionCount()).isEqualTo(12L);
    }

    @Test
    void getRolePermissionSummary_returnsEmptyList_whenNoRoleExists() {
        when(self.findRolesWithPermissionCount()).thenReturn(List.of());

        assertThat(roleService.getRolePermissionSummary()).isEmpty();
    }

    // ── permission grants ────────────────────────────────────────────────────

    @Test
    void getRolePermissions_returnsMappedActiveGrants() {
        stubSortToActuallySort();
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(existingRole));
        Permission permission = new Permission();
        permission.setPermissionId("perm-1");
        permission.setResource("patients");
        permission.setAction("read");
        RolePermission grant = new RolePermission();
        grant.setPermission(permission);
        when(rolePermissionRepository.findByIdRoleIdAndDeletedAtIsNull("role-1")).thenReturn(List.of(grant));

        List<PermissionResponse> result = roleService.getRolePermissions("role-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getResource()).isEqualTo("patients");
        assertThat(result.get(0).getAction()).isEqualTo("read");
    }

    @Test
    void getRolePermissions_sortsByResourceThenAction() {
        stubSortToActuallySort();
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(existingRole));

        Permission writePatients = permissionOf("perm-2", "patients", "write");
        Permission readAppointments = permissionOf("perm-3", "appointments", "read");
        Permission readPatients = permissionOf("perm-1", "patients", "read");
        when(rolePermissionRepository.findByIdRoleIdAndDeletedAtIsNull("role-1")).thenReturn(List.of(
                grantOf(writePatients), grantOf(readAppointments), grantOf(readPatients)));

        List<PermissionResponse> result = roleService.getRolePermissions("role-1");

        assertThat(result).extracting(PermissionResponse::getResource, PermissionResponse::getAction)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("appointments", "read"),
                        org.assertj.core.groups.Tuple.tuple("patients", "read"),
                        org.assertj.core.groups.Tuple.tuple("patients", "write"));
    }

    @Test
    void grantPermission_throwsNotFound_whenPermissionAbsent() {
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(existingRole));
        when(permissionRepository.findById("perm-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.grantPermission("role-1", "perm-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void grantPermission_throwsConflict_whenAlreadyActivelyGranted() {
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(existingRole));
        Permission permission = new Permission();
        permission.setPermissionId("perm-1");
        when(permissionRepository.findById("perm-1")).thenReturn(Optional.of(permission));
        RolePermission existing = new RolePermission();
        when(rolePermissionRepository.findByIdRoleIdAndIdPermissionId("role-1", "perm-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> roleService.grantPermission("role-1", "perm-1"))
                .isInstanceOf(ConflictException.class);
        verify(rolePermissionRepository, never()).save(any());
    }

    @Test
    void grantPermission_reactivatesPreviouslyRevokedGrant_insteadOfInsertingNewRow() {
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(existingRole));
        Permission permission = new Permission();
        permission.setPermissionId("perm-1");
        when(permissionRepository.findById("perm-1")).thenReturn(Optional.of(permission));
        RolePermission revoked = new RolePermission();
        revoked.setDeletedAt(LocalDateTime.now());
        when(rolePermissionRepository.findByIdRoleIdAndIdPermissionId("role-1", "perm-1"))
                .thenReturn(Optional.of(revoked));

        roleService.grantPermission("role-1", "perm-1");

        assertThat(revoked.getDeletedAt()).isNull();
        verify(rolePermissionRepository).save(revoked);
    }

    @Test
    void grantPermission_createsNewGrant_whenNoneExists() {
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(existingRole));
        Permission permission = new Permission();
        permission.setPermissionId("perm-1");
        when(permissionRepository.findById("perm-1")).thenReturn(Optional.of(permission));
        when(rolePermissionRepository.findByIdRoleIdAndIdPermissionId("role-1", "perm-1"))
                .thenReturn(Optional.empty());

        roleService.grantPermission("role-1", "perm-1");

        ArgumentCaptor<RolePermission> captor = ArgumentCaptor.forClass(RolePermission.class);
        verify(rolePermissionRepository).save(captor.capture());
        RolePermission saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(idFor("role-1", "perm-1"));
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void revokePermission_throwsNotFound_whenNotGranted() {
        when(rolePermissionRepository.findByIdRoleIdAndIdPermissionId("role-1", "perm-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.revokePermission("role-1", "perm-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void revokePermission_setsDeletedAt() {
        RolePermission grant = new RolePermission();
        when(rolePermissionRepository.findByIdRoleIdAndIdPermissionId("role-1", "perm-1"))
                .thenReturn(Optional.of(grant));

        roleService.revokePermission("role-1", "perm-1");

        assertThat(grant.getDeletedAt()).isNotNull();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static RoleRequest requestFor(String roleName, String description) {
        RoleRequest request = new RoleRequest();
        request.setRoleName(roleName);
        request.setDescription(description);
        return request;
    }

    private static RolePermissionId idFor(String roleId, String permissionId) {
        RolePermissionId id = new RolePermissionId();
        id.setRoleId(roleId);
        id.setPermissionId(permissionId);
        return id;
    }

    private static Permission permissionOf(String id, String resource, String action) {
        Permission permission = new Permission();
        permission.setPermissionId(id);
        permission.setResource(resource);
        permission.setAction(action);
        return permission;
    }

    private static RolePermission grantOf(Permission permission) {
        RolePermission grant = new RolePermission();
        grant.setPermission(permission);
        return grant;
    }
}
