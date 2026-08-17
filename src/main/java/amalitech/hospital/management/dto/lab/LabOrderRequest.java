package amalitech.hospital.management.dto.lab;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LabOrderRequest {

    @Schema(description = "ID of the appointment this lab order is associated with. Required.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Appointment id is required")
    private String appointmentId;

    @Schema(description = "ID of the doctor ordering the lab test. Required.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Doctor id is required")
    private String doctorId;

    @Schema(description = "Name of the lab test being ordered. Required, at most 150 characters.",
            example = "Complete Blood Count", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Test name is required")
    @Size(max = 150, message = "Test name must be at most 150 characters")
    private String testName;

    /** Optional on create — the entity defaults to "ordered". */
    @Schema(description = "Optional. Status of the lab order. Must be one of: ordered, in_progress, completed, cancelled. Defaults to \"ordered\" when omitted.",
            example = "ordered", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "(?i)^(ordered|in_progress|completed|cancelled)$",
            message = "Status must be one of: ordered, in_progress, completed, cancelled")
    private String status;
}
