package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.lab.LabOrderRequest;
import amalitech.hospital.management.dto.lab.LabOrderResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.LabOrderService;
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
 * Lab order management — backed by {@link LabOrderService}. See that class for
 * caching/exception/transaction behavior; this layer only maps HTTP <-> DTOs. Results
 * are managed separately — see {@code LabResultController}.
 */
@RestController
@RequestMapping("/api/v1/lab-orders")
@Tag(name = "Lab Orders", description = "Lab test orders requested per appointment")
@Timed(value = "hms.rest.requests", extraTags = {"layer", "rest"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class LabOrderController {

    private final LabOrderService labOrderService;

    @GetMapping
    @Operation(summary = "List lab orders (paginated, sortable, filterable)",
            description = "Standard `?sort=property,direction` query param (e.g. `sort=orderedAt,desc`) "
                    + "— backed directly by Spring Data JPA, so any `LabOrder` field is sortable: "
                    + "`labOrderId`, `testName`, `status`, `orderedAt`, `updatedAt`. Unlike "
                    + "`/api/v1/users`, an unrecognized property is not validated ahead of time and "
                    + "currently surfaces as a 400 rather than silently falling back. Optional `status` "
                    + "query param filters the list — e.g. a lab technician's still-pending worklist.")
    @ApiResponse(responseCode = "200", description = "Lab orders returned")
    @Parameter(name = "sort", in = ParameterIn.QUERY,
            description = "Sort by property,direction. Possible properties: labOrderId, testName, "
                    + "status, orderedAt, updatedAt.",
            array = @ArraySchema(schema = @Schema(type = "string")), example = "orderedAt,desc")
    @RequirePermission(resource = Resource.LAB_ORDERS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PagedModel<LabOrderResponse>>> getLabOrders(
            Pageable pageable,
            @Parameter(description = "Filter by status: ordered, in_progress, completed, cancelled")
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResult.of("Lab orders retrieved", labOrderService.getLabOrders(pageable, status)));
    }

    @GetMapping("/{labOrderId}")
    @Operation(summary = "Get a lab order by id")
    @ApiResponse(responseCode = "200", description = "Lab order found")
    @ApiResponse(responseCode = "404", description = "Lab order not found")
    @RequirePermission(resource = Resource.LAB_ORDERS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<LabOrderResponse>> getLabOrder(
            @Parameter(description = "Lab order UUID") @PathVariable String labOrderId) {
        return ResponseEntity.ok(ApiResult.of("Lab order retrieved", labOrderService.getLabOrder(labOrderId)));
    }

    @PostMapping
    @Operation(summary = "Create a lab order")
    @ApiResponse(responseCode = "201", description = "Lab order created")
    @ApiResponse(responseCode = "404", description = "Appointment or doctor not found")
    @RequirePermission(resource = Resource.LAB_ORDERS, action = PermissionAction.CREATE)
    public ResponseEntity<ApiResult<LabOrderResponse>> createLabOrder(@Valid @RequestBody LabOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Lab order created", labOrderService.createLabOrder(request)));
    }

    @PutMapping("/{labOrderId}")
    @Operation(summary = "Update a lab order")
    @ApiResponse(responseCode = "200", description = "Lab order updated")
    @ApiResponse(responseCode = "404", description = "Lab order, appointment, or doctor not found")
    @RequirePermission(resource = Resource.LAB_ORDERS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<LabOrderResponse>> updateLabOrder(
            @Parameter(description = "Lab order UUID") @PathVariable String labOrderId,
            @Valid @RequestBody LabOrderRequest request) {
        return ResponseEntity.ok(ApiResult.of("Lab order updated", labOrderService.updateLabOrder(labOrderId, request)));
    }

    @DeleteMapping("/{labOrderId}")
    @Operation(summary = "Delete a lab order")
    @ApiResponse(responseCode = "204", description = "Lab order deleted")
    @ApiResponse(responseCode = "404", description = "Lab order not found")
    @RequirePermission(resource = Resource.LAB_ORDERS, action = PermissionAction.DELETE)
    public ResponseEntity<Void> deleteLabOrder(
            @Parameter(description = "Lab order UUID") @PathVariable String labOrderId) {
        labOrderService.deleteLabOrder(labOrderId);
        return ResponseEntity.noContent().build();
    }
}
