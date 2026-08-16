package amalitech.hospital.management.dto.patient;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppointmentResponse {
    private String appointmentId;
    private String patientId;
    private String patientName;
    private String doctorId;
    private String doctorName;
    private LocalDateTime appointmentDate;
    private String status;
    private String reason;
}
