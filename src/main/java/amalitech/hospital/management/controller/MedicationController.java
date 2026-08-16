package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.pharmacy.MedicationRequest;
import amalitech.hospital.management.dto.pharmacy.MedicationResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.MedicationService;
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
 * Medication catalog management — backed by {@link MedicationService}. See that class
 * for caching/exception/transaction behavior; this layer only maps HTTP <-> DTOs.
 */
@RestController
@RequestMapping("/api/v1/medications")
@Tag(name = "Medications", description = "Medication catalog")
@RequiredArgsConstructor
public class MedicationController {

    private final MedicationService medicationService;

    @GetMapping
    @Operation(summary = "List medications (paginated, sortable)")
    @ApiResponse(responseCode = "200", description = "Medications returned")
    @RequirePermission(resource = Resource.MEDICATIONS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PagedModel<MedicationResponse>>> getMedications(Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Medications retrieved", medicationService.getMedications(pageable)));
    }

    @GetMapping("/{medicationId}")
    @Operation(summary = "Get a medication by id")
    @ApiResponse(responseCode = "200", description = "Medication found")
    @ApiResponse(responseCode = "404", description = "Medication not found")
    @RequirePermission(resource = Resource.MEDICATIONS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<MedicationResponse>> getMedication(
            @Parameter(description = "Medication UUID") @PathVariable String medicationId) {
        return ResponseEntity.ok(ApiResult.of("Medication retrieved", medicationService.getMedication(medicationId)));
    }

    @PostMapping
    @Operation(summary = "Create a medication")
    @ApiResponse(responseCode = "201", description = "Medication created")
    @ApiResponse(responseCode = "409", description = "Medication name already exists")
    @RequirePermission(resource = Resource.MEDICATIONS, action = PermissionAction.CREATE)
    public ResponseEntity<ApiResult<MedicationResponse>> createMedication(@Valid @RequestBody MedicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Medication created", medicationService.createMedication(request)));
    }

    @PutMapping("/{medicationId}")
    @Operation(summary = "Update a medication")
    @ApiResponse(responseCode = "200", description = "Medication updated")
    @ApiResponse(responseCode = "404", description = "Medication not found")
    @ApiResponse(responseCode = "409", description = "Medication name already exists")
    @RequirePermission(resource = Resource.MEDICATIONS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<MedicationResponse>> updateMedication(
            @Parameter(description = "Medication UUID") @PathVariable String medicationId,
            @Valid @RequestBody MedicationRequest request) {
        return ResponseEntity.ok(ApiResult.of("Medication updated", medicationService.updateMedication(medicationId, request)));
    }

    @DeleteMapping("/{medicationId}")
    @Operation(summary = "Delete a medication")
    @ApiResponse(responseCode = "204", description = "Medication deleted")
    @ApiResponse(responseCode = "404", description = "Medication not found")
    @RequirePermission(resource = Resource.MEDICATIONS, action = PermissionAction.DELETE)
    public ResponseEntity<Void> deleteMedication(
            @Parameter(description = "Medication UUID") @PathVariable String medicationId) {
        medicationService.deleteMedication(medicationId);
        return ResponseEntity.noContent().build();
    }
}
