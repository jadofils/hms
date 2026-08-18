package amalitech.hospital.management.service;

import amalitech.hospital.management.annotation.ApplyAlgorithm;
import amalitech.hospital.management.annotation.FindUserData;
import amalitech.hospital.management.annotation.SqlQueryBuilder;
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
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Role CRUD + permission grants.
 *
 * Single-item lookups are cached in Redis under the "roles" cache; every write
 * invalidates the affected entry.
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;

    /**
     * Self-injected proxy reference, used only to call this class's own
     * {@code @ApplyAlgorithm}-annotated method ({@link #sort}) through the Spring AOP
     * proxy. {@code @Lazy} breaks the circular dependency this creates at bean-creation
     * time.
     */
    @Lazy
    private final RoleService self;

    public PagedModel<RoleResponse> getRoles(Pageable pageable) {
        return new PagedModel<>(roleRepository.findAll(pageable).map(this::toResponse));
    }

    /**
     * Roles currently held by at least one active user — distinct from {@link #getRoles}
     * (every role in the catalog, assigned or not). Useful for an admin audit/cleanup
     * view, or a "currently in-use" dropdown, without a caller needing to pull every
     * unassigned role along with it. AOP-driven native SQL (see
     * {@code FindUserDataAspect}'s {@code "role"} case), the same pattern
     * {@code UserService.getUsers} uses.
     */
    public PagedModel<RoleResponse> getAssignedRoles(Pageable pageable) {
        Sort.Order order = pageable.getSort().stream().findFirst().orElse(null);
        String sortBy = order != null ? order.getProperty() : null;
        String sortDir = order != null ? order.getDirection().name() : null;
        PagedRawResult raw = self.findAssignedRolesPage(pageable.getPageNumber(), pageable.getPageSize(), sortBy, sortDir);
        List<RoleResponse> content = raw.rows().stream()
                .map(row -> (Object[]) row)
                .map(cols -> {
                    RoleResponse response = new RoleResponse();
                    response.setRoleId((String) cols[0]);
                    response.setRoleName((String) cols[1]);
                    return response;
                })
                .toList();
        Page<RoleResponse> page = new PageImpl<>(content, pageable, raw.total());
        return new PagedModel<>(page);
    }

    /**
     * AOP entry point for {@code FindUserDataAspect} — must be called via {@link #self},
     * never as {@code this.findAssignedRolesPage(...)}: Spring AOP proxies only
     * intercept calls made through the proxy, so a same-class call would bypass the
     * aspect and fall through to the body below.
     */
    @FindUserData(domain = "role")
    public PagedRawResult findAssignedRolesPage(int page, int size, String sortBy, String sortDir) {
        throw new IllegalStateException("FindUserDataAspect did not intercept this call");
    }

    /**
     * Every active role plus how many permissions it currently holds — an admin-facing
     * summary so a role's grant footprint is visible at a glance, instead of paging
     * through {@link #getRoles} and calling {@link #getRolePermissions} once per role.
     * Backed by a {@code GROUP BY} native query (see {@code SqlQueryBuilderAspect}'s
     * {@code "findRolesWithPermissionCount"} case) rather than N+1 lookups.
     */
    public List<RolePermissionCountResponse> getRolePermissionSummary() {
        return self.findRolesWithPermissionCount().stream()
                .map(row -> {
                    RolePermissionCountResponse response = new RolePermissionCountResponse();
                    response.setRoleId((String) row[0]);
                    response.setRoleName((String) row[1]);
                    response.setPermissionCount(((Number) row[2]).longValue());
                    return response;
                })
                .toList();
    }

    /**
     * AOP entry point for {@code SqlQueryBuilderAspect} — must be called via
     * {@link #self}, never as {@code this.findRolesWithPermissionCount()}: Spring AOP
     * proxies only intercept calls made through the proxy, so a same-class call would
     * bypass the aspect and fall through to the body below.
     */
    @SqlQueryBuilder("findRolesWithPermissionCount")
    public List<Object[]> findRolesWithPermissionCount() {
        throw new IllegalStateException("SqlQueryBuilderAspect did not intercept this call");
    }

    @Cacheable(value = "roles", key = "#roleId")
    public RoleResponse getRole(String roleId) {
        RoleResponse response = toResponse(findRoleOrThrow(roleId));
        response.setPermissions(getRolePermissions(roleId));
        return response;
    }

    /**
     * Grants {@code request.getPermissionIds()} (if any) to the new role in the same
     * transaction — an unknown permission id throws (via {@link #grantPermission}) and
     * rolls the whole creation back, rather than leaving a role behind with only some of
     * the requested permissions.
     */
    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        if (roleRepository.existsByRoleName(request.getRoleName())) {
            throw new ConflictException("Role '" + request.getRoleName() + "' already exists");
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        Role role = new Role();
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        Role saved = roleRepository.save(role);

        if (request.getPermissionIds() != null) {
            for (String permissionId : request.getPermissionIds()) {
                // self.grantPermission(...), not this.grantPermission(...) — grantPermission
                // is @Transactional, and a same-class call bypasses that proxy advice too,
                // not just the custom @ApplyAlgorithm/@FindUserData/@SqlQueryBuilder aspects
                // this self-injected field otherwise exists for.
                self.grantPermission(saved.getRoleId(), permissionId);
            }
        }

        return toResponse(saved);
    }

    @Transactional
    @CachePut(value = "roles", key = "#roleId")
    public RoleResponse updateRole(String roleId, RoleRequest request) {
        Role role = findRoleOrThrow(roleId);
        throwIfAssignedToAnyUser(roleId, "updated");
        if (!role.getRoleName().equals(request.getRoleName())
                && roleRepository.existsByRoleName(request.getRoleName())) {
            throw new ConflictException("Role '" + request.getRoleName() + "' already exists");
        }
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return toResponse(roleRepository.save(role));
    }

    @Transactional
    @CacheEvict(value = "roles", key = "#roleId")
    public void deleteRole(String roleId) {
        Role role = findRoleOrThrow(roleId);
        throwIfAssignedToAnyUser(roleId, "deleted");
        role.setDeletedAt(LocalDateTime.now(ZoneId.systemDefault()));
        roleRepository.save(role);
    }

    /** A role currently held by at least one user can't be renamed or removed out from
     *  under them — revoke it from every holder first (see {@code UserService.revokeRole}),
     *  then update/delete it once nobody actively holds it. */
    private void throwIfAssignedToAnyUser(String roleId, String action) {
        if (userRoleRepository.existsByIdRoleIdAndRevokedAtIsNull(roleId)) {
            throw new ConflictException(
                    "Role cannot be " + action + " while it is still assigned to one or more users");
        }
    }

    // ── Permission grants ────────────────────────────────────────────────────

    /**
     * Returned sorted by "resource:action" — sorting is done via {@link #sort}, an
     * {@code @ApplyAlgorithm("mergeSort")} entry point, so the order is deterministic
     * for API consumers.
     */
    public List<PermissionResponse> getRolePermissions(String roleId) {
        findRoleOrThrow(roleId);
        List<PermissionResponse> permissions = new ArrayList<>(
                rolePermissionRepository.findByIdRoleIdAndDeletedAtIsNull(roleId).stream()
                        .map(rp -> toPermissionResponse(rp.getPermission()))
                        .toList());
        return self.sort(permissions, Comparator.comparing(RoleService::permissionKey));
    }

    private static String permissionKey(PermissionResponse permission) {
        return permission.getResource() + ":" + permission.getAction();
    }

    /**
     * AOP entry point for {@code AlgorithmAspect} — sorts {@code list} in place and
     * returns the same reference; {@code list} must be mutable. Must be called via
     * {@link #self}, never as {@code this.sort(...)}: Spring AOP proxies only intercept
     * calls made through the proxy, so a same-class call would bypass the aspect and
     * fall through to the body below.
     */
    @ApplyAlgorithm("mergeSort")
    public <T> List<T> sort(List<T> list, Comparator<T> comparator) {
        throw new IllegalStateException("AlgorithmAspect did not intercept this call");
    }

    @Transactional
    public void grantPermission(String roleId, String permissionId) {
        Role role = findRoleOrThrow(roleId);
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new NotFoundException("Permission not found: " + permissionId));

        RolePermission existing = rolePermissionRepository
                .findByIdRoleIdAndIdPermissionId(roleId, permissionId).orElse(null);
        if (existing != null) {
            if (existing.getDeletedAt() == null) {
                throw new ConflictException("Role already has this permission");
            }
            // Re-granting a previously revoked permission updates the existing join row —
            // (roleId, permissionId) is the composite PK, so a second insert would collide.
            existing.setDeletedAt(null);
            rolePermissionRepository.save(existing);
            return;
        }

        RolePermissionId id = new RolePermissionId();
        id.setRoleId(roleId);
        id.setPermissionId(permissionId);

        RolePermission rolePermission = new RolePermission();
        rolePermission.setId(id);
        rolePermission.setRole(role);
        rolePermission.setPermission(permission);
        rolePermission.setCreatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        rolePermissionRepository.save(rolePermission);
    }

    @Transactional
    public void revokePermission(String roleId, String permissionId) {
        RolePermission rolePermission = rolePermissionRepository
                .findByIdRoleIdAndIdPermissionId(roleId, permissionId)
                .filter(rp -> rp.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Role does not have this permission"));
        rolePermission.setDeletedAt(LocalDateTime.now(ZoneId.systemDefault()));
        rolePermissionRepository.save(rolePermission);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Role findRoleOrThrow(String roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Role not found: " + roleId));
        if (role.getDeletedAt() != null) {
            throw new NotFoundException("Role not found: " + roleId);
        }
        return role;
    }

    private RoleResponse toResponse(Role role) {
        RoleResponse response = new RoleResponse();
        response.setRoleId(role.getRoleId());
        response.setRoleName(role.getRoleName());
        response.setDescription(role.getDescription());
        return response;
    }

    private PermissionResponse toPermissionResponse(Permission permission) {
        PermissionResponse response = new PermissionResponse();
        response.setPermissionId(permission.getPermissionId());
        response.setResource(permission.getResource());
        response.setAction(permission.getAction());
        return response;
    }
}
