package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.notification.NotificationRequest;
import amalitech.hospital.management.dto.notification.NotificationResponse;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.notification.Notification;
import amalitech.hospital.management.model.user.User;
import amalitech.hospital.management.repository.notification.NotificationRepository;
import amalitech.hospital.management.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;

    private NotificationService notificationService;

    private User existingActor;
    private Notification existingNotification;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, userRepository);

        existingActor = new User();
        existingActor.setUserId("user-1");
        existingActor.setUsername("admin");

        existingNotification = new Notification();
        existingNotification.setNotificationId("notif-1");
        existingNotification.setType("appointment-created");
        existingNotification.setActor(existingActor);
        existingNotification.setRecipients("[\"user-2\"]");
        existingNotification.setPriority("normal");
    }

    @Test
    void getNotification_returnsMappedResponse_whenFoundAndActive() {
        when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(existingNotification));

        NotificationResponse response = notificationService.getNotification("notif-1");

        assertThat(response.getNotificationId()).isEqualTo("notif-1");
        assertThat(response.getActorUserId()).isEqualTo("user-1");
        assertThat(response.getActorUsername()).isEqualTo("admin");
        assertThat(response.getRecipients()).containsExactly("user-2");
    }

    @Test
    void getNotification_throwsNotFound_whenAbsent() {
        when(notificationRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.getNotification("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getNotification_throwsNotFound_whenSoftDeleted() {
        existingNotification.setDeletedAt(LocalDateTime.now());
        when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(existingNotification));

        assertThatThrownBy(() -> notificationService.getNotification("notif-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createNotification_withoutActor_leavesActorFieldsNull() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        NotificationRequest request = requestFor(null, List.of("user-2"));

        NotificationResponse response = notificationService.createNotification(request);

        assertThat(response.getActorUserId()).isNull();
        assertThat(response.getPriority()).isEqualTo("normal");
    }

    @Test
    void createNotification_throwsNotFound_whenActorAbsent() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());
        NotificationRequest request = requestFor("missing", List.of("user-2"));

        assertThatThrownBy(() -> notificationService.createNotification(request))
                .isInstanceOf(NotFoundException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void createNotification_throwsNotFound_whenActorSoftDeleted() {
        existingActor.setDeletedAt(LocalDateTime.now());
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingActor));
        NotificationRequest request = requestFor("user-1", List.of("user-2"));

        assertThatThrownBy(() -> notificationService.createNotification(request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createNotification_withBlankActorUserId_leavesActorFieldsNull() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        NotificationRequest request = requestFor("   ", List.of("user-2"));

        NotificationResponse response = notificationService.createNotification(request);

        assertThat(response.getActorUserId()).isNull();
    }

    @Test
    void createNotification_withActor_savesActorAndRecipients() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingActor));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        NotificationRequest request = requestFor("user-1", List.of("user-2", "user-3"));

        NotificationResponse response = notificationService.createNotification(request);

        assertThat(response.getActorUserId()).isEqualTo("user-1");
        assertThat(response.getRecipients()).containsExactly("user-2", "user-3");
    }

    @Test
    void createNotification_throwsBadRequest_whenPayloadIsNotValidJson() {
        NotificationRequest request = requestFor(null, List.of("user-2"));
        request.setPayload("not-json{");

        assertThatThrownBy(() -> notificationService.createNotification(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createNotification_acceptsValidJsonPayloadChannelsAndStatus() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        NotificationRequest request = requestFor(null, List.of("user-2"));
        request.setPayload("{\"appointmentId\":\"appt-1\"}");
        request.setChannels("[\"email\",\"sms\"]");
        request.setStatus("{\"email\":\"sent\"}");

        NotificationResponse response = notificationService.createNotification(request);

        assertThat(response.getPayload()).isEqualTo("{\"appointmentId\":\"appt-1\"}");
        assertThat(response.getChannels()).isEqualTo("[\"email\",\"sms\"]");
        assertThat(response.getStatus()).isEqualTo("{\"email\":\"sent\"}");
    }

    @Test
    void updateNotification_throwsNotFound_whenAbsent() {
        when(notificationRepository.findById("missing")).thenReturn(Optional.empty());
        NotificationRequest request = requestFor(null, List.of("user-2"));

        assertThatThrownBy(() -> notificationService.updateNotification("missing", request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateNotification_appliesNewPriority_whenProvided() {
        when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(existingNotification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        NotificationRequest request = requestFor(null, List.of("user-2"));
        request.setPriority("high");

        NotificationResponse response = notificationService.updateNotification("notif-1", request);

        assertThat(response.getPriority()).isEqualTo("high");
    }

    @Test
    void markAsRead_setsReadAt() {
        when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(existingNotification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = notificationService.markAsRead("notif-1");

        assertThat(response.getReadAt()).isNotNull();
    }

    @Test
    void markAsRead_throwsNotFound_whenAbsent() {
        when(notificationRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteNotification_setsDeletedAt() {
        when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(existingNotification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.deleteNotification("notif-1");

        assertThat(existingNotification.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteNotification_throwsNotFound_whenAbsent() {
        when(notificationRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.deleteNotification("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    private static NotificationRequest requestFor(String actorUserId, List<String> recipients) {
        NotificationRequest request = new NotificationRequest();
        request.setType("appointment-created");
        request.setActorUserId(actorUserId);
        request.setRecipients(recipients);
        return request;
    }
}
