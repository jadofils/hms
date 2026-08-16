package amalitech.hospital.management.dto.lab;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LabResultRequest {

    /** Optional free text — no fixed shape across test types. */
    @Size(max = 100, message = "Result value must be at most 100 characters")
    private String resultValue;

    @Size(max = 20, message = "Unit must be at most 20 characters")
    private String unit;

    @Size(max = 50, message = "Reference range must be at most 50 characters")
    private String referenceRange;

    /** Optional — defaults to false when omitted. */
    private Boolean isAbnormal;

    @PastOrPresent(message = "Completed at cannot be in the future")
    private LocalDateTime completedAt;
}
