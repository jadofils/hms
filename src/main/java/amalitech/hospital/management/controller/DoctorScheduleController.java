package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.doctor.DoctorScheduleRequest;
import amalitech.hospital.management.dto.doctor.DoctorScheduleResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.DoctorScheduleService;
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
import java.util.Map;
import io.micrometer.core.annotation.Timed;

/**
 * Doctor weekly recurring availability ("the scheduler") — backed by
 * {@link DoctorScheduleService}. See that class for validation/transaction behavior;
 * this layer only maps HTTP <-> DTOs.
 */
@RestController
@RequestMapping("/api/v1/doctors/{doctorId}/schedules")
@Tag(name = "Doctor Schedules", description = "Doctor weekly recurring availability")
@Timed(value = "hms.rest.requests", extraTags = {"layer", "rest"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class DoctorScheduleController {

    private final DoctorScheduleService doctorScheduleService;

    @GetMapping
    @Operation(summary = "List a doctor's schedule blocks")
    @ApiResponse(responseCode = "200", description = "Schedule blocks returned")
    @ApiResponse(responseCode = "404", description = "Doctor not found")
    @RequirePermission(resource = Resource.DOCTOR_SCHEDULES, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<List<DoctorScheduleResponse>>> getSchedules(
            @Parameter(description = "Doctor UUID") @PathVariable String doctorId) {
        return ResponseEntity.ok(ApiResult.of("Schedule blocks retrieved", doctorScheduleService.getSchedules(doctorId)));
    }

    @PostMapping
    @Operation(summary = "Add a recurring availability block")
    @ApiResponse(responseCode = "201", description = "Schedule block created")
    @ApiResponse(responseCode = "400", description = "End time is not after start time")
    @ApiResponse(responseCode = "404", description = "Doctor not found")
    @RequirePermission(resource = Resource.DOCTOR_SCHEDULES, action = PermissionAction.CREATE)
    public ResponseEntity<ApiResult<DoctorScheduleResponse>> createSchedule(
            @Parameter(description = "Doctor UUID") @PathVariable String doctorId,
            @Valid @RequestBody DoctorScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Schedule block created", doctorScheduleService.createSchedule(doctorId, request)));
    }

    @PutMapping("/{scheduleId}")
    @Operation(summary = "Update a recurring availability block")
    @ApiResponse(responseCode = "200", description = "Schedule block updated")
    @ApiResponse(responseCode = "400", description = "End time is not after start time")
    @ApiResponse(responseCode = "404", description = "Doctor or schedule block not found")
    @RequirePermission(resource = Resource.DOCTOR_SCHEDULES, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<DoctorScheduleResponse>> updateSchedule(
            @Parameter(description = "Doctor UUID") @PathVariable String doctorId,
            @Parameter(description = "Schedule block UUID") @PathVariable String scheduleId,
            @Valid @RequestBody DoctorScheduleRequest request) {
        return ResponseEntity.ok(ApiResult.of("Schedule block updated",
                doctorScheduleService.updateSchedule(doctorId, scheduleId, request)));
    }

    @DeleteMapping("/{scheduleId}")
    @Operation(summary = "Delete a recurring availability block")
    @ApiResponse(responseCode = "204", description = "Schedule block deleted")
    @ApiResponse(responseCode = "404", description = "Doctor or schedule block not found")
    @RequirePermission(resource = Resource.DOCTOR_SCHEDULES, action = PermissionAction.DELETE)
    public ResponseEntity<Void> deleteSchedule(
            @Parameter(description = "Doctor UUID") @PathVariable String doctorId,
            @Parameter(description = "Schedule block UUID") @PathVariable String scheduleId) {
        doctorScheduleService.deleteSchedule(doctorId, scheduleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/availability")
    @Operation(summary = "Check whether a doctor is available at a given day/time",
            description = "`day` is one of Mon/Tue/Wed/Thu/Fri/Sat/Sun; `time` is HH:mm.")
    @ApiResponse(responseCode = "200", description = "Availability returned")
    @ApiResponse(responseCode = "400", description = "Invalid day or time format")
    @ApiResponse(responseCode = "404", description = "Doctor not found")
    @RequirePermission(resource = Resource.DOCTOR_SCHEDULES, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<Map<String, Boolean>>> checkAvailability(
            @Parameter(description = "Doctor UUID") @PathVariable String doctorId,
            @Parameter(description = "Day of week, e.g. Mon") @RequestParam String day,
            @Parameter(description = "Time of day, HH:mm") @RequestParam String time) {
        boolean available = doctorScheduleService.isDoctorAvailable(doctorId, day, time);
        return ResponseEntity.ok(ApiResult.of("Availability checked", Map.of("available", available)));
    }
}
