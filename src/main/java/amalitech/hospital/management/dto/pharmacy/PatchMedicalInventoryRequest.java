package amalitech.hospital.management.dto.pharmacy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Partial-update counterpart to {@link MedicalInventoryRequest} — every field
 * optional; only the ones actually present in the request body get changed. See
 * {@code MedicalInventoryService.patchInventoryRecord}.
 */
@Data
public class PatchMedicalInventoryRequest {

    @Schema(description = "Optional. ID of the medication this inventory batch belongs to.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String medicationId;

    @Schema(description = "Optional. Supplier or manufacturer batch/lot number, at most 50 characters.",
            example = "BATCH-2026-0142", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 50, message = "Batch number must be at most 50 characters")
    private String batchNumber;

    @Schema(description = "Optional. Expiry date of the batch, must be today or a future date.",
            example = "2027-06-01", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @FutureOrPresent(message = "Expiry date cannot be in the past")
    private LocalDate expiryDate;

    @Schema(description = "Optional. Quantity currently in stock, zero or greater.",
            example = "100", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Min(value = 0, message = "Quantity in stock cannot be negative")
    private Integer quantityInStock;

    @Schema(description = "Optional. Stock level at or below which the item should be reordered, zero or greater.",
            example = "10", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Min(value = 0, message = "Reorder level cannot be negative")
    private Integer reorderLevel;

    @Schema(description = "Optional. Name of the supplier, at most 100 characters.",
            example = "MedSupply Ltd.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 100, message = "Supplier must be at most 100 characters")
    private String supplier;
}
