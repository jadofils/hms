package amalitech.hospital.management.dto.pharmacy;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MedicalInventoryRequest {

    @NotBlank(message = "Medication id is required")
    private String medicationId;

    /** Optional. */
    @Size(max = 50, message = "Batch number must be at most 50 characters")
    private String batchNumber;

    @NotNull(message = "Expiry date is required")
    @FutureOrPresent(message = "Expiry date cannot be in the past")
    private LocalDate expiryDate;

    /** Optional on create — defaults to 0. */
    @Min(value = 0, message = "Quantity in stock cannot be negative")
    private Integer quantityInStock;

    /** Optional on create — defaults to 10. */
    @Min(value = 0, message = "Reorder level cannot be negative")
    private Integer reorderLevel;

    /** Optional. */
    @Size(max = 100, message = "Supplier must be at most 100 characters")
    private String supplier;
}
