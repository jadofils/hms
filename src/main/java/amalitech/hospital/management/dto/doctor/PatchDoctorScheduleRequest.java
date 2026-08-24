package amalitech.hospital.management.dto.doctor;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalTime;

/**
 * Partial-update counterpart to {@link DoctorScheduleRequest} — every field optional;
 * only the ones actually present in the request body get changed. See
 * {@code DoctorScheduleService.patchSchedule}.
 */
@Data
public class PatchDoctorScheduleRequest {

    @Schema(description = "Optional. Day of the week this schedule applies to, one of: Mon, Tue, Wed, Thu, Fri, Sat, Sun (case-insensitive).",
            example = "Mon", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "(?i)^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)$",
            message = "Day must be one of: Mon, Tue, Wed, Thu, Fri, Sat, Sun")
    private String dayOfWeek;

    @Schema(description = "Optional. Start time of the doctor's availability window on this day.",
            example = "09:00:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private LocalTime startTime;

    @Schema(description = "Optional. End time of the doctor's availability window on this day.",
            example = "17:00:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private LocalTime endTime;

    @Schema(description = "Optional. Whether the doctor is available during this window.",
            example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean isAvailable;
}
