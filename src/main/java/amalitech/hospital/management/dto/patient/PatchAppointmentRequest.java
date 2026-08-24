package amalitech.hospital.management.dto.patient;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Partial-update counterpart to {@link AppointmentRequest} — every field optional;
 * only the ones actually present in the request body get changed. See
 * {@code AppointmentService.patchAppointment} — in particular, its own Javadoc for
 * why the double-booking check only re-runs when {@code doctorId}/
 * {@code appointmentDate} are actually part of the patch, not on every call.
 */
@Data
public class PatchAppointmentRequest {

    @Schema(description = "Optional. Id of the patient this appointment is for.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String patientId;

    @Schema(description = "Optional. Id of the doctor this appointment is with.",
            example = "7c9e6679-7425-40de-944b-e07fc1f90ae7", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String doctorId;

    @Schema(description = "Optional. Date and time of the appointment, must be in the future.",
            example = "2026-09-01T10:30:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Future(message = "Appointment date must be in the future")
    private LocalDateTime appointmentDate;

    @Schema(description = "Optional. Free-text reason for the appointment, up to 255 characters.",
            example = "Routine checkup", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 255, message = "Reason must be at most 255 characters")
    private String reason;

    @Schema(description = "Optional. Appointment status, one of: scheduled, completed, cancelled (case-insensitive).",
            example = "scheduled", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "(?i)^(scheduled|completed|cancelled)$",
            message = "Status must be one of: scheduled, completed, cancelled")
    private String status;
}
