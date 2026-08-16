package amalitech.hospital.management.dto.pharmacy;

import lombok.Data;

import java.time.LocalDate;

@Data
public class MedicalInventoryResponse {
    private String inventoryId;
    private String medicationId;
    private String medicationName;
    private String batchNumber;
    private LocalDate expiryDate;
    private Integer quantityInStock;
    private Integer reorderLevel;
    private String supplier;
}
