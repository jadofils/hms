package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.user.role.permission.PermissionRequest;
import amalitech.hospital.management.dto.user.role.permission.PermissionResponse;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.user.role.Permission;
import amalitech.hospital.management.repository.user.role.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Permission (resource:action) CRUD.
 *
 * Single-item lookups are cached in Redis under the "permissions" cache; every write
 * invalidates the affected entry.
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

    @Transactional
    public PermissionResponse createPermission(PermissionRequest request) {
        if (permissionRepository.existsByResourceAndAction(request.getResource(), request.getAction())) {
            throw new ConflictException(
                    "Permission '" + request.getResource() + ":" + request.getAction() + "' already exists");
        }
        LocalDateTime now = LocalDateTime.now();
        Permission permission = new Permission();
        permission.setResource(request.getResource());
        permission.setAction(request.getAction());
        permission.setCreatedAt(now);
        permission.setUpdatedAt(now);
        return toResponse(permissionRepository.save(permission));
    }

    @Transactional
    @CachePut(value = "permissions", key = "#permissionId")
    public PermissionResponse updatePermission(String permissionId, PermissionRequest request) {
        Permission permission = findPermissionOrThrow(permissionId);
        boolean changed = !permission.getResource().equals(request.getResource())
                || !permission.getAction().equals(request.getAction());
        if (changed && permissionRepository.existsByResourceAndAction(request.getResource(), request.getAction())) {
            throw new ConflictException(
                    "Permission '" + request.getResource() + ":" + request.getAction() + "' already exists");
        }
        permission.setResource(request.getResource());
        permission.setAction(request.getAction());
        permission.setUpdatedAt(LocalDateTime.now());
        return toResponse(permissionRepository.save(permission));
    }

    @Transactional
    @CacheEvict(value = "permissions", key = "#permissionId")
    public void deletePermission(String permissionId) {
        Permission permission = findPermissionOrThrow(permissionId);
        permission.setDeletedAt(LocalDateTime.now());
        permissionRepository.save(permission);
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
