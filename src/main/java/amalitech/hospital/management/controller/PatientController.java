package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.patient.PatientRequest;
import amalitech.hospital.management.dto.patient.PatientResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.PatientService;
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

/**
 * Patient management — backed by {@link PatientService}. See that class for caching/
 * exception/transaction behavior; this layer only maps HTTP <-> DTOs.
 */
@RestController
@RequestMapping("/api/v1/patients")
@Tag(name = "Patients", description = "Patient records management")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @GetMapping
    @Operation(summary = "List patients (paginated, sortable, filterable)",
            description = "Standard `?sort=property,direction` query param (e.g. "
                    + "`sort=lastName,desc`); only the first sort property is honored. "
                    + "Sortable columns: `patientId`, `firstName`, `lastName`, `dob`, "
                    + "`gender`, `phone`, `email`, `address`, `status`. An omitted or "
                    + "unrecognized column never errors — it falls back to `patientId` "
                    + "ascending. Optional `status`/`gender` query params filter the list.")
    @ApiResponse(responseCode = "200", description = "Patients returned")
    @Parameter(name = "sort", in = ParameterIn.QUERY,
            description = "Sort by property,direction. Possible properties: patientId, firstName, "
                    + "lastName, dob, gender, phone, email, address, status.",
            array = @ArraySchema(schema = @Schema(type = "string")), example = "lastName,desc")
    @RequirePermission(resource = Resource.PATIENTS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PagedModel<PatientResponse>>> getPatients(
            Pageable pageable,
            @Parameter(description = "Filter by status: active, inactive")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter by gender: M, F, Other")
            @RequestParam(required = false) String gender) {
        return ResponseEntity.ok(ApiResult.of("Patients retrieved", patientService.getPatients(pageable, status, gender)));
    }

    @GetMapping("/{patientId}")
    @Operation(summary = "Get a patient by id")
    @ApiResponse(responseCode = "200", description = "Patient found")
    @ApiResponse(responseCode = "404", description = "Patient not found")
    @RequirePermission(resource = Resource.PATIENTS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PatientResponse>> getPatient(
            @Parameter(description = "Patient UUID") @PathVariable String patientId) {
        return ResponseEntity.ok(ApiResult.of("Patient retrieved", patientService.getPatient(patientId)));
    }

    @PostMapping
    @Operation(summary = "Create a patient")
    @ApiResponse(responseCode = "201", description = "Patient created")
    @ApiResponse(responseCode = "409", description = "Phone or email already registered")
    @RequirePermission(resource = Resource.PATIENTS, action = PermissionAction.CREATE)
    public ResponseEntity<ApiResult<PatientResponse>> createPatient(@Valid @RequestBody PatientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Patient created", patientService.createPatient(request)));
    }

    @PutMapping("/{patientId}")
    @Operation(summary = "Update a patient")
    @ApiResponse(responseCode = "200", description = "Patient updated")
    @ApiResponse(responseCode = "404", description = "Patient not found")
    @ApiResponse(responseCode = "409", description = "Phone or email already registered")
    @RequirePermission(resource = Resource.PATIENTS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<PatientResponse>> updatePatient(
            @Parameter(description = "Patient UUID") @PathVariable String patientId,
            @Valid @RequestBody PatientRequest request) {
        return ResponseEntity.ok(ApiResult.of("Patient updated", patientService.updatePatient(patientId, request)));
    }

    @DeleteMapping("/{patientId}")
    @Operation(summary = "Delete a patient")
    @ApiResponse(responseCode = "204", description = "Patient deleted")
    @ApiResponse(responseCode = "404", description = "Patient not found")
    @RequirePermission(resource = Resource.PATIENTS, action = PermissionAction.DELETE)
    public ResponseEntity<Void> deletePatient(
            @Parameter(description = "Patient UUID") @PathVariable String patientId) {
        patientService.deletePatient(patientId);
        return ResponseEntity.noContent().build();
    }
}
