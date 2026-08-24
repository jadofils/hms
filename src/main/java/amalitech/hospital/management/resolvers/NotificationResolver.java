package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.notification.NotificationRequest;
import amalitech.hospital.management.dto.notification.NotificationResponse;
import amalitech.hospital.management.dto.notification.PatchNotificationRequest;
import amalitech.hospital.management.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import amalitech.hospital.management.utils.GraphQlPaging;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import io.micrometer.core.annotation.Timed;

/**
 * GraphQL front door for {@link NotificationService} — see {@code UserResolver}'s
 * Javadoc for the shared reasoning (same service layer as REST, same request DTOs).
 */
@Controller
@Validated
@Timed(value = "hms.graphql.requests", extraTags = {"layer", "graphql"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class NotificationResolver {

    private final NotificationService notificationService;

    @QueryMapping
    public List<NotificationResponse> notifications(@Argument int page, @Argument int size, @Argument String sort,
            @Argument Boolean unread) {
        return notificationService.getNotifications(GraphQlPaging.of(page, size, sort), unread).getContent();
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
    public NotificationResponse patchNotification(@Argument String notificationId, @Argument @Valid PatchNotificationRequest input) {
        return notificationService.patchNotification(notificationId, input);
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
