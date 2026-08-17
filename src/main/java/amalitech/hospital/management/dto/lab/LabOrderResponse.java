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
    /** {@code null} until a result has been recorded ({@code LabResultService.createResult})
     *  — not populated by the paginated listing or by create/update, only by the
     *  single-item lookup ({@code LabOrderService.getLabOrder}), same convention as
     *  {@code DoctorResponse.departments}. */
    private LabResultResponse result;
}
