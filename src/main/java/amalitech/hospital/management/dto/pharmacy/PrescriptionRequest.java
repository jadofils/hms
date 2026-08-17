package amalitech.hospital.management.dto.pharmacy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PrescriptionRequest {

    @Schema(description = "ID of the appointment this prescription is issued for. Required.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Appointment id is required")
    private String appointmentId;

    /** Optional — defaults to today when omitted. */
    @Schema(description = "Optional. Date the prescription was issued, must be today or in the past. Defaults to today when omitted.",
            example = "2026-08-15", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @PastOrPresent(message = "Date issued cannot be in the future")
    private LocalDate dateIssued;
}
