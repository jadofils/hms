package amalitech.hospital.management.dto.lab;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LabOrderResponse {
    private String labOrderId;
    private String appointmentId;
    private String patientId;
    private String patientName;
    private String doctorId;
    private String doctorName;
    private String testName;
    private String status;
    private LocalDateTime orderedAt;
}
