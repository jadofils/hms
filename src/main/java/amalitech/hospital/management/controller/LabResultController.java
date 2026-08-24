package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.lab.LabResultRequest;
import amalitech.hospital.management.dto.lab.LabResultResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.LabResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.micrometer.core.annotation.Timed;

/**
 * The single result for a lab order — backed by {@link LabResultService}. See that
 * class for exception/transaction behavior; this layer only maps HTTP <-> DTOs. A
 * singular sub-resource (no list endpoint): {@code lab_order_id} carries a hard,
 * one-to-one DB constraint, so at most one result ever exists per lab order.
 */
@RestController
@RequestMapping("/api/v1/lab-orders/{labOrderId}/result")
@Tag(name = "Lab Results", description = "The result recorded for a lab order")
@Timed(value = "hms.rest.requests", extraTags = {"layer", "rest"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class LabResultController {

    private final LabResultService labResultService;

    @GetMapping
    @Operation(summary = "Get a lab order's result")
    @ApiResponse(responseCode = "200", description = "Result found")
    @ApiResponse(responseCode = "404", description = "Lab order or result not found")
    @RequirePermission(resource = Resource.LAB_RESULTS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<LabResultResponse>> getResult(
            @Parameter(description = "Lab order UUID") @PathVariable String labOrderId) {
        return ResponseEntity.ok(ApiResult.of("Result retrieved", labResultService.getResult(labOrderId)));
    }

    @PostMapping
    @Operation(summary = "Record a lab order's result")
    @ApiResponse(responseCode = "201", description = "Result created")
    @ApiResponse(responseCode = "404", description = "Lab order not found")
    @ApiResponse(responseCode = "409", description = "Lab order already has a result")
    @RequirePermission(resource = Resource.LAB_RESULTS, action = PermissionAction.CREATE)
    public ResponseEntity<ApiResult<LabResultResponse>> createResult(
            @Parameter(description = "Lab order UUID") @PathVariable String labOrderId,
            @Valid @RequestBody LabResultRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Result created", labResultService.createResult(labOrderId, request)));
    }

    @PutMapping
    @Operation(summary = "Update a lab order's result")
    @ApiResponse(responseCode = "200", description = "Result updated")
    @ApiResponse(responseCode = "404", description = "Lab order or result not found")
    @RequirePermission(resource = Resource.LAB_RESULTS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<LabResultResponse>> updateResult(
            @Parameter(description = "Lab order UUID") @PathVariable String labOrderId,
            @Valid @RequestBody LabResultRequest request) {
        return ResponseEntity.ok(ApiResult.of("Result updated", labResultService.updateResult(labOrderId, request)));
    }

    @PatchMapping
    @Operation(summary = "Partially update a lab order's result",
            description = "Unlike PUT — which overwrites every field with whatever the request carries, "
                    + "including null for anything omitted — only the fields actually present in the request "
                    + "body are changed here; omitted fields are left exactly as they were.")
    @ApiResponse(responseCode = "200", description = "Result updated")
    @ApiResponse(responseCode = "404", description = "Lab order or result not found")
    @RequirePermission(resource = Resource.LAB_RESULTS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<LabResultResponse>> patchResult(
            @Parameter(description = "Lab order UUID") @PathVariable String labOrderId,
            @Valid @RequestBody LabResultRequest request) {
        return ResponseEntity.ok(ApiResult.of("Result updated", labResultService.patchResult(labOrderId, request)));
    }

    @DeleteMapping
    @Operation(summary = "Delete a lab order's result")
    @ApiResponse(responseCode = "204", description = "Result deleted")
    @ApiResponse(responseCode = "404", description = "Lab order or result not found")
    @RequirePermission(resource = Resource.LAB_RESULTS, action = PermissionAction.DELETE)
    public ResponseEntity<Void> deleteResult(
            @Parameter(description = "Lab order UUID") @PathVariable String labOrderId) {
        labResultService.deleteResult(labOrderId);
        return ResponseEntity.noContent().build();
    }
}
