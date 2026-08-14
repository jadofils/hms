package amalitech.hospital.management.repository.user;


import amalitech.hospital.management.model.user.UserRole;
import amalitech.hospital.management.model.user.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    List<UserRole> findByIdUserId(String userId);
    List<UserRole> findByIdRoleId(String roleId);
    Optional<UserRole> findByIdUserIdAndIdRoleId(String userId, String roleId);

    /** Whether any user currently, actively holds this role — see
     *  {@code RoleService.updateRole}/{@code deleteRole}, which block modifying a role
     *  while this is true. */
    boolean existsByIdRoleIdAndRevokedAtIsNull(String roleId);
}
