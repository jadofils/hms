package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.user.role.permission.PermissionResponse;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.user.role.Permission;
import amalitech.hospital.management.repository.user.role.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;

/**
 * Permission (resource:action) lookups — read-only. Permissions are a fixed,
 * system-managed catalog (every {@code Resource}<code>×</code>{@code PermissionAction}
 * combination — see {@code DataSeeder}, the sole writer of this table) rather than
 * something an admin creates/renames/deletes ad hoc, so there is deliberately no
 * create/update/delete capability here or in {@code PermissionController}.
 *
 * Single-item lookups are cached in Redis under the "permissions" cache.
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PagedModel<PermissionResponse> getPermissions(Pageable pageable) {
        return new PagedModel<>(permissionRepository.findAll(pageable).map(this::toResponse));
    }

    @Cacheable(value = "permissions", key = "#permissionId")
    public PermissionResponse getPermission(String permissionId) {
        return toResponse(findPermissionOrThrow(permissionId));
    }

    private Permission findPermissionOrThrow(String permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new NotFoundException("Permission not found: " + permissionId));
        if (permission.getDeletedAt() != null) {
            throw new NotFoundException("Permission not found: " + permissionId);
        }
        return permission;
    }

    private PermissionResponse toResponse(Permission permission) {
        PermissionResponse response = new PermissionResponse();
        response.setPermissionId(permission.getPermissionId());
        response.setResource(permission.getResource());
        response.setAction(permission.getAction());
        return response;
    }
}
