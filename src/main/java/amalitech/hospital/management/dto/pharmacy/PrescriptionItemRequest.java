package amalitech.hospital.management.dto.pharmacy;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PrescriptionItemRequest {

    @NotBlank(message = "Medication id is required")
    private String medicationId;

    /** Optional — e.g. "500mg twice daily". */
    @Size(max = 50, message = "Dosage must be at most 50 characters")
    private String dosage;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    /** Optional free text. */
    @Size(max = 255, message = "Instructions must be at most 255 characters")
    private String instructions;
}
