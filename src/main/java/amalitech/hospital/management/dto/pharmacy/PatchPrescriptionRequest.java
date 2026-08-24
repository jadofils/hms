package amalitech.hospital.management.dto.pharmacy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;

/**
 * Partial-update counterpart to {@link PrescriptionRequest} — every field optional;
 * only the ones actually present in the request body get changed. See
 * {@code PrescriptionService.patchPrescription}.
 */
@Data
public class PatchPrescriptionRequest {

    @Schema(description = "Optional. ID of the appointment this prescription is issued for.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String appointmentId;

    @Schema(description = "Optional. Date the prescription was issued, must be today or in the past.",
            example = "2026-08-15", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @PastOrPresent(message = "Date issued cannot be in the future")
    private LocalDate dateIssued;
}
