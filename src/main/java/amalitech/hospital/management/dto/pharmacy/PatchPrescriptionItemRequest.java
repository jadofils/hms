package amalitech.hospital.management.dto.pharmacy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Partial-update counterpart to {@link PrescriptionItemRequest} — every field optional;
 * only the ones actually present in the request body get changed. See
 * {@code PrescriptionItemService.patchItem}.
 */
@Data
public class PatchPrescriptionItemRequest {

    @Schema(description = "Optional. ID of the medication being prescribed.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String medicationId;

    @Schema(description = "Optional. Dosage instructions, at most 50 characters, e.g. \"500mg twice daily\".",
            example = "500mg twice daily", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 50, message = "Dosage must be at most 50 characters")
    private String dosage;

    @Schema(description = "Optional. Quantity of the medication to dispense, at least 1.",
            example = "20", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @Schema(description = "Optional. Free-text instructions for the patient, at most 255 characters.",
            example = "Take with food, once daily for 7 days.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 255, message = "Instructions must be at most 255 characters")
    private String instructions;
}
