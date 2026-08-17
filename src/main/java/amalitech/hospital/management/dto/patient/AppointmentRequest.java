package amalitech.hospital.management.dto.patient;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppointmentRequest {

    @Schema(description = "Id of the patient this appointment is for. Required.", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Patient id is required")
    private String patientId;

    @Schema(description = "Id of the doctor this appointment is with. Required.", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Doctor id is required")
    private String doctorId;

    @Schema(description = "Date and time of the appointment. Required, and must be in the future.", example = "2026-09-01T10:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Appointment date is required")
    @Future(message = "Appointment date must be in the future")
    private LocalDateTime appointmentDate;

    /** Free text — no shape to validate beyond length. */
    @Schema(description = "Optional. Free-text reason for the appointment, up to 255 characters.", example = "Routine checkup", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 255, message = "Reason must be at most 255 characters")
    private String reason;

    /** Optional on create — the entity defaults to "scheduled". */
    @Schema(description = "Optional. Appointment status, one of: scheduled, completed, cancelled (case-insensitive). Defaults to \"scheduled\" when omitted.", example = "scheduled", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "(?i)^(scheduled|completed|cancelled)$",
            message = "Status must be one of: scheduled, completed, cancelled")
    private String status;
}
