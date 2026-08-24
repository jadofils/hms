package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.notification.NotificationRequest;
import amalitech.hospital.management.dto.notification.NotificationResponse;
import amalitech.hospital.management.dto.notification.PatchNotificationRequest;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.micrometer.core.annotation.Timed;

/**
 * Notification management — backed by {@link NotificationService}. See that class for
 * caching/exception/transaction behavior; this layer only maps HTTP <-> DTOs.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "In-app notifications")
@Timed(value = "hms.rest.requests", extraTags = {"layer", "rest"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List notifications (paginated, sortable, filterable)",
            description = "Standard `?sort=property,direction` query param (e.g. `sort=createdAt,desc`) "
                    + "— backed directly by Spring Data JPA, so any `Notification` field is sortable: "
                    + "`notificationId`, `type`, `status`, `priority`, `readAt`, `createdAt`, `updatedAt`. "
                    + "Unlike `/api/v1/users`, an unrecognized property is not validated ahead of time "
                    + "and currently surfaces as a 400 rather than silently falling back. Optional "
                    + "`unread` query param filters to only unread (`true`) or only already-read "
                    + "(`false`) notifications — omitted returns every notification.")
    @ApiResponse(responseCode = "200", description = "Notifications returned")
    @Parameter(name = "sort", in = ParameterIn.QUERY,
            description = "Sort by property,direction. Possible properties: notificationId, type, "
                    + "status, priority, readAt, createdAt, updatedAt.",
            array = @ArraySchema(schema = @Schema(type = "string")), example = "createdAt,desc")
    @RequirePermission(resource = Resource.NOTIFICATIONS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PagedModel<NotificationResponse>>> getNotifications(
            Pageable pageable,
            @Parameter(description = "Filter to only unread (true) or only already-read (false) notifications",
                    example = "true")
            @RequestParam(required = false) Boolean unread) {
        return ResponseEntity.ok(ApiResult.of("Notifications retrieved", notificationService.getNotifications(pageable, unread)));
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

    @PatchMapping("/{notificationId}")
    @Operation(summary = "Partially update a notification",
            description = "Unlike PUT — which overwrites every field with whatever the request carries — "
                    + "only the fields actually present in the request body are changed here; omitted "
                    + "fields are left exactly as they were. Not to be confused with the narrower "
                    + "`PATCH /{notificationId}/read` below, which only ever touches `readAt`.")
    @ApiResponse(responseCode = "200", description = "Notification updated")
    @ApiResponse(responseCode = "404", description = "Notification or actor user not found")
    @RequirePermission(resource = Resource.NOTIFICATIONS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<NotificationResponse>> patchNotification(
            @Parameter(description = "Notification UUID") @PathVariable String notificationId,
            @Valid @RequestBody PatchNotificationRequest request) {
        return ResponseEntity.ok(ApiResult.of("Notification updated",
                notificationService.patchNotification(notificationId, request)));
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
