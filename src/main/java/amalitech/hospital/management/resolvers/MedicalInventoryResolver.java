package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.pharmacy.MedicalInventoryRequest;
import amalitech.hospital.management.dto.pharmacy.MedicalInventoryResponse;
import amalitech.hospital.management.dto.pharmacy.MedicationResponse;
import amalitech.hospital.management.service.MedicalInventoryService;
import amalitech.hospital.management.service.MedicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * GraphQL front door for {@link MedicalInventoryService} — see {@code UserResolver}'s
 * Javadoc for the shared reasoning (same service layer as REST, same request DTOs).
 */
@Controller
@Validated
@RequiredArgsConstructor
public class MedicalInventoryResolver {

    private final MedicalInventoryService medicalInventoryService;
    private final MedicationService medicationService;

    @QueryMapping
    public List<MedicalInventoryResponse> inventoryRecords(@Argument int page, @Argument int size) {
        return medicalInventoryService.getInventoryRecords(PageRequest.of(page, size)).getContent();
    }

    @QueryMapping
    public MedicalInventoryResponse inventoryRecord(@Argument String inventoryId) {
        return medicalInventoryService.getInventoryRecord(inventoryId);
    }

    @MutationMapping
    public MedicalInventoryResponse createInventoryRecord(@Argument @Valid MedicalInventoryRequest input) {
        return medicalInventoryService.createInventoryRecord(input);
    }

    @MutationMapping
    public MedicalInventoryResponse updateInventoryRecord(@Argument String inventoryId, @Argument @Valid MedicalInventoryRequest input) {
        return medicalInventoryService.updateInventoryRecord(inventoryId, input);
    }

    @MutationMapping
    public boolean deleteInventoryRecord(@Argument String inventoryId) {
        medicalInventoryService.deleteInventoryRecord(inventoryId);
        return true;
    }

    @SchemaMapping(typeName = "MedicalInventory", field = "medication")
    public MedicationResponse medication(MedicalInventoryResponse inventory) {
        return medicationService.getMedication(inventory.getMedicationId());
    }
}
