package amalitech.hospital.management.dto.finance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Partial-update counterpart to {@link InvoiceRequest} — every field optional; only
 * the ones actually present in the request body get changed. See
 * {@code InvoiceService.patchInvoice}.
 */
@Data
public class PatchInvoiceRequest {

    @Schema(description = "Optional. Id of the appointment this invoice is billed for.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String appointmentId;

    @Schema(description = "Optional. Id of the patient this invoice is billed to.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String patientId;

    @Schema(description = "Optional. Total invoice amount, up to 8 integer digits and 2 decimal places.",
            example = "250.00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @DecimalMin(value = "0.0", message = "Total amount must be zero or greater")
    @Digits(integer = 8, fraction = 2, message = "Total amount can have at most 8 integer digits and 2 decimal places")
    private BigDecimal totalAmount;

    @Schema(description = "Optional. Payment status of the invoice; must be one of: unpaid, partially_paid, paid.",
            example = "unpaid", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "(?i)^(unpaid|partially_paid|paid)$",
            message = "Payment status must be one of: unpaid, partially_paid, paid")
    private String paymentStatus;
}
