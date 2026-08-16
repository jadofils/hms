package amalitech.hospital.management.repository.user.role;

import amalitech.hospital.management.model.user.role.RolePermission;
import amalitech.hospital.management.model.user.role.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
    List<RolePermission> findByIdRoleIdAndDeletedAtIsNull(String roleId);
    Optional<RolePermission> findByIdRoleIdAndIdPermissionId(String roleId, String permissionId);

    /**
     * Single-query check used by {@code aop.AuthorizationAspect} on every
     * {@code @RequirePermission}-annotated call — one JPQL round trip instead of
     * separately resolving role name -> role id -> permission id -> grant row.
     */
    @Query("""
            SELECT COUNT(rp) > 0 FROM RolePermission rp
            WHERE rp.role.roleName = :roleName AND rp.permission.resource = :resource
              AND rp.permission.action = :action AND rp.deletedAt IS NULL
              AND rp.role.deletedAt IS NULL AND rp.permission.deletedAt IS NULL
            """)
    boolean hasGrantedPermission(@Param("roleName") String roleName,
                                  @Param("resource") String resource,
                                  @Param("action") String action);
}
