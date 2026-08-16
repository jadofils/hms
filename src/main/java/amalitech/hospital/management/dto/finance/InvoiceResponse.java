package amalitech.hospital.management.dto.finance;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InvoiceResponse {
    private String invoiceId;
    private String appointmentId;
    private String patientId;
    private String patientName;
    private BigDecimal totalAmount;
    private String paymentStatus;
    private LocalDateTime issuedAt;
}
