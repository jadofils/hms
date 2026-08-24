package amalitech.hospital.management.dto.pharmacy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Partial-update counterpart to {@link MedicationRequest} — every field is optional;
 * {@code MedicationService.patchMedication} only touches the ones actually present in
 * the request body.
 */
@Data
public class PatchMedicationRequest {

    @Schema(description = "Optional. New name, at most 150 characters, letters/digits/spaces/' . ( ) / - only. "
            + "Omit to leave unchanged.",
            example = "Amoxicillin 500mg", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 150, message = "Name must be at most 150 characters")
    @Pattern(regexp = "^[A-Za-z0-9' .()/-]+$",
            message = "Name can only contain letters, digits, spaces, and ' . ( ) / -")
    private String name;

    @Schema(description = "Optional. New generic (non-brand) name, at most 150 characters. Omit to leave unchanged.",
            example = "Amoxicillin", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 150, message = "Generic name must be at most 150 characters")
    @Pattern(regexp = "^[A-Za-z0-9' .()/-]*$",
            message = "Generic name can only contain letters, digits, spaces, and ' . ( ) / -")
    private String genericName;

    @Schema(description = "Optional. New dosage form, at most 50 characters, e.g. \"tablet\", \"capsule\", \"syrup\". "
            + "Omit to leave unchanged.",
            example = "tablet", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 50, message = "Form must be at most 50 characters")
    private String form;

    @Schema(description = "Optional. New unit price, zero or greater, at most 8 integer digits and 2 decimal "
            + "places. Omit to leave unchanged.",
            example = "12.50", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @DecimalMin(value = "0.0", message = "Unit price must be zero or greater")
    @Digits(integer = 8, fraction = 2, message = "Unit price can have at most 8 integer digits and 2 decimal places")
    private BigDecimal unitPrice;
}
