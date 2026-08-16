package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.aop.EventBus;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin toggle for the {@code @Subscribe}-driven event listeners registered in
 * {@link EventBus} — lists every registered subscriber and its current enabled state,
 * and lets an admin subscribe/unsubscribe one by its {@code name}. See
 * {@code NotificationEventListener} for the listeners currently registered.
 */
@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Events", description = "Domain-event subscriber administration")
@RequiredArgsConstructor
public class EventSubscriptionController {

    private final EventBus eventBus;

    @GetMapping
    @Operation(summary = "List every registered event subscriber and its enabled state")
    @ApiResponse(responseCode = "200", description = "Subscribers returned")
    @RequirePermission(resource = Resource.EVENTS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<List<EventBus.SubscriberStatus>>> getSubscribers() {
        return ResponseEntity.ok(ApiResult.of("Event subscribers retrieved", eventBus.listSubscribers()));
    }

    @PostMapping("/{name}/subscribe")
    @Operation(summary = "Enable an event subscriber")
    @ApiResponse(responseCode = "200", description = "Subscriber enabled")
    @ApiResponse(responseCode = "404", description = "No such subscriber")
    @RequirePermission(resource = Resource.EVENTS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<Void>> subscribe(
            @Parameter(description = "Subscriber name") @PathVariable String name) {
        eventBus.setEnabled(name, true);
        return ResponseEntity.ok(ApiResult.of("Subscriber '" + name + "' enabled", null));
    }

    @PostMapping("/{name}/unsubscribe")
    @Operation(summary = "Disable an event subscriber")
    @ApiResponse(responseCode = "200", description = "Subscriber disabled")
    @ApiResponse(responseCode = "404", description = "No such subscriber")
    @RequirePermission(resource = Resource.EVENTS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<Void>> unsubscribe(
            @Parameter(description = "Subscriber name") @PathVariable String name) {
        eventBus.setEnabled(name, false);
        return ResponseEntity.ok(ApiResult.of("Subscriber '" + name + "' disabled", null));
    }
}
