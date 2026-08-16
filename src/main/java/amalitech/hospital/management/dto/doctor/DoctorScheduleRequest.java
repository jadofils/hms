package amalitech.hospital.management.dto.doctor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalTime;

@Data
public class DoctorScheduleRequest {

    @NotBlank(message = "Day of week is required")
    @Pattern(regexp = "(?i)^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)$",
            message = "Day must be one of: Mon, Tue, Wed, Thu, Fri, Sat, Sun")
    private String dayOfWeek;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;

    /** Defaults to true when omitted. */
    private Boolean isAvailable;
}
