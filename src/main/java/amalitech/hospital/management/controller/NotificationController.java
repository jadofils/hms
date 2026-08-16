package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.notification.NotificationRequest;
import amalitech.hospital.management.dto.notification.NotificationResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Notification management — backed by {@link NotificationService}. See that class for
 * caching/exception/transaction behavior; this layer only maps HTTP <-> DTOs.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "In-app notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List notifications (paginated, sortable)")
    @ApiResponse(responseCode = "200", description = "Notifications returned")
    @RequirePermission(resource = Resource.NOTIFICATIONS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PagedModel<NotificationResponse>>> getNotifications(Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Notifications retrieved", notificationService.getNotifications(pageable)));
    }

    @GetMapping("/{notificationId}")
    @Operation(summary = "Get a notification by id")
    @ApiResponse(responseCode = "200", description = "Notification found")
    @ApiResponse(responseCode = "404", description = "Notification not found")
    @RequirePermission(resource = Resource.NOTIFICATIONS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<NotificationResponse>> getNotification(
            @Parameter(description = "Notification UUID") @PathVariable String notificationId) {
        return ResponseEntity.ok(ApiResult.of("Notification retrieved", notificationService.getNotification(notificationId)));
    }

    @PostMapping
    @Operation(summary = "Create a notification")
    @ApiResponse(responseCode = "201", description = "Notification created")
    @ApiResponse(responseCode = "404", description = "Actor user not found")
    @RequirePermission(resource = Resource.NOTIFICATIONS, action = PermissionAction.CREATE)
    public ResponseEntity<ApiResult<NotificationResponse>> createNotification(
            @Valid @RequestBody NotificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Notification created", notificationService.createNotification(request)));
    }

    @PutMapping("/{notificationId}")
    @Operation(summary = "Update a notification")
    @ApiResponse(responseCode = "200", description = "Notification updated")
    @ApiResponse(responseCode = "404", description = "Notification or actor user not found")
    @RequirePermission(resource = Resource.NOTIFICATIONS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<NotificationResponse>> updateNotification(
            @Parameter(description = "Notification UUID") @PathVariable String notificationId,
            @Valid @RequestBody NotificationRequest request) {
        return ResponseEntity.ok(ApiResult.of("Notification updated",
                notificationService.updateNotification(notificationId, request)));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "Mark a notification as read")
    @ApiResponse(responseCode = "200", description = "Notification marked read")
    @ApiResponse(responseCode = "404", description = "Notification not found")
    @RequirePermission(resource = Resource.NOTIFICATIONS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<NotificationResponse>> markAsRead(
            @Parameter(description = "Notification UUID") @PathVariable String notificationId) {
        return ResponseEntity.ok(ApiResult.of("Notification marked read", notificationService.markAsRead(notificationId)));
    }

    @DeleteMapping("/{notificationId}")
    @Operation(summary = "Delete a notification")
    @ApiResponse(responseCode = "204", description = "Notification deleted")
    @ApiResponse(responseCode = "404", description = "Notification not found")
    @RequirePermission(resource = Resource.NOTIFICATIONS, action = PermissionAction.DELETE)
    public ResponseEntity<Void> deleteNotification(
            @Parameter(description = "Notification UUID") @PathVariable String notificationId) {
        notificationService.deleteNotification(notificationId);
        return ResponseEntity.noContent().build();
    }
}
