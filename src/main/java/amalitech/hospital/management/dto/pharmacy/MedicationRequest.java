package amalitech.hospital.management.dto.pharmacy;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MedicationRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    @Pattern(regexp = "^[A-Za-z0-9' .()/-]+$",
            message = "Name can only contain letters, digits, spaces, and ' . ( ) / -")
    private String name;

    /** Optional. */
    @Size(max = 150, message = "Generic name must be at most 150 characters")
    @Pattern(regexp = "^[A-Za-z0-9' .()/-]*$",
            message = "Generic name can only contain letters, digits, spaces, and ' . ( ) / -")
    private String genericName;

    /** Optional — e.g. "tablet", "capsule", "syrup". */
    @Size(max = 50, message = "Form must be at most 50 characters")
    private String form;

    /** Optional — matches the entity column's {@code precision = 10, scale = 2}. */
    @DecimalMin(value = "0.0", message = "Unit price must be zero or greater")
    @Digits(integer = 8, fraction = 2, message = "Unit price can have at most 8 integer digits and 2 decimal places")
    private BigDecimal unitPrice;
}
