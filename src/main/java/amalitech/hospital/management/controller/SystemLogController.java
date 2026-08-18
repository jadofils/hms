package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.log.SystemLogResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.SystemLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.micrometer.core.annotation.Timed;

/**
 * Read-only access to {@code system_logs} — backed by {@link SystemLogService}. See that
 * class's Javadoc for why there's no create/update/delete here: {@code LoggingAspect}/
 * {@code SystemLogWriter} are the only writer, and a log row is never edited once
 * written. This layer only maps HTTP <-> DTOs, same as every other controller.
 */
@RestController
@RequestMapping("/api/v1/system-logs")
@Tag(name = "System Logs", description = "Read-only operational log trail written by LoggingAspect/SystemLogWriter")
@Timed(value = "hms.rest.requests", extraTags = {"layer", "rest"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class SystemLogController {

    private final SystemLogService systemLogService;

    @GetMapping
    @Operation(summary = "List system logs (paginated, sortable, filterable)",
            description = "Standard `?sort=property,direction` query param (e.g. `sort=createdAt,desc`) "
                    + "— backed directly by Spring Data JPA, so any `SystemLog` field is sortable: "
                    + "`logId`, `logLevel`, `source`, `createdAt`. Optional `logLevel` (exact match, one "
                    + "of DEBUG/INFO/WARNING/ERROR) and `source` (case-insensitive contains, e.g. "
                    + "`source=RoleService` for every failure logged from that class) filters, "
                    + "independently combinable.")
    @ApiResponse(responseCode = "200", description = "System logs returned")
    @ApiResponse(responseCode = "400", description = "Unrecognized logLevel")
    @Parameter(name = "sort", in = ParameterIn.QUERY,
            description = "Sort by property,direction. Possible properties: logId, logLevel, source, createdAt.",
            array = @ArraySchema(schema = @Schema(type = "string")), example = "createdAt,desc")
    @RequirePermission(resource = Resource.SYSTEM_LOGS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PagedModel<SystemLogResponse>>> getSystemLogs(
            Pageable pageable,
            @Parameter(description = "Filter by exact log level: DEBUG, INFO, WARNING, ERROR")
            @RequestParam(required = false) String logLevel,
            @Parameter(description = "Filter by source, case-insensitive contains match (e.g. RoleService)")
            @RequestParam(required = false) String source) {
        return ResponseEntity.ok(
                ApiResult.of("System logs retrieved", systemLogService.getSystemLogs(pageable, logLevel, source)));
    }

    @GetMapping("/{logId}")
    @Operation(summary = "Get a system log by id")
    @ApiResponse(responseCode = "200", description = "System log found")
    @ApiResponse(responseCode = "404", description = "System log not found")
    @RequirePermission(resource = Resource.SYSTEM_LOGS, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<SystemLogResponse>> getSystemLog(
            @Parameter(description = "System log UUID") @PathVariable String logId) {
        return ResponseEntity.ok(ApiResult.of("System log retrieved", systemLogService.getSystemLog(logId)));
    }
}
