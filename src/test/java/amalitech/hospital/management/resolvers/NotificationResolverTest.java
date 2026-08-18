package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.config.graphql.GraphQlConfig;
import amalitech.hospital.management.dto.notification.NotificationResponse;
import amalitech.hospital.management.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Slice test for {@link NotificationResolver} — see {@code UserResolverTest}'s Javadoc
 *  for the shared reasoning. */
@GraphQlTest(NotificationResolver.class)
@Import(GraphQlConfig.class)
class NotificationResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private NotificationService notificationService;

    private NotificationResponse existingNotification() {
        NotificationResponse response = new NotificationResponse();
        response.setNotificationId("notif-1");
        response.setType("appointment-created");
        response.setRecipients(List.of("patient-1"));
        response.setPriority("normal");
        return response;
    }

    @Test
    void notification_returnsMappedResponse() {
        when(notificationService.getNotification("notif-1")).thenReturn(existingNotification());

        graphQlTester.document("{ notification(notificationId: \"notif-1\") { type recipients priority } }")
                .execute()
                .path("notification.type").entity(String.class).isEqualTo("appointment-created")
                .path("notification.recipients").entityList(String.class).containsExactly("patient-1");

        verify(notificationService).getNotification("notif-1");
    }

    @Test
    void createNotification_delegatesToService() {
        when(notificationService.createNotification(any())).thenReturn(existingNotification());

        graphQlTester.document(
                        "mutation { createNotification(input: { type: \"appointment-created\", recipients: [\"patient-1\"] }) { notificationId } }")
                .execute()
                .path("createNotification.notificationId").entity(String.class).isEqualTo("notif-1");

        verify(notificationService).createNotification(any());
    }

    @Test
    void markNotificationAsRead_delegatesToService() {
        when(notificationService.markAsRead("notif-1")).thenReturn(existingNotification());

        graphQlTester.document("mutation { markNotificationAsRead(notificationId: \"notif-1\") { notificationId } }")
                .execute()
                .path("markNotificationAsRead.notificationId").entity(String.class).isEqualTo("notif-1");

        verify(notificationService).markAsRead("notif-1");
    }

    @Test
    void deleteNotification_returnsTrue() {
        graphQlTester.document("mutation { deleteNotification(notificationId: \"notif-1\") }")
                .execute()
                .path("deleteNotification").entity(Boolean.class).isEqualTo(true);

        verify(notificationService).deleteNotification("notif-1");
    }
}
