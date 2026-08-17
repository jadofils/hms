package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.patient.AppointmentRequest;
import amalitech.hospital.management.dto.patient.AppointmentResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.AppointmentService;
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
 * Appointment management — backed by {@link AppointmentService}. See that class for
 * caching/exception/transaction behavior; this layer only maps HTTP <-> DTOs.
 */
@RestController
@RequestMapping("/api/v1/appointments")
@Tag(name = "Appointments", description = "Patient/doctor appointment scheduling")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @GetMapping
    @Operation(summary = "List appointments (paginated, sortable, filterable)",
            description = "Standard `?sort=property,direction` query param (e.g. "
                    + "`sort=appointmentDate,desc`); only the first sort property is "
                    + "honored. Sortable columns: `appointmentId`, `patientId`, `doctorId`, "
                    + "`patientFirstName`, `patientLastName`, `doctorFirstName`, "
                    + "`doctorLastName`, `appointmentDate`, `status`, `reason`. An omitted "
                    + "or unrecognized column never errors — it falls back to "
                    + "`appointmentId` ascending. Optional `status` query param filters "
                    + "the list.")
    @ApiResponse(responseCode = "200", description = "Appointments returned")
    @Parameter(name = "sort", in = ParameterIn.QUERY,
            description = "Sort by property,direction. Possible properties: appointmentId, patientId, "
                    + "doctorId, patientFirstName, patientLastName, doctorFirstName, doctorLastName, "
                    + "appointmentDate, status, reason.",
            array = @ArraySchema(schema = @Schema(type = "string")), example = "appointmentDate,desc")
    @RequirePermission(resource = Resource.APPOINTMENTS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PagedModel<AppointmentResponse>>> getAppointments(
            Pageable pageable,
            @Parameter(description = "Filter by status: scheduled, completed, cancelled")
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResult.of("Appointments retrieved", appointmentService.getAppointments(pageable, status)));
    }

    @GetMapping("/{appointmentId}")
    @Operation(summary = "Get an appointment by id")
    @ApiResponse(responseCode = "200", description = "Appointment found")
    @ApiResponse(responseCode = "404", description = "Appointment not found")
    @RequirePermission(resource = Resource.APPOINTMENTS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<AppointmentResponse>> getAppointment(
            @Parameter(description = "Appointment UUID") @PathVariable String appointmentId) {
        return ResponseEntity.ok(ApiResult.of("Appointment retrieved", appointmentService.getAppointment(appointmentId)));
    }

    @PostMapping
    @Operation(summary = "Create an appointment")
    @ApiResponse(responseCode = "201", description = "Appointment created")
    @ApiResponse(responseCode = "404", description = "Patient or doctor not found")
    @RequirePermission(resource = Resource.APPOINTMENTS, action = PermissionAction.CREATE)
    public ResponseEntity<ApiResult<AppointmentResponse>> createAppointment(@Valid @RequestBody AppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Appointment created", appointmentService.createAppointment(request)));
    }

    @PutMapping("/{appointmentId}")
    @Operation(summary = "Update an appointment")
    @ApiResponse(responseCode = "200", description = "Appointment updated")
    @ApiResponse(responseCode = "404", description = "Appointment, patient, or doctor not found")
    @RequirePermission(resource = Resource.APPOINTMENTS, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<AppointmentResponse>> updateAppointment(
            @Parameter(description = "Appointment UUID") @PathVariable String appointmentId,
            @Valid @RequestBody AppointmentRequest request) {
        return ResponseEntity.ok(ApiResult.of("Appointment updated",
                appointmentService.updateAppointment(appointmentId, request)));
    }

    @DeleteMapping("/{appointmentId}")
    @Operation(summary = "Delete an appointment")
    @ApiResponse(responseCode = "204", description = "Appointment deleted")
    @ApiResponse(responseCode = "404", description = "Appointment not found")
    @RequirePermission(resource = Resource.APPOINTMENTS, action = PermissionAction.DELETE)
    public ResponseEntity<Void> deleteAppointment(
            @Parameter(description = "Appointment UUID") @PathVariable String appointmentId) {
        appointmentService.deleteAppointment(appointmentId);
        return ResponseEntity.noContent().build();
    }
}
