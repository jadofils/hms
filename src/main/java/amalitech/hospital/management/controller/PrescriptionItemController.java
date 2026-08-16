package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.pharmacy.PrescriptionItemRequest;
import amalitech.hospital.management.dto.pharmacy.PrescriptionItemResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.PrescriptionItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Prescription line items — backed by {@link PrescriptionItemService}. See that class
 * for exception/transaction behavior; this layer only maps HTTP <-> DTOs.
 */
@RestController
@RequestMapping("/api/v1/prescriptions/{prescriptionId}/items")
@Tag(name = "Prescription Items", description = "Medication line items within a prescription")
@RequiredArgsConstructor
public class PrescriptionItemController {

    private final PrescriptionItemService prescriptionItemService;

    @GetMapping
    @Operation(summary = "List a prescription's line items")
    @ApiResponse(responseCode = "200", description = "Items returned")
    @ApiResponse(responseCode = "404", description = "Prescription not found")
    @RequirePermission(resource = Resource.PRESCRIPTION_ITEMS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<List<PrescriptionItemResponse>>> getItems(
            @Parameter(description = "Prescription UUID") @PathVariable String prescriptionId) {
        return ResponseEntity.ok(ApiResult.of("Items retrieved", prescriptionItemService.getItems(prescriptionId)));
    }

    @PostMapping
    @Operation(summary = "Add a line item to a prescription")
    @ApiResponse(responseCode = "201", description = "Item created")
    @ApiResponse(responseCode = "404", description = "Prescription or medication not found")
    @RequirePermission(resource = Resource.PRESCRIPTION_ITEMS, action = PermissionAction.CREATE)
    public ResponseEntity<ApiResult<PrescriptionItemResponse>> createItem(
            @Parameter(description = "Prescription UUID") @PathVariable String prescriptionId,
            @Valid @RequestBody PrescriptionItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Item created", prescriptionItemService.createItem(prescriptionId, request)));
    }

    @PutMapping("/{itemId}")
    @Operation(summary = "Update a prescription line item")
    @ApiResponse(responseCode = "200", description = "Item updated")
    @ApiResponse(responseCode = "404", description = "Prescription, item, or medication not found")
    @RequirePermission(resource = Resource.PRESCRIPTION_ITEMS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<PrescriptionItemResponse>> updateItem(
            @Parameter(description = "Prescription UUID") @PathVariable String prescriptionId,
            @Parameter(description = "Item UUID") @PathVariable String itemId,
            @Valid @RequestBody PrescriptionItemRequest request) {
        return ResponseEntity.ok(ApiResult.of("Item updated",
                prescriptionItemService.updateItem(prescriptionId, itemId, request)));
    }

    @DeleteMapping("/{itemId}")
    @Operation(summary = "Remove a prescription line item")
    @ApiResponse(responseCode = "204", description = "Item deleted")
    @ApiResponse(responseCode = "404", description = "Prescription or item not found")
    @RequirePermission(resource = Resource.PRESCRIPTION_ITEMS, action = PermissionAction.DELETE)
    public ResponseEntity<Void> deleteItem(
            @Parameter(description = "Prescription UUID") @PathVariable String prescriptionId,
            @Parameter(description = "Item UUID") @PathVariable String itemId) {
        prescriptionItemService.deleteItem(prescriptionId, itemId);
        return ResponseEntity.noContent().build();
    }
}
