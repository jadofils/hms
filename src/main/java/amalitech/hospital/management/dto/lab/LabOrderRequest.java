package amalitech.hospital.management.dto.lab;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LabOrderRequest {

    @NotBlank(message = "Appointment id is required")
    private String appointmentId;

    @NotBlank(message = "Doctor id is required")
    private String doctorId;

    @NotBlank(message = "Test name is required")
    @Size(max = 150, message = "Test name must be at most 150 characters")
    private String testName;

    /** Optional on create — the entity defaults to "ordered". */
    @Pattern(regexp = "(?i)^(ordered|in_progress|completed|cancelled)$",
            message = "Status must be one of: ordered, in_progress, completed, cancelled")
    private String status;
}
