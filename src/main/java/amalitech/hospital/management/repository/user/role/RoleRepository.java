package amalitech.hospital.management.repository.user.role;

import amalitech.hospital.management.model.user.role.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, String> {
    Optional<Role> findByRoleName(String roleName);
    boolean existsByRoleName(String roleName);
}
