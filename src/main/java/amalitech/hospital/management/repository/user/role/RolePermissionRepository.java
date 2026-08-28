package amalitech.hospital.management.repository.user.role;

import amalitech.hospital.management.model.user.role.RolePermission;
import amalitech.hospital.management.model.user.role.RolePermissionId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    // @EntityGraph (HMS v5) — RoleService.getRolePermissions maps rp.getPermission()
    // per row (@ManyToOne(LAZY)); only surfaced once spring.jpa.open-in-view was
    // disabled (previously masked by OSIV keeping a session open for the whole request).
    @EntityGraph(attributePaths = "permission")
    List<RolePermission> findByIdRoleIdAndDeletedAtIsNull(String roleId);

    Optional<RolePermission> findByIdRoleIdAndIdPermissionId(String roleId, String permissionId);

    /**
     * Single-query check used by {@code aop.AuthorizationAspect}/{@code PermissionExpressions}
     * on every {@code @RequirePermission}/{@code @PreAuthorize} check — one JPQL round
     * trip instead of separately resolving role name -> role id -> permission id -> grant
     * row, and one query for however many roles the caller holds rather than one per
     * role: a permission is granted if <em>any</em> of {@code roleNames} grants it (a
     * user can hold several roles simultaneously — see CLAUDE.md's User↔Role
     * many-to-many note).
     */
    @Query("""
            SELECT COUNT(rp) > 0 FROM RolePermission rp
            WHERE rp.role.roleName IN :roleNames AND rp.permission.resource = :resource
              AND rp.permission.action = :action AND rp.deletedAt IS NULL
              AND rp.role.deletedAt IS NULL AND rp.permission.deletedAt IS NULL
            """)
    boolean hasGrantedPermission(@Param("roleNames") List<String> roleNames,
                                  @Param("resource") String resource,
                                  @Param("action") String action);
}
