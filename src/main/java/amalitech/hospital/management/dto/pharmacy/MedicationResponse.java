package amalitech.hospital.management.dto.pharmacy;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MedicationResponse {
    private String medicationId;
    private String name;
    private String genericName;
    private String form;
    private BigDecimal unitPrice;
}
