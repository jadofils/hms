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

    /** Batched form of {@link #findByIdUserId} for a whole set of user IDs at once —
     *  used by {@code UserService.attachRolesAndDoctors} to eager-load every user's
     *  active roles for a page of {@code getUsers} results in one query, instead of
     *  one {@code findByIdUserId} call per row. */
    List<UserRole> findByIdUserIdInAndRevokedAtIsNull(List<String> userIds);

    /** Whether any user currently, actively holds this role — see
     *  {@code RoleService.updateRole}/{@code deleteRole}, which block modifying a role
     *  while this is true. */
    boolean existsByIdRoleIdAndRevokedAtIsNull(String roleId);
}
