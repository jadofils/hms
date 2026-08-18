package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.user.role.permission.PermissionResponse;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.user.role.Permission;
import amalitech.hospital.management.repository.user.role.PermissionRepository;
import amalitech.hospital.management.utils.filters.PagedRawResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionService} is read-only — see its own Javadoc: permissions are a fixed,
 * system-managed catalog seeded by {@code DataSeeder}, with no create/update/delete
 * capability anywhere in the API.
 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock private PermissionRepository permissionRepository;
    // Stands in for the self-injected AOP proxy reference — findGrantedPermissionsPage is
    // @FindUserData-annotated and normally intercepted by FindUserDataAspect; mocked here
    // at the boundary rather than exercised for real (see CLAUDE.md's Testing section).
    @Mock private PermissionService self;

    private PermissionService permissionService;

    private Permission existingPermission;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService(permissionRepository, self);

        existingPermission = new Permission();
        existingPermission.setPermissionId("perm-1");
        existingPermission.setResource("patients");
        existingPermission.setAction("read");
    }

    @Test
    void getPermissions_mapsPageOfEntitiesToResponses() {
        Page<Permission> page = new PageImpl<>(List.of(existingPermission), PageRequest.of(0, 20), 1);
        when(permissionRepository.findAll(any(Pageable.class))).thenReturn(page);

        PagedModel<PermissionResponse> result = permissionService.getPermissions(PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getResource()).isEqualTo("patients");
    }

    @Test
    void getPermission_returnsMappedResponse() {
        when(permissionRepository.findById("perm-1")).thenReturn(Optional.of(existingPermission));

        PermissionResponse response = permissionService.getPermission("perm-1");

        assertThat(response.getResource()).isEqualTo("patients");
        assertThat(response.getAction()).isEqualTo("read");
    }

    @Test
    void getPermission_throwsNotFound_whenAbsent() {
        when(permissionRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.getPermission("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getPermission_throwsNotFound_whenSoftDeleted() {
        existingPermission.setDeletedAt(LocalDateTime.now());
        when(permissionRepository.findById("perm-1")).thenReturn(Optional.of(existingPermission));

        assertThatThrownBy(() -> permissionService.getPermission("perm-1"))
                .isInstanceOf(NotFoundException.class);
    }

    // ── getGrantedPermissions (AOP-driven pagination) ───────────────────────

    @Test
    void getGrantedPermissions_mapsRawRowsAndTotalIntoPagedModel() {
        Object[] row = {"perm-1", "patients", "read"};
        when(self.findGrantedPermissionsPage(0, 20, null, null))
                .thenReturn(new PagedRawResult(List.of((Object) row), 1L));

        PagedModel<PermissionResponse> result = permissionService.getGrantedPermissions(PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        PermissionResponse response = result.getContent().get(0);
        assertThat(response.getPermissionId()).isEqualTo("perm-1");
        assertThat(response.getResource()).isEqualTo("patients");
        assertThat(response.getAction()).isEqualTo("read");
        assertThat(result.getMetadata().totalElements()).isEqualTo(1);
    }

    @Test
    void getGrantedPermissions_passesRequestedSortColumnAndDirectionThrough() {
        when(self.findGrantedPermissionsPage(0, 20, "resource", "DESC"))
                .thenReturn(new PagedRawResult(List.of(), 0L));
        Pageable sorted = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "resource"));

        permissionService.getGrantedPermissions(sorted);

        verify(self).findGrantedPermissionsPage(0, 20, "resource", "DESC");
    }
}
