package amalitech.hospital.management.service;

import amalitech.hospital.management.annotation.FindUserData;
import amalitech.hospital.management.dto.user.role.permission.PermissionResponse;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.user.role.Permission;
import amalitech.hospital.management.repository.user.role.PermissionRepository;
import amalitech.hospital.management.utils.filters.PagedRawResult;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;

import java.util.List;

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

    /**
     * Self-injected proxy reference, used only to call this class's own
     * {@code @FindUserData}-annotated method through the Spring AOP proxy — see
     * {@link #findGrantedPermissionsPage}. {@code @Lazy} breaks the circular dependency
     * this creates at bean-creation time.
     */
    @Lazy
    private final PermissionService self;

    public PagedModel<PermissionResponse> getPermissions(Pageable pageable) {
        return new PagedModel<>(permissionRepository.findAll(pageable).map(this::toResponse));
    }

    /**
     * Permissions currently granted via at least one active role held by an active
     * user — distinct from {@link #getPermissions} (the entire fixed catalog, granted
     * or not). Useful for an admin audit view of what's actually in effect right now.
     * AOP-driven native SQL (see {@code FindUserDataAspect}'s {@code "permission"}
     * case), the same pattern {@code UserService.getUsers} uses.
     */
    public PagedModel<PermissionResponse> getGrantedPermissions(Pageable pageable) {
        Sort.Order order = pageable.getSort().stream().findFirst().orElse(null);
        String sortBy = order != null ? order.getProperty() : null;
        String sortDir = order != null ? order.getDirection().name() : null;
        PagedRawResult raw = self.findGrantedPermissionsPage(pageable.getPageNumber(), pageable.getPageSize(), sortBy, sortDir);
        List<PermissionResponse> content = raw.rows().stream()
                .map(row -> (Object[]) row)
                .map(cols -> {
                    PermissionResponse response = new PermissionResponse();
                    response.setPermissionId((String) cols[0]);
                    response.setResource((String) cols[1]);
                    response.setAction((String) cols[2]);
                    return response;
                })
                .toList();
        Page<PermissionResponse> page = new PageImpl<>(content, pageable, raw.total());
        return new PagedModel<>(page);
    }

    /**
     * AOP entry point for {@code FindUserDataAspect} — must be called via {@link #self},
     * never as {@code this.findGrantedPermissionsPage(...)}: Spring AOP proxies only
     * intercept calls made through the proxy, so a same-class call would bypass the
     * aspect and fall through to the body below.
     */
    @FindUserData(domain = "permission")
    public PagedRawResult findGrantedPermissionsPage(int page, int size, String sortBy, String sortDir) {
        throw new IllegalStateException("FindUserDataAspect did not intercept this call");
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
