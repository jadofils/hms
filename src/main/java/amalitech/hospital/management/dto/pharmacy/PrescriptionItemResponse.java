package amalitech.hospital.management.dto.pharmacy;

import lombok.Data;

@Data
public class PrescriptionItemResponse {
    private String itemId;
    private String prescriptionId;
    private String medicationId;
    private String medicationName;
    private String dosage;
    private Integer quantity;
    private String instructions;
}
