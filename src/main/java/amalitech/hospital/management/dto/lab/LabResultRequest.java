package amalitech.hospital.management.dto.lab;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LabResultRequest {

    /** Optional free text — no fixed shape across test types. */
    @Schema(description = "Optional. Result value of the lab test, at most 100 characters. Shape varies by test type.",
            example = "5.4 x10^9/L", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 100, message = "Result value must be at most 100 characters")
    private String resultValue;

    @Schema(description = "Optional. Unit of measurement for the result value, at most 20 characters.",
            example = "mg/dL", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 20, message = "Unit must be at most 20 characters")
    private String unit;

    @Schema(description = "Optional. Normal/expected reference range for the result, at most 50 characters.",
            example = "4.0-11.0", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 50, message = "Reference range must be at most 50 characters")
    private String referenceRange;

    /** Optional — defaults to false when omitted. */
    @Schema(description = "Optional. Whether the result is outside the normal range. Defaults to false when omitted.",
            example = "false", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean isAbnormal;

    @Schema(description = "Optional. Timestamp the result was completed, must be now or in the past.",
            example = "2026-08-15T14:00:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @PastOrPresent(message = "Completed at cannot be in the future")
    private LocalDateTime completedAt;
}
