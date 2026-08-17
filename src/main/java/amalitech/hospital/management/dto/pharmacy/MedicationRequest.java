package amalitech.hospital.management.dto.pharmacy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MedicationRequest {

    @Schema(description = "Name of the medication. Required, at most 150 characters, and may only contain letters, digits, spaces, and ' . ( ) / -",
            example = "Amoxicillin 500mg", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    @Pattern(regexp = "^[A-Za-z0-9' .()/-]+$",
            message = "Name can only contain letters, digits, spaces, and ' . ( ) / -")
    private String name;

    /** Optional. */
    @Schema(description = "Optional. Generic (non-brand) name of the medication, at most 150 characters, and may only contain letters, digits, spaces, and ' . ( ) / -",
            example = "Amoxicillin", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 150, message = "Generic name must be at most 150 characters")
    @Pattern(regexp = "^[A-Za-z0-9' .()/-]*$",
            message = "Generic name can only contain letters, digits, spaces, and ' . ( ) / -")
    private String genericName;

    /** Optional — e.g. "tablet", "capsule", "syrup". */
    @Schema(description = "Optional. Dosage form of the medication, at most 50 characters, e.g. \"tablet\", \"capsule\", \"syrup\".",
            example = "tablet", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 50, message = "Form must be at most 50 characters")
    private String form;

    /** Optional — matches the entity column's {@code precision = 10, scale = 2}. */
    @Schema(description = "Optional. Unit price of the medication, zero or greater, with at most 8 integer digits and 2 decimal places.",
            example = "12.50", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @DecimalMin(value = "0.0", message = "Unit price must be zero or greater")
    @Digits(integer = 8, fraction = 2, message = "Unit price can have at most 8 integer digits and 2 decimal places")
    private BigDecimal unitPrice;
}
