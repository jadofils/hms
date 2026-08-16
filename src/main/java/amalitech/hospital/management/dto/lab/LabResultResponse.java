package amalitech.hospital.management.dto.lab;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LabResultResponse {
    private String labResultId;
    private String labOrderId;
    private String resultValue;
    private String unit;
    private String referenceRange;
    private Boolean isAbnormal;
    private LocalDateTime completedAt;
}
