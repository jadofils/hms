package amalitech.hospital.management.dto.lab;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Partial-update counterpart to {@link LabOrderRequest} — every field optional; only
 * the ones actually present in the request body get changed. See
 * {@code LabOrderService.patchLabOrder}.
 */
@Data
public class PatchLabOrderRequest {

    @Schema(description = "Optional. ID of the appointment this lab order is associated with.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String appointmentId;

    @Schema(description = "Optional. ID of the doctor ordering the lab test.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String doctorId;

    @Schema(description = "Optional. Name of the lab test being ordered, at most 150 characters.",
            example = "Complete Blood Count", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 150, message = "Test name must be at most 150 characters")
    private String testName;

    @Schema(description = "Optional. Status of the lab order. Must be one of: ordered, in_progress, completed, cancelled.",
            example = "ordered", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "(?i)^(ordered|in_progress|completed|cancelled)$",
            message = "Status must be one of: ordered, in_progress, completed, cancelled")
    private String status;
}
