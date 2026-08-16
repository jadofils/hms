package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.pharmacy.PrescriptionRequest;
import amalitech.hospital.management.dto.pharmacy.PrescriptionResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.PrescriptionService;
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
 * Prescription management — backed by {@link PrescriptionService}. See that class for
 * caching/exception/transaction behavior; this layer only maps HTTP <-> DTOs. Line items
 * are managed separately — see {@code PrescriptionItemController}.
 */
@RestController
@RequestMapping("/api/v1/prescriptions")
@Tag(name = "Prescriptions", description = "Prescriptions issued per appointment")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    @GetMapping
    @Operation(summary = "List prescriptions (paginated, sortable)")
    @ApiResponse(responseCode = "200", description = "Prescriptions returned")
    @RequirePermission(resource = Resource.PRESCRIPTIONS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PagedModel<PrescriptionResponse>>> getPrescriptions(Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Prescriptions retrieved", prescriptionService.getPrescriptions(pageable)));
    }

    @GetMapping("/{prescriptionId}")
    @Operation(summary = "Get a prescription by id")
    @ApiResponse(responseCode = "200", description = "Prescription found")
    @ApiResponse(responseCode = "404", description = "Prescription not found")
    @RequirePermission(resource = Resource.PRESCRIPTIONS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PrescriptionResponse>> getPrescription(
            @Parameter(description = "Prescription UUID") @PathVariable String prescriptionId) {
        return ResponseEntity.ok(ApiResult.of("Prescription retrieved", prescriptionService.getPrescription(prescriptionId)));
    }

    @PostMapping
    @Operation(summary = "Create a prescription")
    @ApiResponse(responseCode = "201", description = "Prescription created")
    @ApiResponse(responseCode = "404", description = "Appointment not found")
    @RequirePermission(resource = Resource.PRESCRIPTIONS, action = PermissionAction.CREATE)
    public ResponseEntity<ApiResult<PrescriptionResponse>> createPrescription(
            @Valid @RequestBody PrescriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Prescription created", prescriptionService.createPrescription(request)));
    }

    @PutMapping("/{prescriptionId}")
    @Operation(summary = "Update a prescription")
    @ApiResponse(responseCode = "200", description = "Prescription updated")
    @ApiResponse(responseCode = "404", description = "Prescription or appointment not found")
    @RequirePermission(resource = Resource.PRESCRIPTIONS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<PrescriptionResponse>> updatePrescription(
            @Parameter(description = "Prescription UUID") @PathVariable String prescriptionId,
            @Valid @RequestBody PrescriptionRequest request) {
        return ResponseEntity.ok(ApiResult.of("Prescription updated",
                prescriptionService.updatePrescription(prescriptionId, request)));
    }

    @DeleteMapping("/{prescriptionId}")
    @Operation(summary = "Delete a prescription")
    @ApiResponse(responseCode = "204", description = "Prescription deleted")
    @ApiResponse(responseCode = "404", description = "Prescription not found")
    @RequirePermission(resource = Resource.PRESCRIPTIONS, action = PermissionAction.DELETE)
    public ResponseEntity<Void> deletePrescription(
            @Parameter(description = "Prescription UUID") @PathVariable String prescriptionId) {
        prescriptionService.deletePrescription(prescriptionId);
        return ResponseEntity.noContent().build();
    }
}
