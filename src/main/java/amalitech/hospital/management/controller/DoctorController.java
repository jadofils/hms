package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.doctor.DoctorRequest;
import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.DoctorService;
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
 * Doctor management — backed by {@link DoctorService}. See that class for caching/
 * exception/transaction behavior; this layer only maps HTTP <-> DTOs.
 */
@RestController
@RequestMapping("/api/v1/doctors")
@Tag(name = "Doctors", description = "Doctor records and department membership")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    @GetMapping
    @Operation(summary = "List doctors (paginated, sortable)",
            description = "Standard `?sort=property,direction` query param (e.g. "
                    + "`sort=lastName,desc`); only the first sort property is honored. "
                    + "Sortable columns: `doctorId`, `firstName`, `lastName`, "
                    + "`specialization`, `phone`, `email`. An omitted or unrecognized "
                    + "column never errors — it falls back to `doctorId` ascending.")
    @ApiResponse(responseCode = "200", description = "Doctors returned")
    @RequirePermission(resource = Resource.DOCTORS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PagedModel<DoctorResponse>>> getDoctors(Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Doctors retrieved", doctorService.getDoctors(pageable)));
    }

    @GetMapping("/{doctorId}")
    @Operation(summary = "Get a doctor by id, including department memberships")
    @ApiResponse(responseCode = "200", description = "Doctor found")
    @ApiResponse(responseCode = "404", description = "Doctor not found")
    @RequirePermission(resource = Resource.DOCTORS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<DoctorResponse>> getDoctor(
            @Parameter(description = "Doctor UUID") @PathVariable String doctorId) {
        return ResponseEntity.ok(ApiResult.of("Doctor retrieved", doctorService.getDoctor(doctorId)));
    }

    @PostMapping
    @Operation(summary = "Create a doctor")
    @ApiResponse(responseCode = "201", description = "Doctor created")
    @ApiResponse(responseCode = "409", description = "Phone or email already registered")
    @RequirePermission(resource = Resource.DOCTORS, action = PermissionAction.CREATE)
    public ResponseEntity<ApiResult<DoctorResponse>> createDoctor(@Valid @RequestBody DoctorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Doctor created", doctorService.createDoctor(request)));
    }

    @PutMapping("/{doctorId}")
    @Operation(summary = "Update a doctor")
    @ApiResponse(responseCode = "200", description = "Doctor updated")
    @ApiResponse(responseCode = "404", description = "Doctor not found")
    @ApiResponse(responseCode = "409", description = "Phone or email already registered")
    @RequirePermission(resource = Resource.DOCTORS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<DoctorResponse>> updateDoctor(
            @Parameter(description = "Doctor UUID") @PathVariable String doctorId,
            @Valid @RequestBody DoctorRequest request) {
        return ResponseEntity.ok(ApiResult.of("Doctor updated", doctorService.updateDoctor(doctorId, request)));
    }

    @DeleteMapping("/{doctorId}")
    @Operation(summary = "Delete a doctor")
    @ApiResponse(responseCode = "204", description = "Doctor deleted")
    @ApiResponse(responseCode = "404", description = "Doctor not found")
    @RequirePermission(resource = Resource.DOCTORS, action = PermissionAction.DELETE)
    public ResponseEntity<Void> deleteDoctor(
            @Parameter(description = "Doctor UUID") @PathVariable String doctorId) {
        doctorService.deleteDoctor(doctorId);
        return ResponseEntity.noContent().build();
    }

    // ── Department membership ────────────────────────────────────────────────

    @PostMapping("/{doctorId}/departments/{departmentId}")
    @Operation(summary = "Assign a doctor to a department")
    @ApiResponse(responseCode = "204", description = "Department assigned")
    @ApiResponse(responseCode = "404", description = "Doctor or department not found")
    @ApiResponse(responseCode = "409", description = "Doctor already assigned to this department")
    @RequirePermission(resource = Resource.DOCTORS, action = PermissionAction.UPDATE)
    public ResponseEntity<Void> assignDepartment(
            @Parameter(description = "Doctor UUID") @PathVariable String doctorId,
            @Parameter(description = "Department UUID") @PathVariable String departmentId) {
        doctorService.assignDepartment(doctorId, departmentId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{doctorId}/departments/{departmentId}")
    @Operation(summary = "Remove a doctor from a department")
    @ApiResponse(responseCode = "204", description = "Department removed")
    @ApiResponse(responseCode = "404", description = "Doctor is not assigned to this department")
    @RequirePermission(resource = Resource.DOCTORS, action = PermissionAction.UPDATE)
    public ResponseEntity<Void> removeDepartment(
            @Parameter(description = "Doctor UUID") @PathVariable String doctorId,
            @Parameter(description = "Department UUID") @PathVariable String departmentId) {
        doctorService.removeDepartment(doctorId, departmentId);
        return ResponseEntity.noContent().build();
    }
}
