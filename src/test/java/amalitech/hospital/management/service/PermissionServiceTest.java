package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.user.role.permission.PermissionResponse;
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
import static org.mockito.Mockito.when;

/**
 * {@link PermissionService} is read-only — see its own Javadoc: permissions are a fixed,
 * system-managed catalog seeded by {@code DataSeeder}, with no create/update/delete
 * capability anywhere in the API.
 */
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
}
