package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.pharmacy.MedicalInventoryRequest;
import amalitech.hospital.management.dto.pharmacy.MedicalInventoryResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.MedicalInventoryService;
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
 * Medication stock management — backed by {@link MedicalInventoryService}. See that
 * class for caching/exception/transaction behavior; this layer only maps HTTP <-> DTOs.
 */
@RestController
@RequestMapping("/api/v1/medical-inventory")
@Tag(name = "Medical Inventory", description = "Medication stock records")
@Timed(value = "hms.rest.requests", extraTags = {"layer", "rest"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class MedicalInventoryController {

    private final MedicalInventoryService medicalInventoryService;

    @GetMapping
    @Operation(summary = "List inventory records (paginated, sortable, filterable)",
            description = "Standard `?sort=property,direction` query param (e.g. `sort=expiryDate,asc`) "
                    + "— backed directly by Spring Data JPA, so any `MedicalInventory` field is "
                    + "sortable: `inventoryId`, `batchNumber`, `expiryDate`, `quantityInStock`, "
                    + "`reorderLevel`, `supplier`, `createdAt`, `updatedAt`. Unlike `/api/v1/users`, an "
                    + "unrecognized property is not validated ahead of time and currently surfaces as a "
                    + "400 rather than silently falling back. `lowStock=true` and `medicationId` are "
                    + "independent, mutually exclusive filters — `lowStock` wins if both are given.")
    @ApiResponse(responseCode = "200", description = "Inventory records returned")
    @Parameter(name = "sort", in = ParameterIn.QUERY,
            description = "Sort by property,direction. Possible properties: inventoryId, batchNumber, "
                    + "expiryDate, quantityInStock, reorderLevel, supplier, createdAt, updatedAt.",
            array = @ArraySchema(schema = @Schema(type = "string")), example = "expiryDate,asc")
    @RequirePermission(resource = Resource.MEDICAL_INVENTORY, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PagedModel<MedicalInventoryResponse>>> getInventoryRecords(
            Pageable pageable,
            @Parameter(description = "Filter to only batches at or below their own reorder level — a "
                    + "restock-alert worklist") @RequestParam(required = false) Boolean lowStock,
            @Parameter(description = "Filter to every batch on hand for one medication")
            @RequestParam(required = false) String medicationId) {
        return ResponseEntity.ok(ApiResult.of("Inventory records retrieved",
                medicalInventoryService.getInventoryRecords(pageable, medicationId, lowStock)));
    }

    @GetMapping("/{inventoryId}")
    @Operation(summary = "Get an inventory record by id")
    @ApiResponse(responseCode = "200", description = "Inventory record found")
    @ApiResponse(responseCode = "404", description = "Inventory record not found")
    @RequirePermission(resource = Resource.MEDICAL_INVENTORY, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<MedicalInventoryResponse>> getInventoryRecord(
            @Parameter(description = "Inventory record UUID") @PathVariable String inventoryId) {
        return ResponseEntity.ok(ApiResult.of("Inventory record retrieved",
                medicalInventoryService.getInventoryRecord(inventoryId)));
    }

    @PostMapping
    @Operation(summary = "Create an inventory record")
    @ApiResponse(responseCode = "201", description = "Inventory record created")
    @ApiResponse(responseCode = "404", description = "Medication not found")
    @RequirePermission(resource = Resource.MEDICAL_INVENTORY, action = PermissionAction.CREATE)
    public ResponseEntity<ApiResult<MedicalInventoryResponse>> createInventoryRecord(
            @Valid @RequestBody MedicalInventoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Inventory record created", medicalInventoryService.createInventoryRecord(request)));
    }

    @PutMapping("/{inventoryId}")
    @Operation(summary = "Update an inventory record")
    @ApiResponse(responseCode = "200", description = "Inventory record updated")
    @ApiResponse(responseCode = "404", description = "Inventory record or medication not found")
    @RequirePermission(resource = Resource.MEDICAL_INVENTORY, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<MedicalInventoryResponse>> updateInventoryRecord(
            @Parameter(description = "Inventory record UUID") @PathVariable String inventoryId,
            @Valid @RequestBody MedicalInventoryRequest request) {
        return ResponseEntity.ok(ApiResult.of("Inventory record updated",
                medicalInventoryService.updateInventoryRecord(inventoryId, request)));
    }

    @DeleteMapping("/{inventoryId}")
    @Operation(summary = "Delete an inventory record")
    @ApiResponse(responseCode = "204", description = "Inventory record deleted")
    @ApiResponse(responseCode = "404", description = "Inventory record not found")
    @RequirePermission(resource = Resource.MEDICAL_INVENTORY, action = PermissionAction.DELETE)
    public ResponseEntity<Void> deleteInventoryRecord(
            @Parameter(description = "Inventory record UUID") @PathVariable String inventoryId) {
        medicalInventoryService.deleteInventoryRecord(inventoryId);
        return ResponseEntity.noContent().build();
    }
}
