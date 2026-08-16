package amalitech.hospital.management.dto.patient;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppointmentRequest {

    @NotBlank(message = "Patient id is required")
    private String patientId;

    @NotBlank(message = "Doctor id is required")
    private String doctorId;

    @NotNull(message = "Appointment date is required")
    @Future(message = "Appointment date must be in the future")
    private LocalDateTime appointmentDate;

    /** Free text — no shape to validate beyond length. */
    @Size(max = 255, message = "Reason must be at most 255 characters")
    private String reason;

    /** Optional on create — the entity defaults to "scheduled". */
    @Pattern(regexp = "(?i)^(scheduled|completed|cancelled)$",
            message = "Status must be one of: scheduled, completed, cancelled")
    private String status;
}
