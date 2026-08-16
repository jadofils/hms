package amalitech.hospital.management.dto.pharmacy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PrescriptionRequest {

    @NotBlank(message = "Appointment id is required")
    private String appointmentId;

    /** Optional — defaults to today when omitted. */
    @PastOrPresent(message = "Date issued cannot be in the future")
    private LocalDate dateIssued;
}
