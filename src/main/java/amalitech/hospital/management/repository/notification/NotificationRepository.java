package amalitech.hospital.management.repository.notification;

import amalitech.hospital.management.model.notification.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, String> {
}
