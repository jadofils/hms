package amalitech.hospital.management.dto.doctor;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalTime;

@Data
public class DoctorScheduleRequest {

    @Schema(description = "Day of the week this schedule applies to. Required, one of: Mon, Tue, Wed, Thu, Fri, Sat, Sun (case-insensitive).", example = "Mon", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Day of week is required")
    @Pattern(regexp = "(?i)^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)$",
            message = "Day must be one of: Mon, Tue, Wed, Thu, Fri, Sat, Sun")
    private String dayOfWeek;

    @Schema(description = "Start time of the doctor's availability window on this day. Required.", example = "09:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @Schema(description = "End time of the doctor's availability window on this day. Required.", example = "17:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "End time is required")
    private LocalTime endTime;

    /** Defaults to true when omitted. */
    @Schema(description = "Optional. Whether the doctor is available during this window. Defaults to true when omitted.", example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean isAvailable;
}
