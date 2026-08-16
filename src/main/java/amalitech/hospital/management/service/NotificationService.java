package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.notification.NotificationRequest;
import amalitech.hospital.management.dto.notification.NotificationResponse;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.notification.Notification;
import amalitech.hospital.management.model.user.User;
import amalitech.hospital.management.repository.notification.NotificationRepository;
import amalitech.hospital.management.repository.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Notification CRUD, plus marking one read.
 *
 * {@code recipients} has real structure (a list of recipient user ids), so it's exposed
 * as {@code List<String>} in the DTO and (de)serialized to/from the entity's {@code jsonb}
 * {@code String} column here; {@code payload}/{@code channels}/{@code status} have no
 * fixed shape (they vary by notification {@code type}), so they stay opaque JSON strings
 * — validated as parseable JSON, not parsed into a specific structure. Uses its own
 * {@code ObjectMapper} instance rather than a Spring-managed bean, same reasoning as
 * {@code CacheConfig}'s local mapper (this app has no {@code ObjectMapper} bean to reuse).
 *
 * Single-item lookups are cached in Redis under the "notifications" cache; every write
 * invalidates the affected entry.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String DEFAULT_RECIPIENTS = "[]";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PagedModel<NotificationResponse> getNotifications(Pageable pageable) {
        return new PagedModel<>(notificationRepository.findAll(pageable).map(this::toResponse));
    }

    @Cacheable(value = "notifications", key = "#notificationId")
    public NotificationResponse getNotification(String notificationId) {
        return toResponse(findNotificationOrThrow(notificationId));
    }

    @Transactional
    public NotificationResponse createNotification(NotificationRequest request) {
        User actor = request.getActorUserId() == null || request.getActorUserId().isBlank()
                ? null : findUserOrThrow(request.getActorUserId());

        LocalDateTime now = LocalDateTime.now();
        Notification notification = new Notification();
        notification.setType(request.getType());
        notification.setActor(actor);
        notification.setRecipients(writeRecipients(request.getRecipients()));
        notification.setPayload(validateJson(request.getPayload(), "payload"));
        notification.setChannels(validateJson(request.getChannels(), "channels"));
        notification.setStatus(validateJson(request.getStatus(), "status"));
        notification.setPriority(request.getPriority() == null || request.getPriority().isBlank()
                ? "normal" : request.getPriority().toLowerCase());
        notification.setCreatedAt(now);
        notification.setUpdatedAt(now);
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    @CachePut(value = "notifications", key = "#notificationId")
    public NotificationResponse updateNotification(String notificationId, NotificationRequest request) {
        Notification notification = findNotificationOrThrow(notificationId);
        User actor = request.getActorUserId() == null || request.getActorUserId().isBlank()
                ? null : findUserOrThrow(request.getActorUserId());

        notification.setType(request.getType());
        notification.setActor(actor);
        notification.setRecipients(writeRecipients(request.getRecipients()));
        notification.setPayload(validateJson(request.getPayload(), "payload"));
        notification.setChannels(validateJson(request.getChannels(), "channels"));
        notification.setStatus(validateJson(request.getStatus(), "status"));
        if (request.getPriority() != null && !request.getPriority().isBlank()) {
            notification.setPriority(request.getPriority().toLowerCase());
        }
        notification.setUpdatedAt(LocalDateTime.now());
        return toResponse(notificationRepository.save(notification));
    }

    /** The one real reason {@code readAt} exists — a caller dismissing their own
     *  notification. Idempotent: marking an already-read notification read again just
     *  refreshes the timestamp rather than erroring. */
    @Transactional
    @CachePut(value = "notifications", key = "#notificationId")
    public NotificationResponse markAsRead(String notificationId) {
        Notification notification = findNotificationOrThrow(notificationId);
        notification.setReadAt(LocalDateTime.now());
        notification.setUpdatedAt(LocalDateTime.now());
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    @CacheEvict(value = "notifications", key = "#notificationId")
    public void deleteNotification(String notificationId) {
        Notification notification = findNotificationOrThrow(notificationId);
        notification.setDeletedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Notification findNotificationOrThrow(String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification not found: " + notificationId));
        if (notification.getDeletedAt() != null) {
            throw new NotFoundException("Notification not found: " + notificationId);
        }
        return notification;
    }

    private User findUserOrThrow(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        if (user.getDeletedAt() != null) {
            throw new NotFoundException("User not found: " + userId);
        }
        return user;
    }

    private String writeRecipients(List<String> recipients) {
        try {
            return objectMapper.writeValueAsString(recipients);
        } catch (JsonProcessingException e) {
            // Never actually thrown for a List<String> — every element is already a
            // plain string, which Jackson always serializes successfully.
            throw new IllegalStateException("Failed to serialize recipients", e);
        }
    }

    private List<String> readRecipients(String recipientsJson) {
        try {
            return objectMapper.readValue(
                    recipientsJson == null ? DEFAULT_RECIPIENTS : recipientsJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse stored recipients", e);
        }
    }

    /** {@code payload}/{@code channels}/{@code status} have no fixed shape, so this only
     *  checks the caller sent *something parseable as JSON* rather than an arbitrary
     *  string that would violate the column's {@code jsonb} type at the DB layer. */
    private String validateJson(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            objectMapper.readTree(json);
            return json;
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Field '" + fieldName + "' must be valid JSON");
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.setNotificationId(notification.getNotificationId());
        response.setType(notification.getType());
        if (notification.getActor() != null) {
            response.setActorUserId(notification.getActor().getUserId());
            response.setActorUsername(notification.getActor().getUsername());
        }
        response.setRecipients(readRecipients(notification.getRecipients()));
        response.setPayload(notification.getPayload());
        response.setChannels(notification.getChannels());
        response.setStatus(notification.getStatus());
        response.setPriority(notification.getPriority());
        response.setReadAt(notification.getReadAt());
        response.setCreatedAt(notification.getCreatedAt());
        return response;
    }
}
