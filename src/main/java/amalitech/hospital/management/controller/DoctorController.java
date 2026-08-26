package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.doctor.AssignDepartmentsRequest;
import amalitech.hospital.management.dto.doctor.DoctorDepartmentRosterResponse;
import amalitech.hospital.management.dto.doctor.DoctorRequest;
import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.dto.doctor.PatchDoctorRequest;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.DoctorService;
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

import java.util.List;
import io.micrometer.core.annotation.Timed;

/**
 * Doctor management — backed by {@link DoctorService}. See that class for caching/
 * exception/transaction behavior; this layer only maps HTTP <-> DTOs.
 */
@RestController
@RequestMapping("/api/v1/doctors")
@Tag(name = "Doctors", description = "Doctor records and department membership")
@Timed(value = "hms.rest.requests", extraTags = {"layer", "rest"}, percentiles = {0.5, 0.95, 0.99})
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
    @Parameter(name = "sort", in = ParameterIn.QUERY,
            description = "Sort by property,direction. Possible properties: doctorId, firstName, "
                    + "lastName, specialization, phone, email.",
            array = @ArraySchema(schema = @Schema(type = "string")), example = "lastName,desc")
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
    @Operation(summary = "Create a doctor",
            description = "`departmentIds` must include at least one existing department id — "
                    + "a doctor must belong somewhere from the moment they're created.")
    @ApiResponse(responseCode = "201", description = "Doctor created")
    @ApiResponse(responseCode = "400", description = "No departmentIds provided")
    @ApiResponse(responseCode = "404", description = "A departmentId does not exist")
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

    @PatchMapping("/{doctorId}")
    @Operation(summary = "Partially update a doctor",
            description = "Unlike PUT, only the fields actually present in the request body are changed — "
                    + "omitted fields are left exactly as they were. Same as PUT, departmentIds is ignored "
                    + "entirely — department membership is managed by the assign/remove department endpoints.")
    @ApiResponse(responseCode = "200", description = "Doctor updated")
    @ApiResponse(responseCode = "404", description = "Doctor not found")
    @ApiResponse(responseCode = "409", description = "Phone or email already registered")
    @RequirePermission(resource = Resource.DOCTORS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<DoctorResponse>> patchDoctor(
            @Parameter(description = "Doctor UUID") @PathVariable String doctorId,
            @Valid @RequestBody PatchDoctorRequest request) {
        return ResponseEntity.ok(ApiResult.of("Doctor updated", doctorService.patchDoctor(doctorId, request)));
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

    @GetMapping("/roster")
    @Operation(summary = "Doctor roster with department names",
            description = "One row per doctor-department pairing — a doctor in more than one "
                    + "department appears once per department (unlike `GET /api/v1/doctors/{doctorId}`, "
                    + "which nests every department under one doctor). Backed by a joined native "
                    + "query (`@SqlQueryBuilder(\"findDoctorsByDepartment\")`).")
    @ApiResponse(responseCode = "200", description = "Roster returned")
    @RequirePermission(resource = Resource.DOCTORS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<List<DoctorDepartmentRosterResponse>>> getDoctorDepartmentRoster() {
        return ResponseEntity.ok(ApiResult.of("Doctor department roster retrieved", doctorService.getDoctorDepartmentRoster()));
    }

    // ── Department membership ────────────────────────────────────────────────

    @PostMapping("/{doctorId}/departments")
    @Operation(summary = "Assign a doctor to one or more departments at once",
            description = "A doctor can belong to many departments simultaneously (and a department can "
                    + "hold many doctors) — pass a single id or several; every id in the list is granted "
                    + "in one call instead of one request per department. All-or-nothing: if any single "
                    + "id doesn't exist or the doctor is already assigned to it, the whole call fails and "
                    + "nothing in the list is assigned.")
    @ApiResponse(responseCode = "204", description = "Departments assigned")
    @ApiResponse(responseCode = "404", description = "Doctor or one of the departments not found")
    @ApiResponse(responseCode = "409", description = "Doctor already assigned to one of the given departments")
    @RequirePermission(resource = Resource.DOCTORS, action = PermissionAction.UPDATE)
    public ResponseEntity<Void> assignDepartments(
            @Parameter(description = "Doctor UUID") @PathVariable String doctorId,
            @Valid @RequestBody AssignDepartmentsRequest request) {
        doctorService.assignDepartments(doctorId, request.getDepartmentIds());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{doctorId}/departments/{departmentId}")
    @Operation(summary = "Remove a doctor from a department",
            description = "Refused if this is the doctor's last remaining department — "
                    + "assign a replacement department first.")
    @ApiResponse(responseCode = "204", description = "Department removed")
    @ApiResponse(responseCode = "404", description = "Doctor is not assigned to this department")
    @ApiResponse(responseCode = "409", description = "This is the doctor's last remaining department")
    @RequirePermission(resource = Resource.DOCTORS, action = PermissionAction.UPDATE)
    public ResponseEntity<Void> removeDepartment(
            @Parameter(description = "Doctor UUID") @PathVariable String doctorId,
            @Parameter(description = "Department UUID") @PathVariable String departmentId) {
        doctorService.removeDepartment(doctorId, departmentId);
        return ResponseEntity.noContent().build();
    }
}
