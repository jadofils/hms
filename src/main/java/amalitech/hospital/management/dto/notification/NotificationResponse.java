package amalitech.hospital.management.dto.notification;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class NotificationResponse {
    private String notificationId;
    private String type;
    private String actorUserId;
    private String actorUsername;
    private List<String> recipients;
    private String payload;
    private String channels;
    private String status;
    private String priority;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
