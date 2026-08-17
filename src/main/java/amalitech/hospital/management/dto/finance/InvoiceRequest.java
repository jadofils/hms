package amalitech.hospital.management.dto.finance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InvoiceRequest {

    @Schema(description = "Id of the appointment this invoice is billed for.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Appointment id is required")
    private String appointmentId;

    @Schema(description = "Id of the patient this invoice is billed to.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Patient id is required")
    private String patientId;

    /** Optional on create — defaults to 0; matches the entity column's
     *  {@code precision = 10, scale = 2}. */
    @Schema(description = "Optional. Total invoice amount, up to 8 integer digits and 2 decimal places. Defaults to 0 when omitted.",
            example = "250.00",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @DecimalMin(value = "0.0", message = "Total amount must be zero or greater")
    @Digits(integer = 8, fraction = 2, message = "Total amount can have at most 8 integer digits and 2 decimal places")
    private BigDecimal totalAmount;

    /** Optional on create — the entity defaults to "unpaid". */
    @Schema(description = "Optional. Payment status of the invoice; must be one of: unpaid, partially_paid, paid. Defaults to \"unpaid\" when omitted.",
            example = "unpaid",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "(?i)^(unpaid|partially_paid|paid)$",
            message = "Payment status must be one of: unpaid, partially_paid, paid")
    private String paymentStatus;
}
