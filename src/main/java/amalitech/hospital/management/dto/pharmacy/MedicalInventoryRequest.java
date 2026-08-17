package amalitech.hospital.management.dto.pharmacy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MedicalInventoryRequest {

    @Schema(description = "ID of the medication this inventory batch belongs to. Required.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Medication id is required")
    private String medicationId;

    /** Optional. */
    @Schema(description = "Optional. Supplier or manufacturer batch/lot number, at most 50 characters.",
            example = "BATCH-2026-0142", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 50, message = "Batch number must be at most 50 characters")
    private String batchNumber;

    @Schema(description = "Expiry date of the batch. Required and must be today or a future date.",
            example = "2027-06-01", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Expiry date is required")
    @FutureOrPresent(message = "Expiry date cannot be in the past")
    private LocalDate expiryDate;

    /** Optional on create — defaults to 0. */
    @Schema(description = "Optional. Quantity currently in stock, zero or greater. Defaults to 0 when omitted.",
            example = "100", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Min(value = 0, message = "Quantity in stock cannot be negative")
    private Integer quantityInStock;

    /** Optional on create — defaults to 10. */
    @Schema(description = "Optional. Stock level at or below which the item should be reordered, zero or greater. Defaults to 10 when omitted.",
            example = "10", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Min(value = 0, message = "Reorder level cannot be negative")
    private Integer reorderLevel;

    /** Optional. */
    @Schema(description = "Optional. Name of the supplier, at most 100 characters.",
            example = "MedSupply Ltd.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 100, message = "Supplier must be at most 100 characters")
    private String supplier;
}
