package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.user.role.permission.PermissionRequest;
import amalitech.hospital.management.dto.user.role.permission.PermissionResponse;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.user.role.Permission;
import amalitech.hospital.management.repository.user.role.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock private PermissionRepository permissionRepository;

    private PermissionService permissionService;

    private Permission existingPermission;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService(permissionRepository);

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

    @Test
    void createPermission_throwsConflict_whenResourceActionPairExists() {
        when(permissionRepository.existsByResourceAndAction("patients", "read")).thenReturn(true);
        PermissionRequest request = requestFor("patients", "read");

        assertThatThrownBy(() -> permissionService.createPermission(request))
                .isInstanceOf(ConflictException.class);
        verify(permissionRepository, never()).save(any());
    }

    @Test
    void createPermission_savesAndReturnsResponse() {
        when(permissionRepository.existsByResourceAndAction("appointments", "create")).thenReturn(false);
        when(permissionRepository.save(any(Permission.class))).thenAnswer(inv -> inv.getArgument(0));
        PermissionRequest request = requestFor("appointments", "create");

        PermissionResponse response = permissionService.createPermission(request);

        assertThat(response.getResource()).isEqualTo("appointments");
        assertThat(response.getAction()).isEqualTo("create");
    }

    @Test
    void updatePermission_doesNotConflictCheck_whenUnchanged() {
        when(permissionRepository.findById("perm-1")).thenReturn(Optional.of(existingPermission));
        when(permissionRepository.save(any(Permission.class))).thenAnswer(inv -> inv.getArgument(0));
        PermissionRequest request = requestFor("patients", "read");

        permissionService.updatePermission("perm-1", request);

        verify(permissionRepository, never()).existsByResourceAndAction(any(), any());
    }

    @Test
    void updatePermission_throwsConflict_whenChangedToExistingPair() {
        when(permissionRepository.findById("perm-1")).thenReturn(Optional.of(existingPermission));
        when(permissionRepository.existsByResourceAndAction("patients", "delete")).thenReturn(true);
        PermissionRequest request = requestFor("patients", "delete");

        assertThatThrownBy(() -> permissionService.updatePermission("perm-1", request))
                .isInstanceOf(ConflictException.class);
        verify(permissionRepository, never()).save(any());
    }

    @Test
    void updatePermission_updatesFields_whenChangedAndAvailable() {
        when(permissionRepository.findById("perm-1")).thenReturn(Optional.of(existingPermission));
        when(permissionRepository.existsByResourceAndAction("patients", "update")).thenReturn(false);
        when(permissionRepository.save(any(Permission.class))).thenAnswer(inv -> inv.getArgument(0));
        PermissionRequest request = requestFor("patients", "update");

        PermissionResponse response = permissionService.updatePermission("perm-1", request);

        assertThat(response.getAction()).isEqualTo("update");
    }

    @Test
    void deletePermission_setsDeletedAt() {
        when(permissionRepository.findById("perm-1")).thenReturn(Optional.of(existingPermission));
        when(permissionRepository.save(any(Permission.class))).thenAnswer(inv -> inv.getArgument(0));

        permissionService.deletePermission("perm-1");

        assertThat(existingPermission.getDeletedAt()).isNotNull();
    }

    @Test
    void deletePermission_throwsNotFound_whenAbsent() {
        when(permissionRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.deletePermission("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    private static PermissionRequest requestFor(String resource, String action) {
        PermissionRequest request = new PermissionRequest();
        request.setResource(resource);
        request.setAction(action);
        return request;
    }
}
