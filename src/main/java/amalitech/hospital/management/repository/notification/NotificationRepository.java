package amalitech.hospital.management.repository.notification;

import amalitech.hospital.management.model.notification.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, String> {
    // Backs NotificationService.getNotifications' optional ?unread= filter — "show me
    // only what I haven't dismissed yet" is the one real reason readAt exists at all
    // (see NotificationService's own Javadoc on markAsRead).
    Page<Notification> findByReadAtIsNull(Pageable pageable);
    Page<Notification> findByReadAtIsNotNull(Pageable pageable);
}
