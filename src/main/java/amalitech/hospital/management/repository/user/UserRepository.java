package amalitech.hospital.management.repository.user;

import amalitech.hospital.management.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    /** Active, non-deleted users with no {@code UserSession.loginAt} after
     *  {@code cutoff} — used by {@code MaintenanceService.deactivateIdleUsers}.
     *  {@code createdAt < cutoff} excludes brand-new accounts that simply haven't had a
     *  chance to log in yet from being immediately deactivated. */
    @Query("""
            SELECT u FROM User u
            WHERE u.isActive = true AND u.deletedAt IS NULL
              AND u.createdAt < :cutoff
              AND u.userId NOT IN (
                  SELECT s.user.userId FROM UserSession s WHERE s.loginAt > :cutoff
              )
            """)
    List<User> findActiveUsersIdleSince(@Param("cutoff") LocalDateTime cutoff);
}
