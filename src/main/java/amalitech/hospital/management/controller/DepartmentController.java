package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.doctor.DepartmentDoctorCountResponse;
import amalitech.hospital.management.dto.doctor.DepartmentRequest;
import amalitech.hospital.management.dto.doctor.DepartmentResponse;
import amalitech.hospital.management.dto.doctor.PatchDepartmentRequest;
import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.DepartmentService;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.micrometer.core.annotation.Timed;

/**
 * Department management — backed by {@link DepartmentService}. See that class for
 * caching/exception/transaction behavior; this layer only maps HTTP <-> DTOs.
 */
@RestController
@RequestMapping("/api/v1/departments")
@Tag(name = "Departments", description = "Hospital department management")
@Timed(value = "hms.rest.requests", extraTags = {"layer", "rest"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    @Operation(summary = "List departments (paginated, sortable)",
            description = "Standard `?sort=property,direction` query param (e.g. `sort=name,asc`) — "
                    + "backed directly by Spring Data JPA, so any `Department` field is sortable: "
                    + "`departmentId`, `name`, `location`, `phone`, `createdAt`, `updatedAt`. Unlike "
                    + "`/api/v1/users`, an unrecognized property is not validated ahead of time and "
                    + "currently surfaces as a 400 rather than silently falling back.")
    @ApiResponse(responseCode = "200", description = "Departments returned")
    @Parameter(name = "sort", in = ParameterIn.QUERY,
            description = "Sort by property,direction. Possible properties: departmentId, name, "
                    + "location, phone, createdAt, updatedAt.",
            array = @ArraySchema(schema = @Schema(type = "string")), example = "name,asc")
    @RequirePermission(resource = Resource.DEPARTMENTS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PagedModel<DepartmentResponse>>> getDepartments(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Departments retrieved", departmentService.getDepartments(pageable)));
    }

    @GetMapping("/{departmentId}")
    @Operation(summary = "Get a department by id")
    @ApiResponse(responseCode = "200", description = "Department found")
    @ApiResponse(responseCode = "404", description = "Department not found")
    @RequirePermission(resource = Resource.DEPARTMENTS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<DepartmentResponse>> getDepartment(
            @Parameter(description = "Department UUID") @PathVariable String departmentId) {
        return ResponseEntity.ok(ApiResult.of("Department retrieved", departmentService.getDepartment(departmentId)));
    }

    @PostMapping
    @Operation(summary = "Create a department")
    @ApiResponse(responseCode = "201", description = "Department created")
    @ApiResponse(responseCode = "409", description = "Name or phone already registered")
    @RequirePermission(resource = Resource.DEPARTMENTS, action = PermissionAction.CREATE)
    public ResponseEntity<ApiResult<DepartmentResponse>> createDepartment(@Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Department created", departmentService.createDepartment(request)));
    }

    @PutMapping("/{departmentId}")
    @Operation(summary = "Update a department")
    @ApiResponse(responseCode = "200", description = "Department updated")
    @ApiResponse(responseCode = "404", description = "Department not found")
    @ApiResponse(responseCode = "409", description = "Name/phone taken, or department still assigned to doctors")
    @RequirePermission(resource = Resource.DEPARTMENTS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<DepartmentResponse>> updateDepartment(
            @Parameter(description = "Department UUID") @PathVariable String departmentId,
            @Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(ApiResult.of("Department updated", departmentService.updateDepartment(departmentId, request)));
    }

    @PatchMapping("/{departmentId}")
    @Operation(summary = "Partially update a department",
            description = "Unlike PUT, only the fields actually present in the request body are changed — "
                    + "omitted fields are left exactly as they were. The still-assigned-to-doctors guard only "
                    + "fires when name is included.")
    @ApiResponse(responseCode = "200", description = "Department updated")
    @ApiResponse(responseCode = "404", description = "Department not found")
    @ApiResponse(responseCode = "409", description = "Name/phone taken, or department still assigned to doctors")
    @RequirePermission(resource = Resource.DEPARTMENTS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<DepartmentResponse>> patchDepartment(
            @Parameter(description = "Department UUID") @PathVariable String departmentId,
            @Valid @RequestBody PatchDepartmentRequest request) {
        return ResponseEntity.ok(ApiResult.of("Department updated", departmentService.patchDepartment(departmentId, request)));
    }

    @DeleteMapping("/{departmentId}")
    @Operation(summary = "Delete a department")
    @ApiResponse(responseCode = "204", description = "Department deleted")
    @ApiResponse(responseCode = "404", description = "Department not found")
    @ApiResponse(responseCode = "409", description = "Department still assigned to one or more doctors")
    @RequirePermission(resource = Resource.DEPARTMENTS, action = PermissionAction.DELETE)
    public ResponseEntity<Void> deleteDepartment(
            @Parameter(description = "Department UUID") @PathVariable String departmentId) {
        departmentService.deleteDepartment(departmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/staffing-summary")
    @Operation(summary = "Departments with active doctor counts",
            description = "Only departments with at least one active doctor appear — an "
                    + "unstaffed department is a gap to notice on `GET /api/v1/departments` "
                    + "itself, not something buried among staffed ones here. Backed by a "
                    + "`GROUP BY`/`HAVING` aggregate query "
                    + "(`@SqlQueryBuilder(\"findDepartmentsWithDoctors\")`).")
    @ApiResponse(responseCode = "200", description = "Staffing summary returned")
    @RequirePermission(resource = Resource.DEPARTMENTS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<List<DepartmentDoctorCountResponse>>> getStaffingSummary() {
        return ResponseEntity.ok(ApiResult.of("Department staffing summary retrieved", departmentService.getStaffingSummary()));
    }

    @GetMapping("/{departmentId}/doctors")
    @Operation(summary = "List doctors in a department")
    @ApiResponse(responseCode = "200", description = "Doctors returned")
    @ApiResponse(responseCode = "404", description = "Department not found")
    @RequirePermission(resource = Resource.DEPARTMENTS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<List<DoctorResponse>>> getDepartmentDoctors(
            @Parameter(description = "Department UUID") @PathVariable String departmentId) {
        return ResponseEntity.ok(ApiResult.of("Doctors retrieved", departmentService.getDepartmentDoctors(departmentId)));
    }
}
