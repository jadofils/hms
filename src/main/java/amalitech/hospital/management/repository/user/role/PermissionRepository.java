package amalitech.hospital.management.repository.user.role;

import amalitech.hospital.management.model.user.role.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, String> {
    Optional<Permission> findByResourceAndAction(String resource, String action);
    boolean existsByResourceAndAction(String resource, String action);
}
