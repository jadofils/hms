package amalitech.hospital.management.dto.pharmacy;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PrescriptionResponse {
    private String prescriptionId;
    private String appointmentId;
    private String patientId;
    private String patientName;
    private String doctorId;
    private String doctorName;
    private LocalDate dateIssued;
}
