package amalitech.hospital.management.repository.user.role;

import amalitech.hospital.management.model.user.role.RolePermission;
import amalitech.hospital.management.model.user.role.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
    List<RolePermission> findByIdRoleIdAndDeletedAtIsNull(String roleId);
    Optional<RolePermission> findByIdRoleIdAndIdPermissionId(String roleId, String permissionId);
}
