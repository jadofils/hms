package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.notification.NotificationRequest;
import amalitech.hospital.management.dto.notification.NotificationResponse;
import amalitech.hospital.management.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * GraphQL front door for {@link NotificationService} — see {@code UserResolver}'s
 * Javadoc for the shared reasoning (same service layer as REST, same request DTOs).
 */
@Controller
@Validated
@RequiredArgsConstructor
public class NotificationResolver {

    private final NotificationService notificationService;

    @QueryMapping
    public List<NotificationResponse> notifications(@Argument int page, @Argument int size) {
        return notificationService.getNotifications(PageRequest.of(page, size)).getContent();
    }

    @QueryMapping
    public NotificationResponse notification(@Argument String notificationId) {
        return notificationService.getNotification(notificationId);
    }

    @MutationMapping
    public NotificationResponse createNotification(@Argument @Valid NotificationRequest input) {
        return notificationService.createNotification(input);
    }

    @MutationMapping
    public NotificationResponse updateNotification(@Argument String notificationId, @Argument @Valid NotificationRequest input) {
        return notificationService.updateNotification(notificationId, input);
    }

    @MutationMapping
    public NotificationResponse markNotificationAsRead(@Argument String notificationId) {
        return notificationService.markAsRead(notificationId);
    }

    @MutationMapping
    public boolean deleteNotification(@Argument String notificationId) {
        notificationService.deleteNotification(notificationId);
        return true;
    }
}
