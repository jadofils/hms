package amalitech.hospital.management.repository.user;

import amalitech.hospital.management.model.user.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {
    List<UserSession> findByUser_UserIdAndIsActiveTrue(String userId);
}
