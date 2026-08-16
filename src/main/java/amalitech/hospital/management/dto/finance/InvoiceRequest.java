package amalitech.hospital.management.dto.finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InvoiceRequest {

    @NotBlank(message = "Appointment id is required")
    private String appointmentId;

    @NotBlank(message = "Patient id is required")
    private String patientId;

    /** Optional on create — defaults to 0; matches the entity column's
     *  {@code precision = 10, scale = 2}. */
    @DecimalMin(value = "0.0", message = "Total amount must be zero or greater")
    @Digits(integer = 8, fraction = 2, message = "Total amount can have at most 8 integer digits and 2 decimal places")
    private BigDecimal totalAmount;

    /** Optional on create — the entity defaults to "unpaid". */
    @Pattern(regexp = "(?i)^(unpaid|partially_paid|paid)$",
            message = "Payment status must be one of: unpaid, partially_paid, paid")
    private String paymentStatus;
}
